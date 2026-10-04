/**
 * Core type definitions for TRC Photo Booth React Web Client.
 */

export type FilterId =
  | 'none'
  | 'bw'
  | 'sepia'
  | 'vintage'
  | 'cool'
  | 'warm'
  | 'high_contrast'
  | 'vignette'
  | 'film_grain'
  | 'polaroid'
  | 'random'

export interface FilterPreset {
  id: FilterId
  name: string
  tagline: string
  cssFilter: string
  canvasFilter?: string
  accentColor: string
  description: string
  badgeText: string
}

export type AppMode = 'admin' | 'booth'

export type BoothPhase = 'idle' | 'capturing' | 'complete'

export type CaptureType = 'photo' | 'gif'

export interface CaptureMetadata {
  id: string
  type: CaptureType
  content_type: string
  filename: string
  width: number
  height: number
  size_bytes: number
  created_at: number
  url: string
  frames?: number | null
  filter_applied?: FilterId | string
}

export interface SystemStatus {
  status: string
  camera_ready: boolean
  camera_backend: string
  is_mock: boolean
  actual_fps: number
  target_fps: number
  connected_clients: number
  resolution: string
  frames_sent_total: number
  uptime_seconds: number
  swap_rb: boolean
  flip_horizontal: boolean
  is_stream_paused?: boolean
  webcam_device?: number
  device_path?: string
  available_devices?: VideoDevice[]
}

export interface VideoDevice {
  device: string
  index: number
  name: string
  available: boolean
}

export interface MemoryStats {
  total_mb: number
  used_mb: number
  free_mb: number
  percent: number
}

export interface SystemStats {
  cpu_percent: number
  cpu_temp_c?: number | null
  temperature_c?: number | null
  memory?: MemoryStats
  load_avg?: number[]
  cpu_count?: number
  throttled?: string | null
  timestamp?: number
  memory_used_mb?: number
  memory_total_mb?: number
  memory_percent?: number
  uptime_formatted?: string
}

export interface CameraConfig {
  width: number
  height: number
  fps: number
  quality: number
  swap_rb: boolean
  flip_horizontal: boolean
  capture_width: number
  capture_height: number
  capture_quality: number
  webcam_device?: number
  device_path?: string
  available_devices?: VideoDevice[]
}

export type WebSocketConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'error'

export interface CountdownState {
  secondsLeft: number
  action: CaptureType
}

export interface GifRecordingState {
  frames: number
  intervalMs: number
}
