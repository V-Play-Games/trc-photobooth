import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  CameraConfig,
  CaptureMetadata,
  CaptureType,
  CountdownState,
  GifRecordingState,
  SystemStats,
  WebSocketConnectionStatus,
} from '../types'

export interface WebSocketHookOptions {
  onCaptureResult?: (capture: CaptureMetadata) => void
  onFlashTrigger?: () => void
  onCountdownTick?: (secondsLeft: number, action: CaptureType) => void
}

export function useWebSocket({
  onCaptureResult,
  onFlashTrigger,
  onCountdownTick,
}: WebSocketHookOptions = {}) {
  const [status, setStatus] = useState<WebSocketConnectionStatus>('connecting')
  const [fps, setFps] = useState<number>(0)
  const [latency, setLatency] = useState<number>(0)
  const [lastFrameBitmap, setLastFrameBitmap] = useState<ImageBitmap | null>(null)
  const [countdown, setCountdown] = useState<CountdownState | null>(null)
  const [gifRecording, setGifRecording] = useState<GifRecordingState | null>(null)
  const [cameraConfig, setCameraConfig] = useState<Partial<CameraConfig>>({})
  const [systemStats, setSystemStats] = useState<SystemStats | null>(null)

  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttemptsRef = useRef<number>(0)
  const isUnmountedRef = useRef<boolean>(false)
  const connectRef = useRef<() => void>(() => {})

  // Rolling FPS tracker
  const frameTimestampsRef = useRef<number[]>([])
  const lastFpsCalcRef = useRef<number>(0)

  // Latency measurement
  const pingTimestampRef = useRef<number>(0)

  // Construct WebSocket target URL
  const getWebSocketUrl = useCallback(() => {
    const loc = window.location
    const protocol = loc.protocol === 'https:' ? 'wss:' : 'ws:'

    // If developing with Vite on port 5173, route WebSocket to port 8000
    if (loc.port === '5173') {
      return `${protocol}//${loc.hostname}:8000/ws/feed`
    }
    return `${protocol}//${loc.host}/ws/feed`
  }, [])

  const connect = useCallback(() => {
    if (isUnmountedRef.current) return
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return
    }

    setStatus('connecting')
    const url = getWebSocketUrl()

    try {
      const ws = new WebSocket(url)
      ws.binaryType = 'arraybuffer'
      wsRef.current = ws

      ws.onopen = () => {
        if (isUnmountedRef.current) {
          ws.close()
          return
        }
        setStatus('connected')
        reconnectAttemptsRef.current = 0

        // Send initial ping and query system stats
        try {
          pingTimestampRef.current = performance.now()
          ws.send('ping')
          ws.send(JSON.stringify({ action: 'get_system_stats' }))
        } catch {
          // Ignore
        }
      }

      ws.onmessage = async (event: MessageEvent) => {
        if (isUnmountedRef.current) return

        // 1. Handle binary camera JPEG frame
        if (event.data instanceof ArrayBuffer) {
          const now = performance.now()
          const times = frameTimestampsRef.current
          times.push(now)

          // Keep timestamps from the last 1 second
          while (times.length > 0 && times[0] < now - 1000) {
            times.shift()
          }

          if (now - lastFpsCalcRef.current >= 400) {
            setFps(times.length)
            lastFpsCalcRef.current = now
          }

          try {
            const blob = new Blob([event.data], { type: 'image/jpeg' })
            const bitmap = await createImageBitmap(blob)
            setLastFrameBitmap((prev) => {
              if (prev) prev.close()
              return bitmap
            })
          } catch {
            // Discard malformed frame silently
          }
          return
        }

        // 2. Handle JSON control & telemetry messages
        if (typeof event.data === 'string') {
          try {
            const msg = JSON.parse(event.data)
            const type = msg.type || msg.action

            if (type === 'pong') {
              if (pingTimestampRef.current > 0) {
                const rtt = Math.round(performance.now() - pingTimestampRef.current)
                setLatency(rtt)
              }
            } else if (type === 'countdown_tick') {
              const secondsLeft = Number(msg.seconds_left)
              const action = (msg.action as CaptureType) || 'photo'
              onCountdownTick?.(secondsLeft, action)

              if (secondsLeft > 0) {
                setCountdown({ secondsLeft, action })
              } else {
                // Ticked to 0! Flash screen
                setCountdown({ secondsLeft: 0, action })
                onFlashTrigger?.()
                setTimeout(() => setCountdown(null), 800)
              }
            } else if (type === 'capture_result') {
              setCountdown(null)
              onFlashTrigger?.()
              if (msg.data) {
                onCaptureResult?.(msg.data)
              }
            } else if (type === 'gif_recording') {
              setGifRecording({
                frames: msg.frames || 10,
                intervalMs: msg.interval_ms || 150,
              })
            } else if (type === 'gif_result') {
              setGifRecording(null)
              setCountdown(null)
              onFlashTrigger?.()
              if (msg.data) {
                onCaptureResult?.(msg.data)
              }
            } else if (type === 'config') {
              setCameraConfig((prev) => ({ ...prev, ...msg }))
            } else if (type === 'system_stats') {
              if (msg.data) {
                setSystemStats(msg.data)
              }
            }
          } catch {
            // Ignore non-json text
          }
        }
      }

      ws.onerror = () => {
        if (isUnmountedRef.current) return
        setStatus('error')
      }

      ws.onclose = () => {
        if (isUnmountedRef.current) return
        setStatus('disconnected')
        wsRef.current = null

        // Exponential backoff reconnect
        const attempt = reconnectAttemptsRef.current
        const delay = Math.min(1000 * Math.pow(1.5, attempt), 8000)
        reconnectAttemptsRef.current += 1

        reconnectTimeoutRef.current = setTimeout(() => {
          connectRef.current()
        }, delay)
      }
    } catch {
      setStatus('error')
    }
  }, [getWebSocketUrl, onCaptureResult, onFlashTrigger])

  useEffect(() => {
    connectRef.current = connect
  }, [connect])

  // Periodic heartbeat & latency checker
  useEffect(() => {
    const interval = setInterval(() => {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        pingTimestampRef.current = performance.now()
        wsRef.current.send('ping')
      }
    }, 4000)
    return () => clearInterval(interval)
  }, [])

  // Mount/unmount connection lifecycle
  useEffect(() => {
    isUnmountedRef.current = false
    connect()

    return () => {
      isUnmountedRef.current = true
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
      if (wsRef.current) {
        wsRef.current.close()
        wsRef.current = null
      }
      setLastFrameBitmap((prev) => {
        if (prev) prev.close()
        return null
      })
    }
  }, [connect])

  // Command dispatchers
  const sendCommand = useCallback((payload: Record<string, unknown>) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(payload))
      return true
    }
    return false
  }, [])

  const triggerPhoto = useCallback(
    (countdownSeconds = 0) => {
      return sendCommand({
        action: 'trigger_capture',
        countdown: countdownSeconds,
      })
    },
    [sendCommand],
  )

  const triggerGif = useCallback(
    (countdownSeconds = 0, frames = 10, intervalMs = 150) => {
      return sendCommand({
        action: 'trigger_gif',
        countdown: countdownSeconds,
        frames,
        interval_ms: intervalMs,
      })
    },
    [sendCommand],
  )

  const setFpsSetting = useCallback(
    (value: number) => {
      return sendCommand({ action: 'set_fps', value })
    },
    [sendCommand],
  )

  const setQualitySetting = useCallback(
    (value: number) => {
      return sendCommand({ action: 'set_quality', value })
    },
    [sendCommand],
  )

  const setResolutionSetting = useCallback(
    (value: string, aspectRatio = '16:9') => {
      return sendCommand({
        action: 'set_resolution',
        value,
        aspect_ratio: aspectRatio,
      })
    },
    [sendCommand],
  )

  const toggleFlip = useCallback(
    (value?: boolean) => {
      return sendCommand({ action: 'flip_horizontal', value })
    },
    [sendCommand],
  )

  const toggleSwapRb = useCallback(
    (value?: boolean) => {
      return sendCommand({ action: 'swap_rb', value })
    },
    [sendCommand],
  )

  const requestSystemStats = useCallback(() => {
    return sendCommand({ action: 'get_system_stats' })
  }, [sendCommand])

  return {
    status,
    fps,
    latency,
    lastFrameBitmap,
    countdown,
    gifRecording,
    cameraConfig,
    systemStats,
    triggerPhoto,
    triggerGif,
    setFpsSetting,
    setQualitySetting,
    setResolutionSetting,
    toggleFlip,
    toggleSwapRb,
    requestSystemStats,
    reconnect: connect,
  }
}
