import React from 'react'
import { Download, Film, Image as ImageIcon, Trash2 } from 'lucide-react'
import type { CaptureMetadata } from '../types'

interface PhotoCardProps {
  capture: CaptureMetadata
  onSelect: (capture: CaptureMetadata) => void
  onDelete: (id: string, e: React.MouseEvent) => void
  onDownload: (capture: CaptureMetadata, e: React.MouseEvent) => void
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatTime(epochSeconds: number): string {
  const elapsed = Math.floor(Date.now() / 1000 - epochSeconds)
  if (elapsed < 60) return 'Just now'
  if (elapsed < 3600) return `${Math.floor(elapsed / 60)}m ago`
  if (elapsed < 86400) return `${Math.floor(elapsed / 3600)}h ago`
  return new Date(epochSeconds * 1000).toLocaleDateString()
}

export const PhotoCard: React.FC<PhotoCardProps> = ({
  capture,
  onSelect,
  onDelete,
  onDownload,
}) => {
  const isGif = capture.type === 'gif'

  return (
    <div className="photo-card" onClick={() => onSelect(capture)}>
      <div className="photo-card-media-wrapper">
        <img
          src={capture.url}
          alt={`Capture ${capture.filename}`}
          className="photo-card-img"
          loading="lazy"
        />

        {/* Top Badges */}
        <div className="photo-card-badges">
          <span className={`media-type-badge ${isGif ? 'badge-gif' : 'badge-photo'}`}>
            {isGif ? (
              <>
                <Film size={11} />
                <span>GIF</span>
              </>
            ) : (
              <>
                <ImageIcon size={11} />
                <span>PHOTO</span>
              </>
            )}
          </span>

          <span className="file-size-badge">{formatFileSize(capture.size_bytes)}</span>
        </div>

        {/* Hover Quick Actions */}
        <div className="photo-card-overlay-actions">
          <button
            type="button"
            className="card-quick-btn download"
            onClick={(e) => onDownload(capture, e)}
            title="Download"
            aria-label="Download capture"
          >
            <Download size={15} />
          </button>
          <button
            type="button"
            className="card-quick-btn delete"
            onClick={(e) => onDelete(capture.id, e)}
            title="Delete"
            aria-label="Delete capture"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </div>

      <div className="photo-card-footer">
        <div className="card-meta-left">
          <span className="card-timestamp">{formatTime(capture.created_at)}</span>
          <span className="card-dims">
            {capture.width}×{capture.height}
          </span>
        </div>
      </div>
    </div>
  )
}
