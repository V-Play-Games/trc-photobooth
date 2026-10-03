import React from 'react'
import {
  Camera,
  Grid,
  FlipHorizontal,
  Images,
  Maximize,
  Minimize,
  RefreshCw,
  Settings as SettingsIcon,
  Volume2,
  VolumeX,
  Wifi,
} from 'lucide-react'
import type { WebSocketConnectionStatus } from '../types'

interface HeaderProps {
  connectionStatus: WebSocketConnectionStatus
  fps: number
  latency: number
  capturesCount: number
  showGuides: boolean
  isFlipped: boolean
  isSoundActive: boolean
  isFullscreen: boolean
  onToggleGuides: () => void
  onToggleFlip: () => void
  onToggleSound: () => void
  onToggleFullscreen: () => void
  onManualReconnect?: () => void
  onOpenGallery: () => void
  onOpenSettings: () => void
  onLaunchBooth?: () => void
}

export const Header: React.FC<HeaderProps> = ({
  connectionStatus,
  fps,
  latency,
  capturesCount,
  showGuides,
  isFlipped,
  isSoundActive,
  isFullscreen,
  onToggleGuides,
  onToggleFlip,
  onToggleSound,
  onToggleFullscreen,
  onManualReconnect,
  onOpenGallery,
  onOpenSettings,
  onLaunchBooth,
}) => {
  const isConnected = connectionStatus === 'connected'
  const isConnecting = connectionStatus === 'connecting'

  return (
    <header className="app-header">
      <div className="header-left">
        <div className="brand-lockup">
          <div className="brand-icon-wrapper">
            <Camera className="brand-camera-icon" size={20} />
            <div className="brand-dot-pulse" />
          </div>
          <div className="brand-text">
            <span className="brand-name">
              TRC <span className="brand-highlight">BOOTH</span>
            </span>
            <span className="brand-badge">PI EDITION</span>
          </div>
        </div>

        {/* Live Status Pill with Reconnect Click */}
        <button
          type="button"
          onClick={!isConnected ? onManualReconnect : undefined}
          className={`status-pill ${
            isConnected ? 'status-connected' : isConnecting ? 'status-connecting' : 'status-disconnected'
          } ${!isConnected ? 'clickable' : ''}`}
          title={
            isConnected
              ? `Live stream connected (${fps} FPS, ${latency}ms latency)`
              : isConnecting
                ? 'Connecting to Raspberry Pi...'
                : 'Offline - Click to reconnect now'
          }
        >
          {isConnected ? (
            <>
              <span className="status-dot" />
              <span className="status-label">LIVE</span>
              <span className="status-fps">{fps} FPS</span>
            </>
          ) : isConnecting ? (
            <>
              <Wifi className="status-icon animate-pulse" size={14} />
              <span className="status-label">CONNECTING</span>
            </>
          ) : (
            <>
              <RefreshCw className="status-icon" size={14} />
              <span className="status-label">RETRY</span>
            </>
          )}
        </button>
      </div>

      <div className="header-right">
        {/* Toggle Sound Effects */}
        <button
          type="button"
          className={`header-tool-btn ${isSoundActive ? 'active' : ''}`}
          onClick={onToggleSound}
          title={isSoundActive ? 'Mute shutter & countdown audio' : 'Enable sound effects'}
          aria-label="Toggle sound effects"
        >
          {isSoundActive ? <Volume2 size={18} /> : <VolumeX size={18} />}
        </button>

        {/* Toggle Fullscreen Mode */}
        <button
          type="button"
          className={`header-tool-btn ${isFullscreen ? 'active' : ''}`}
          onClick={onToggleFullscreen}
          title={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen photo booth mode'}
          aria-label="Toggle fullscreen mode"
        >
          {isFullscreen ? <Minimize size={18} /> : <Maximize size={18} />}
        </button>

        {/* Toggle Framing Guides */}
        <button
          type="button"
          className={`header-tool-btn ${showGuides ? 'active' : ''}`}
          onClick={onToggleGuides}
          title={showGuides ? 'Hide framing grid' : 'Show Rule of Thirds grid'}
          aria-label="Toggle framing grid"
        >
          <Grid size={18} />
        </button>

        {/* Toggle Mirror Reflection */}
        <button
          type="button"
          className={`header-tool-btn ${isFlipped ? 'active' : ''}`}
          onClick={onToggleFlip}
          title={isFlipped ? 'Disable selfie mirror' : 'Enable selfie mirror'}
          aria-label="Toggle selfie mirror"
        >
          <FlipHorizontal size={18} />
        </button>

        {/* Gallery Shortcut with Badge */}
        <button
          type="button"
          className="header-tool-btn gallery-btn"
          onClick={onOpenGallery}
          title="Open photo gallery"
          aria-label="Open photo gallery"
        >
          <Images size={18} />
          {capturesCount > 0 && <span className="gallery-counter-badge">{capturesCount}</span>}
        </button>

        {/* Settings Button */}
        <button
          type="button"
          className="header-tool-btn settings-btn"
          onClick={onOpenSettings}
          title="Camera & System Settings"
          aria-label="Open settings"
        >
          <SettingsIcon size={18} />
        </button>

        {/* Launch Booth Mode */}
        {onLaunchBooth && (
          <button
            type="button"
            className="header-booth-launch-btn"
            onClick={onLaunchBooth}
            title="Launch 4-Quadrant Photo Booth Mode"
            aria-label="Launch Photo Booth Mode"
          >
            <Camera size={14} />
            <span>BOOTH</span>
          </button>
        )}
      </div>
    </header>
  )
}
