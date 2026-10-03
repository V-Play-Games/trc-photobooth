import React from 'react'
import { Camera } from 'lucide-react'

interface BoothCountdownOverlayProps {
  photoIndex: number
  secondsLeft: number | null
  isFlashing: boolean
  activeFilterName?: string
}

export const BoothCountdownOverlay: React.FC<BoothCountdownOverlayProps> = ({
  photoIndex,
  secondsLeft,
  isFlashing,
  activeFilterName,
}) => {
  const getPromptText = (sec: number) => {
    if (sec > 2) return 'GET READY!'
    if (sec === 2) return 'STRIKE A POSE! 📸'
    if (sec === 1) return 'SMILE! ✨'
    return 'HOLD STILL!'
  }

  return (
    <>
      {/* Countdown Overlay */}
      {secondsLeft !== null && (
        <div className="booth-countdown-overlay" aria-live="assertive">
          <div className="booth-countdown-container">
            {/* Sequence Tracker */}
            <div className="booth-sequence-pill">
              <Camera size={14} className="text-cyan" />
              <span>PHOTO {photoIndex + 1} OF 4</span>
            </div>

            {/* Giant Countdown Digit */}
            <div className="booth-countdown-number-ring">
              <span className="booth-countdown-number">{secondsLeft}</span>
            </div>

            {/* Pose Advice */}
            <div className="booth-countdown-prompt">{getPromptText(secondsLeft)}</div>

            {/* Active Filter Note */}
            {activeFilterName && (
              <div className="booth-countdown-filter-note">Filter: {activeFilterName}</div>
            )}
          </div>
        </div>
      )}

      {/* Camera Strobe Flash */}
      {isFlashing && <div className="booth-strobe-flash" aria-hidden="true" />}
    </>
  )
}
