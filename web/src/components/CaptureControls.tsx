import React, { useState } from 'react'
import {
  Camera,
  Clock,
  Film,
  Images,
  Sliders,
  Sparkles,
} from 'lucide-react'
import { CaptureButton } from './CaptureButton'
import type { CaptureMetadata, CaptureType } from '../types'

interface CaptureControlsProps {
  mode: CaptureType
  countdownSetting: number
  isCountingDown: boolean
  isRecordingGif: boolean
  countdownSeconds: number | null
  disabled: boolean
  latestCapture: CaptureMetadata | null
  gifFrames: number
  gifIntervalMs: number
  onModeChange: (mode: CaptureType) => void
  onCountdownSettingChange: (seconds: number) => void
  onGifFramesChange: (frames: number) => void
  onGifIntervalChange: (intervalMs: number) => void
  onTrigger: () => void
  onOpenGallery: () => void
  onToggleFilterBar?: () => void
}

export const CaptureControls: React.FC<CaptureControlsProps> = ({
  mode,
  countdownSetting,
  isCountingDown,
  isRecordingGif,
  countdownSeconds,
  disabled,
  latestCapture,
  gifFrames,
  gifIntervalMs,
  onModeChange,
  onCountdownSettingChange,
  onGifFramesChange,
  onGifIntervalChange,
  onTrigger,
  onOpenGallery,
}) => {
  const [showGifSettings, setShowGifSettings] = useState(false)

  const countdownOptions = [0, 3, 5, 10]

  return (
    <div className="capture-controls-container">
      {/* Top Bar: Mode Switcher & Timer Picker */}
      <div className="controls-top-row">
        {/* Mode Selector Pill */}
        <div className="mode-toggle-group">
          <button
            type="button"
            className={`mode-tab ${mode === 'photo' ? 'active' : ''}`}
            onClick={() => onModeChange('photo')}
          >
            <Camera size={15} />
            <span>PHOTO</span>
          </button>
          <button
            type="button"
            className={`mode-tab ${mode === 'gif' ? 'active' : ''}`}
            onClick={() => onModeChange('gif')}
          >
            <Film size={15} />
            <span>GIF BURST</span>
          </button>
        </div>

        {/* Timer Selector */}
        <div className="timer-selector-group">
          <Clock size={14} className="timer-icon" />
          {countdownOptions.map((sec) => (
            <button
              key={sec}
              type="button"
              className={`timer-chip ${countdownSetting === sec ? 'active' : ''}`}
              onClick={() => onCountdownSettingChange(sec)}
              title={sec === 0 ? 'Instant capture' : `${sec} seconds delay`}
            >
              {sec === 0 ? '0s' : `${sec}s`}
            </button>
          ))}
        </div>

        {/* Optional GIF settings toggle if GIF mode is active */}
        {mode === 'gif' && (
          <button
            type="button"
            className={`gif-settings-trigger ${showGifSettings ? 'active' : ''}`}
            onClick={() => setShowGifSettings((prev) => !prev)}
            title="GIF capture settings"
          >
            <Sliders size={14} />
            <span>{gifFrames}f • {gifIntervalMs}ms</span>
          </button>
        )}
      </div>

      {/* Popover for GIF Options */}
      {showGifSettings && mode === 'gif' && (
        <div className="gif-settings-popover">
          <div className="popover-row">
            <span className="popover-label">Burst Frames:</span>
            <div className="chip-options">
              {[5, 10, 15, 20].map((f) => (
                <button
                  key={f}
                  type="button"
                  className={`option-chip ${gifFrames === f ? 'active' : ''}`}
                  onClick={() => onGifFramesChange(f)}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>
          <div className="popover-row">
            <span className="popover-label">Frame Speed:</span>
            <div className="chip-options">
              {[
                { label: 'Fast (100ms)', val: 100 },
                { label: 'Normal (150ms)', val: 150 },
                { label: 'Slow (250ms)', val: 250 },
              ].map((item) => (
                <button
                  key={item.val}
                  type="button"
                  className={`option-chip ${gifIntervalMs === item.val ? 'active' : ''}`}
                  onClick={() => onGifIntervalChange(item.val)}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Main Bottom Shutter Row */}
      <div className="controls-shutter-row">
        {/* Left Side: Recent Capture Thumbnail / Gallery Trigger */}
        <div className="shutter-side-action left">
          <button
            type="button"
            className="recent-thumbnail-btn"
            onClick={onOpenGallery}
            title={latestCapture ? 'View recent capture' : 'Open gallery'}
          >
            {latestCapture ? (
              <div className="recent-thumb-wrapper">
                <img
                  src={latestCapture.url}
                  alt="Recent capture thumbnail"
                  className="recent-thumb-img"
                />
                <span className="recent-thumb-badge">
                  {latestCapture.type === 'gif' ? 'GIF' : 'NEW'}
                </span>
              </div>
            ) : (
              <div className="recent-thumb-empty">
                <Images size={20} />
              </div>
            )}
          </button>
        </div>

        {/* Center: The Shutter Button */}
        <div className="shutter-center">
          <CaptureButton
            mode={mode}
            isCountingDown={isCountingDown}
            isRecordingGif={isRecordingGif}
            countdownSeconds={countdownSeconds}
            disabled={disabled}
            onTrigger={onTrigger}
          />
        </div>

        {/* Right Side: Quick Gallery Action */}
        <div className="shutter-side-action right">
          <button
            type="button"
            className="side-action-btn gallery-drawer-trigger"
            onClick={onOpenGallery}
            title="Open Gallery"
          >
            <Sparkles size={20} />
            <span className="side-btn-label">GALLERY</span>
          </button>
        </div>
      </div>
    </div>
  )
}
