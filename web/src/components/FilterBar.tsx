import React, { useRef } from 'react'
import { ChevronLeft, ChevronRight, Sparkles } from 'lucide-react'
import { FILTER_LIST } from '../filters/presets'
import type { FilterId } from '../types'

interface FilterBarProps {
  activeFilter: FilterId
  onSelectFilter: (filterId: FilterId) => void
}

export const FilterBar: React.FC<FilterBarProps> = ({
  activeFilter,
  onSelectFilter,
}) => {
  const scrollRef = useRef<HTMLDivElement | null>(null)

  const handleScroll = (direction: 'left' | 'right') => {
    if (!scrollRef.current) return
    const offset = direction === 'left' ? -220 : 220
    scrollRef.current.scrollBy({ left: offset, behavior: 'smooth' })
  }

  return (
    <div className="filter-bar-wrapper">
      <div className="filter-bar-header">
        <div className="filter-bar-title">
          <Sparkles size={15} className="filter-title-icon" />
          <span>PHOTO FILTERS</span>
        </div>
        <div className="filter-scroll-arrows">
          <button
            type="button"
            className="filter-arrow-btn"
            onClick={() => handleScroll('left')}
            aria-label="Scroll filters left"
          >
            <ChevronLeft size={16} />
          </button>
          <button
            type="button"
            className="filter-arrow-btn"
            onClick={() => handleScroll('right')}
            aria-label="Scroll filters right"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      <div className="filter-cards-scroll" ref={scrollRef}>
        {FILTER_LIST.map((preset) => {
          const isSelected = activeFilter === preset.id

          return (
            <button
              key={preset.id}
              type="button"
              className={`filter-card ${isSelected ? 'active' : ''}`}
              onClick={() => onSelectFilter(preset.id)}
              style={
                isSelected
                  ? ({
                      '--card-accent': preset.accentColor,
                    } as React.CSSProperties)
                  : undefined
              }
            >
              {/* Preview Thumbnail Swatch */}
              <div className="filter-swatch-box">
                <div
                  className="filter-swatch-content"
                  style={{
                    filter: preset.cssFilter,
                  }}
                >
                  <div className="swatch-gradient" />
                  <div className="swatch-mini-avatar" />
                </div>
                {preset.id === 'vignette' && <div className="swatch-overlay-vignette" />}
                {preset.id === 'film_grain' && <div className="swatch-overlay-grain" />}
                {preset.id === 'polaroid' && <div className="swatch-overlay-polaroid" />}

                {isSelected && <div className="filter-selected-check">✓</div>}
              </div>

              {/* Filter Label */}
              <div className="filter-card-meta">
                <span className="filter-name">{preset.name}</span>
                <span className="filter-tagline">{preset.tagline}</span>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
