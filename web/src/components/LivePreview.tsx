import React, { useEffect, useRef } from 'react'
import { Camera, Film } from 'lucide-react'
import { FILTER_PRESETS } from '../filters/presets'
import type { FilterId, GifRecordingState } from '../types'

interface LivePreviewProps {
  lastFrameBitmap: ImageBitmap | null
  activeFilter: FilterId
  isFlipped: boolean
  showGuides: boolean
  fps: number
  resolution: string
  isConnected: boolean
  gifRecording: GifRecordingState | null
}

export const LivePreview: React.FC<LivePreviewProps> = ({
  lastFrameBitmap,
  activeFilter,
  isFlipped,
  showGuides,
  fps,
  resolution,
  isConnected,
  gifRecording,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const preset = FILTER_PRESETS[activeFilter] || FILTER_PRESETS.none

  // Render incoming frame onto canvas
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !lastFrameBitmap) return

    const ctx = canvas.getContext('2d', { alpha: false })
    if (!ctx) return

    if (canvas.width !== lastFrameBitmap.width || canvas.height !== lastFrameBitmap.height) {
      canvas.width = lastFrameBitmap.width
      canvas.height = lastFrameBitmap.height
    }

    ctx.drawImage(lastFrameBitmap, 0, 0)
  }, [lastFrameBitmap])

  return (
    <div className="preview-container" ref={containerRef}>
      {/* Canvas Viewport */}
      <div className={`preview-viewport ${isFlipped ? 'mirror-flipped' : ''}`}>
        <canvas
          ref={canvasRef}
          className="preview-canvas"
          style={{
            filter: preset.cssFilter,
          }}
        />

        {/* Procedural Filter Overlays */}
        {activeFilter === 'vignette' && <div className="overlay-vignette" />}
        {activeFilter === 'film_grain' && <div className="overlay-grain" />}
        {activeFilter === 'polaroid' && <div className="overlay-polaroid-tint" />}

        {/* Rule of Thirds Framing Guides */}
        {showGuides && (
          <div className="framing-guides" aria-hidden="true">
            <div className="guide-line guide-h-1" />
            <div className="guide-line guide-h-2" />
            <div className="guide-line guide-v-1" />
            <div className="guide-line guide-v-2" />
            <div className="guide-center-crosshair" />
          </div>
        )}

        {/* Offline / No Stream Placeholder */}
        {(!isConnected || !lastFrameBitmap) && (
          <div className="preview-offline-backdrop">
            <div className="offline-card">
              <div className="lens-aperture-spinner">
                <Camera className="offline-camera-icon" size={48} />
              </div>
              <h3 className="offline-title">
                {isConnected ? 'Acquiring Camera Feed...' : 'Connecting to Raspberry Pi'}
              </h3>
              <p className="offline-hint">
                {isConnected
                  ? 'Initializing camera sensor and streamer...'
                  : 'Ensure the Pi is on the same WiFi network (ws://<pi-ip>:8000/ws/feed)'}
              </p>
            </div>
          </div>
        )}

        {/* GIF Recording Banner */}
        {gifRecording && (
          <div className="gif-recording-banner">
            <div className="banner-content">
              <Film className="banner-icon animate-pulse" size={20} />
              <div className="banner-text">
                <span className="banner-title">RECORDING GIF BURST</span>
                <span className="banner-subtitle">
                  Capturing {gifRecording.frames} frames @ {gifRecording.intervalMs}ms
                </span>
              </div>
              <div className="banner-spinner" />
            </div>
          </div>
        )}

        {/* Live Stream Telemetry Pill Overlay */}
        {isConnected && lastFrameBitmap && (
          <div className="preview-hud-badges">
            <span className="hud-badge hud-res">{resolution || `${lastFrameBitmap.width}×${lastFrameBitmap.height}`}</span>
            <span className="hud-badge hud-fps">{fps} FPS</span>
            {activeFilter !== 'none' && (
              <span className="hud-badge hud-filter" style={{ borderColor: preset.accentColor, color: preset.accentColor }}>
                {preset.badgeText}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
