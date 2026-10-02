import React from 'react'
import {
  Camera,
  Grid,
  FlipHorizontal,
  Images,
  Settings as SettingsIcon,
  Wifi,
  WifiOff,
} from 'lucide-react'
import type { WebSocketConnectionStatus } from '../types'

interface HeaderProps {
  connectionStatus: WebSocketConnectionStatus
  fps: number
  latency: number
  capturesCount: number
  showGuides: boolean
  isFlipped: boolean
  onToggleGuides: () => void
  onToggleFlip: () => void
  onOpenGallery: () => void
  onOpenSettings: () => void
}

export const Header: React.FC<HeaderProps> = ({
  connectionStatus,
  fps,
  latency,
  capturesCount,
  showGuides,
  isFlipped,
  onToggleGuides,
  onToggleFlip,
  onOpenGallery,
  onOpenSettings,
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

        {/* Live Status Pill */}
        <div
          className={`status-pill ${
            isConnected ? 'status-connected' : isConnecting ? 'status-connecting' : 'status-disconnected'
          }`}
          title={
            isConnected
              ? `Live stream connected (${fps} FPS, ${latency}ms latency)`
              : isConnecting
                ? 'Connecting to Raspberry Pi...'
                : 'Offline - Attempting auto-reconnect'
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
              <WifiOff className="status-icon" size={14} />
              <span className="status-label">OFFLINE</span>
            </>
          )}
        </div>
      </div>

      <div className="header-right">
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
      </div>
    </header>
  )
}
