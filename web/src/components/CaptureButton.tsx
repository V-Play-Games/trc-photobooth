import React, { useEffect } from 'react'
import { Camera, Film } from 'lucide-react'
import type { CaptureType } from '../types'

interface CaptureButtonProps {
  mode: CaptureType
  isCountingDown: boolean
  isRecordingGif: boolean
  countdownSeconds: number | null
  disabled: boolean
  onTrigger: () => void
}

export const CaptureButton: React.FC<CaptureButtonProps> = ({
  mode,
  isCountingDown,
  isRecordingGif,
  countdownSeconds,
  disabled,
  onTrigger,
}) => {
  // Listen for Spacebar shortcut
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't trigger if user is typing in an input
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
        return
      }
      if (e.code === 'Space' && !disabled && !isCountingDown && !isRecordingGif) {
        e.preventDefault()
        onTrigger()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [disabled, isCountingDown, isRecordingGif, onTrigger])

  const handleClick = () => {
    if (disabled || isCountingDown || isRecordingGif) return

    // Mobile haptic vibration if supported
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(60)
      } catch {
        // Ignore
      }
    }

    onTrigger()
  }

  const isBusy = isCountingDown || isRecordingGif

  return (
    <div className={`capture-button-outer ${mode === 'gif' ? 'mode-gif' : 'mode-photo'} ${isBusy ? 'busy' : ''}`}>
      <div className="shutter-pulse-ring" />
      <button
        type="button"
        className={`shutter-btn ${isBusy ? 'disabled' : ''}`}
        onClick={handleClick}
        disabled={disabled || isBusy}
        aria-label={
          isCountingDown
            ? `Counting down: ${countdownSeconds}s`
            : isRecordingGif
              ? 'Recording animated GIF'
              : mode === 'gif'
                ? 'Record animated GIF (Spacebar)'
                : 'Take high-resolution photo (Spacebar)'
        }
        title={mode === 'gif' ? 'Record GIF (Press Space)' : 'Take Photo (Press Space)'}
      >
        <div className="shutter-inner">
          {isCountingDown ? (
            <span className="shutter-countdown-num">{countdownSeconds}</span>
          ) : isRecordingGif ? (
            <div className="shutter-recording-spinner" />
          ) : mode === 'gif' ? (
            <Film className="shutter-icon" size={32} />
          ) : (
            <Camera className="shutter-icon" size={32} />
          )}
        </div>
      </button>
    </div>
  )
}
