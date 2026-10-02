import React, { useState } from 'react'
import {
  Camera,
  Film,
  Images,
  RefreshCw,
  Sparkles,
  X,
} from 'lucide-react'
import { PhotoCard } from './PhotoCard'
import type { CaptureMetadata } from '../types'

interface GalleryDrawerProps {
  isOpen: boolean
  captures: CaptureMetadata[]
  isLoading: boolean
  onClose: () => void
  onRefresh: () => void
  onSelectCapture: (capture: CaptureMetadata) => void
  onDeleteCapture: (id: string, e: React.MouseEvent) => void
  onDownloadCapture: (capture: CaptureMetadata, e: React.MouseEvent) => void
}

export const GalleryDrawer: React.FC<GalleryDrawerProps> = ({
  isOpen,
  captures,
  isLoading,
  onClose,
  onRefresh,
  onSelectCapture,
  onDeleteCapture,
  onDownloadCapture,
}) => {
  const [filterType, setFilterType] = useState<'all' | 'photo' | 'gif'>('all')

  if (!isOpen) return null

  const filteredCaptures = captures.filter((c) => {
    if (filterType === 'all') return true
    return c.type === filterType
  })

  return (
    <div className="gallery-drawer-backdrop" onClick={onClose}>
      <aside
        className="gallery-drawer-panel"
        onClick={(e) => e.stopPropagation()}
        aria-label="Captures Gallery"
      >
        {/* Drawer Header */}
        <div className="gallery-drawer-header">
          <div className="drawer-title-group">
            <div className="drawer-title-icon">
              <Images size={18} />
            </div>
            <div>
              <h2 className="drawer-title">BOOTH GALLERY</h2>
              <span className="drawer-subtitle">
                {captures.length} {captures.length === 1 ? 'capture' : 'captures'} stored in session
              </span>
            </div>
          </div>

          <div className="drawer-header-actions">
            <button
              type="button"
              className={`drawer-action-btn ${isLoading ? 'spinning' : ''}`}
              onClick={onRefresh}
              title="Refresh captures"
              aria-label="Refresh captures"
            >
              <RefreshCw size={16} />
            </button>
            <button
              type="button"
              className="drawer-action-btn close-btn"
              onClick={onClose}
              title="Close gallery"
              aria-label="Close gallery"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Filter Pills */}
        <div className="gallery-filters-bar">
          <button
            type="button"
            className={`gallery-filter-pill ${filterType === 'all' ? 'active' : ''}`}
            onClick={() => setFilterType('all')}
          >
            <span>All</span>
            <span className="count-badge">{captures.length}</span>
          </button>
          <button
            type="button"
            className={`gallery-filter-pill ${filterType === 'photo' ? 'active' : ''}`}
            onClick={() => setFilterType('photo')}
          >
            <Camera size={13} />
            <span>Photos</span>
            <span className="count-badge">
              {captures.filter((c) => c.type === 'photo').length}
            </span>
          </button>
          <button
            type="button"
            className={`gallery-filter-pill ${filterType === 'gif' ? 'active' : ''}`}
            onClick={() => setFilterType('gif')}
          >
            <Film size={13} />
            <span>GIFs</span>
            <span className="count-badge">
              {captures.filter((c) => c.type === 'gif').length}
            </span>
          </button>
        </div>

        {/* Drawer Scroll Body */}
        <div className="gallery-drawer-body">
          {filteredCaptures.length > 0 ? (
            <div className="gallery-cards-grid">
              {filteredCaptures.map((capture) => (
                <PhotoCard
                  key={capture.id}
                  capture={capture}
                  onSelect={onSelectCapture}
                  onDelete={onDeleteCapture}
                  onDownload={onDownloadCapture}
                />
              ))}
            </div>
          ) : (
            <div className="gallery-empty-state">
              <div className="empty-state-icon-box">
                <Sparkles size={36} className="empty-sparkle" />
              </div>
              <h3 className="empty-state-title">No captures yet</h3>
              <p className="empty-state-desc">
                {filterType === 'all'
                  ? 'Strike a pose and click the shutter button or press Spacebar to take your first photo!'
                  : `No ${filterType === 'photo' ? 'photos' : 'GIFs'} captured yet.`}
              </p>
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}
