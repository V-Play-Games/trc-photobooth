import React, { useCallback, useEffect, useState } from 'react'
import {
  Download,
  Share2,
  Trash2,
  X,
  Sparkles,
  ChevronLeft,
  ChevronRight,
  Check,
  CheckCheck,
} from 'lucide-react'
import { FILTER_LIST, FILTER_PRESETS, bakeFilterToImageBlob } from '../filters/presets'
import type { CaptureMetadata, FilterId } from '../types'

interface LightboxModalProps {
  capture: CaptureMetadata | null
  capturesList: CaptureMetadata[]
  defaultFilter: FilterId
  onClose: () => void
  onDelete: (id: string) => Promise<boolean>
  onNavigate: (capture: CaptureMetadata) => void
}

export const LightboxModal: React.FC<LightboxModalProps> = ({
  capture,
  capturesList,
  defaultFilter,
  onClose,
  onDelete,
  onNavigate,
}) => {
  const [selectedFilter, setSelectedFilter] = useState<FilterId>(defaultFilter)
  const [includePolaroid, setIncludePolaroid] = useState(defaultFilter === 'polaroid')
  const [isBaking, setIsBaking] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [copiedLink, setCopiedLink] = useState(false)

  const isGif = capture?.type === 'gif'
  const currentIndex = capture ? capturesList.findIndex((c) => c.id === capture.id) : -1
  const hasPrev = currentIndex > 0
  const hasNext = currentIndex !== -1 && currentIndex < capturesList.length - 1

  const handlePrev = useCallback(() => {
    if (hasPrev) onNavigate(capturesList[currentIndex - 1])
  }, [hasPrev, onNavigate, capturesList, currentIndex])

  const handleNext = useCallback(() => {
    if (hasNext) onNavigate(capturesList[currentIndex + 1])
  }, [hasNext, onNavigate, capturesList, currentIndex])

  // Keyboard navigation (Esc to close, Left/Right arrows to flip)
  useEffect(() => {
    if (!capture) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      } else if (e.key === 'ArrowLeft') {
        handlePrev()
      } else if (e.key === 'ArrowRight') {
        handleNext()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [capture, onClose, handlePrev, handleNext])

  if (!capture) return null

  const handleDownload = async () => {
    if (isGif || selectedFilter === 'none') {
      // Direct download of raw photo or GIF
      const link = document.createElement('a')
      link.href = capture.url
      link.download = capture.filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      return
    }

    // Bake filter into the high-res capture
    setIsBaking(true)
    try {
      const blob = await bakeFilterToImageBlob(
        capture.url,
        selectedFilter,
        includePolaroid || selectedFilter === 'polaroid',
      )
      const blobUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = blobUrl
      link.download = `${capture.id}_${selectedFilter}.jpg`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      setTimeout(() => URL.revokeObjectURL(blobUrl), 3000)
    } catch (err) {
      console.error('Error baking filter to photo:', err)
      // Fallback to direct raw download
      const link = document.createElement('a')
      link.href = capture.url
      link.download = capture.filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    } finally {
      setIsBaking(false)
    }
  }

  const handleShare = async () => {
    const fullUrl = new URL(capture.url, window.location.origin).href

    if (navigator.share) {
      try {
        if (selectedFilter !== 'none' && !isGif) {
          // Attempt sharing baked file if possible
          const blob = await bakeFilterToImageBlob(
            capture.url,
            selectedFilter,
            includePolaroid || selectedFilter === 'polaroid',
          )
          const file = new File([blob], `${capture.id}_${selectedFilter}.jpg`, {
            type: 'image/jpeg',
          })
          if (navigator.canShare && navigator.canShare({ files: [file] })) {
            await navigator.share({
              title: 'TRC Photo Booth Capture',
              text: 'Check out my TRC Photo Booth snapshot!',
              files: [file],
            })
            return
          }
        }

        await navigator.share({
          title: 'TRC Photo Booth Capture',
          text: 'Check out my TRC Photo Booth snapshot!',
          url: fullUrl,
        })
      } catch (err) {
        // User cancelled or share failed
        console.debug('Share cancelled or not supported', err)
      }
    } else {
      // Clipboard copy fallback
      try {
        await navigator.clipboard.writeText(fullUrl)
        setCopiedLink(true)
        setTimeout(() => setCopiedLink(false), 2000)
      } catch {
        // Ignore
      }
    }
  }

  const handleDelete = async () => {
    if (confirm('Delete this capture from photo booth memory?')) {
      setIsDeleting(true)
      const ok = await onDelete(capture.id)
      setIsDeleting(false)
      if (ok) {
        onClose()
      }
    }
  }

  const currentPreset = FILTER_PRESETS[selectedFilter] || FILTER_PRESETS.none

  return (
    <div className="lightbox-backdrop" onClick={onClose}>
      <div className="lightbox-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Top Header */}
        <div className="lightbox-header">
          <div className="lightbox-title-meta">
            <span className="lightbox-filename">{capture.filename}</span>
            <span className="lightbox-specs">
              {capture.width}×{capture.height} • {Math.round(capture.size_bytes / 1024)} KB
            </span>
          </div>

          <button
            type="button"
            className="lightbox-close-btn"
            onClick={onClose}
            aria-label="Close lightbox"
          >
            <X size={20} />
          </button>
        </div>

        {/* Media Display Area */}
        <div className="lightbox-media-viewport">
          {hasPrev && (
            <button
              type="button"
              className="lightbox-nav-btn prev"
              onClick={handlePrev}
              title="Previous capture (Left Arrow)"
              aria-label="Previous capture"
            >
              <ChevronLeft size={24} />
            </button>
          )}

          <div
            className={`lightbox-image-wrapper ${
              includePolaroid || selectedFilter === 'polaroid' ? 'polaroid-frame-active' : ''
            }`}
          >
            <img
              src={capture.url}
              alt="Expanded capture"
              className="lightbox-img"
              style={
                !isGif
                  ? {
                      filter: currentPreset.cssFilter,
                    }
                  : undefined
              }
            />

            {/* Overlays for Vignette & Grain preview in lightbox */}
            {!isGif && selectedFilter === 'vignette' && (
              <div className="overlay-vignette lightbox-overlay" />
            )}
            {!isGif && selectedFilter === 'film_grain' && (
              <div className="overlay-grain lightbox-overlay" />
            )}

            {/* Polaroid frame bottom caption if active */}
            {!isGif && (includePolaroid || selectedFilter === 'polaroid') && (
              <div className="polaroid-caption-bar">
                <span>TRC PHOTO BOOTH</span>
              </div>
            )}
          </div>

          {hasNext && (
            <button
              type="button"
              className="lightbox-nav-btn next"
              onClick={handleNext}
              title="Next capture (Right Arrow)"
              aria-label="Next capture"
            >
              <ChevronRight size={24} />
            </button>
          )}
        </div>

        {/* Filter Selection for Photos */}
        {!isGif && (
          <div className="lightbox-filter-picker">
            <div className="lightbox-picker-label">
              <Sparkles size={14} />
              <span>APPLY FILTER:</span>
            </div>
            <div className="lightbox-filter-chips">
              {FILTER_LIST.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  className={`lightbox-filter-chip ${selectedFilter === f.id ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedFilter(f.id)
                    setIncludePolaroid(f.id === 'polaroid')
                  }}
                  style={{
                    borderColor: selectedFilter === f.id ? f.accentColor : undefined,
                  }}
                >
                  {selectedFilter === f.id && <Check size={12} />}
                  <span>{f.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Bottom Actions Bar */}
        <div className="lightbox-footer">
          <div className="lightbox-footer-left">
            <button
              type="button"
              className="lightbox-action-btn delete-btn"
              onClick={handleDelete}
              disabled={isDeleting}
              title="Delete capture"
            >
              <Trash2 size={16} />
              <span>Delete</span>
            </button>
          </div>

          <div className="lightbox-footer-right">
            <button
              type="button"
              className="lightbox-action-btn share-btn"
              onClick={handleShare}
              title="Share photo"
            >
              {copiedLink ? <CheckCheck size={16} /> : <Share2 size={16} />}
              <span>{copiedLink ? 'Link Copied!' : 'Share'}</span>
            </button>

            <button
              type="button"
              className="lightbox-action-btn download-btn"
              onClick={handleDownload}
              disabled={isBaking}
              title="Download photo with applied filter"
            >
              <Download size={16} />
              <span>
                {isBaking
                  ? 'Baking Filter...'
                  : selectedFilter !== 'none' && !isGif
                    ? `Download (${currentPreset.name})`
                    : 'Download'}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
