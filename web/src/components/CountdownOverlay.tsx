import React from 'react'
import type { CountdownState } from '../types'

interface CountdownOverlayProps {
  countdown: CountdownState | null
  isFlashing: boolean
}

export const CountdownOverlay: React.FC<CountdownOverlayProps> = ({
  countdown,
  isFlashing,
}) => {
  return (
    <>
      {/* Studio Camera Strobe Flash Effect */}
      <div
        className={`camera-flash-overlay ${isFlashing ? 'flashing' : ''}`}
        aria-hidden="true"
      />

      {/* Countdown Digits Overlay */}
      {countdown && (
        <div className="countdown-fullscreen-overlay">
          <div className="countdown-backdrop" />
          <div className="countdown-content" key={countdown.secondsLeft}>
            {countdown.secondsLeft > 0 ? (
              <>
                <div className="countdown-digit-wrapper">
                  <span className="countdown-digit">{countdown.secondsLeft}</span>
                  <div className="countdown-ring" />
                </div>
                <div className="countdown-subtext">GET READY!</div>
              </>
            ) : (
              <div className="countdown-smile-box">
                <span className="countdown-smile-text">SMILE!</span>
                <span className="countdown-smile-emoji">✨📸✨</span>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
