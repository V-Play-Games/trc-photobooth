import React, { useEffect, useRef } from 'react'
import { Check, Sparkles } from 'lucide-react'
import { FILTER_PRESETS } from '../../filters/presets'
import type { FilterId } from '../../types'

interface BoothQuadrantGridProps {
  lastFrameBitmap: ImageBitmap | null
  activeQuadrant: number
  capturedUrls: (string | null)[]
  resolvedFilters: FilterId[]
  activeFeedFilter: FilterId
  isFlipped: boolean
  isConnected: boolean
}

export const BoothQuadrantGrid: React.FC<BoothQuadrantGridProps> = ({
  lastFrameBitmap,
  activeQuadrant,
  capturedUrls,
  resolvedFilters,
  activeFeedFilter,
  isFlipped,
  isConnected,
}) => {
  const activeCanvasRef = useRef<HTMLCanvasElement | null>(null)
  const feedPreset = FILTER_PRESETS[activeFeedFilter] || FILTER_PRESETS.none

  // Render incoming frame to active quadrant's canvas
  useEffect(() => {
    const canvas = activeCanvasRef.current
    if (!canvas || !lastFrameBitmap) return

    const ctx = canvas.getContext('2d', { alpha: false })
    if (!ctx) return

    if (canvas.width !== lastFrameBitmap.width || canvas.height !== lastFrameBitmap.height) {
      canvas.width = lastFrameBitmap.width
      canvas.height = lastFrameBitmap.height
    }

    ctx.drawImage(lastFrameBitmap, 0, 0)
  }, [lastFrameBitmap])

  const renderCell = (index: number) => {
    const isCellActive = activeQuadrant === index
    const capturedUrl = capturedUrls[index]
    const resolvedFilterId = resolvedFilters[index] || 'none'
    const resolvedPreset = FILTER_PRESETS[resolvedFilterId] || FILTER_PRESETS.none

    return (
      <div
        key={index}
        className={`booth-quadrant-cell ${
          isCellActive ? 'quadrant-active' : capturedUrl ? 'quadrant-captured' : 'quadrant-empty'
        }`}
      >
        {capturedUrl ? (
          // State 1: Captured photo snapshot
          <div className="quadrant-content-wrapper">
            <img
              src={capturedUrl}
              alt={`Photo ${index + 1}`}
              className="quadrant-captured-img"
            />
            <div className="quadrant-badge-captured">
              <Check size={13} className="text-green" />
              <span>#{index + 1} {resolvedPreset.name}</span>
            </div>
          </div>
        ) : isCellActive ? (
          // State 2: Active live viewfinder feed
          <div className={`quadrant-content-wrapper ${isFlipped ? 'mirror-flipped' : ''}`}>
            {isConnected && lastFrameBitmap ? (
              <>
                <canvas
                  ref={activeCanvasRef}
                  className="quadrant-live-canvas"
                  style={{
                    filter: feedPreset.cssFilter,
                  }}
                />
                {activeFeedFilter === 'vignette' && <div className="overlay-vignette" />}
                {activeFeedFilter === 'film_grain' && <div className="overlay-grain" />}
                {activeFeedFilter === 'polaroid' && <div className="overlay-polaroid-tint" />}
              </>
            ) : (
              <div className="quadrant-waiting-feed">
                <span className="animate-spin inline-block mr-2">◌</span>
                <span>Connecting to camera...</span>
              </div>
            )}

            {/* Live Indicator Pill */}
            <div className="quadrant-badge-live">
              <span className="live-dot-pulse" />
              <span>LIVE • #{index + 1}</span>
            </div>

            {/* Active Filter Pill */}
            <div className="quadrant-badge-filter" style={{ color: feedPreset.accentColor }}>
              <Sparkles size={11} />
              <span>{feedPreset.name}</span>
            </div>
          </div>
        ) : (
          // State 3: Empty / Waiting quadrant
          <div className="quadrant-empty-content">
            <div className="quadrant-number-circle">{index + 1}</div>
            <span className="quadrant-empty-label">Photo {index + 1}</span>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="booth-quadrant-grid">
      <div className="booth-quadrant-row">
        {renderCell(0)}
        {renderCell(1)}
      </div>
      <div className="booth-quadrant-row">
        {renderCell(2)}
        {renderCell(3)}
      </div>
    </div>
  )
}
