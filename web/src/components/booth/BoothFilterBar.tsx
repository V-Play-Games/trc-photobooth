import React from 'react'
import { Check, Dices, Sparkles } from 'lucide-react'
import { FILTER_LIST } from '../../filters/presets'
import type { FilterId } from '../../types'

interface BoothFilterBarProps {
  selectedFilter: FilterId
  onSelectFilter: (id: FilterId) => void
  disabled?: boolean
}

export const BoothFilterBar: React.FC<BoothFilterBarProps> = ({
  selectedFilter,
  onSelectFilter,
  disabled = false,
}) => {
  return (
    <div className={`booth-filter-bar ${disabled ? 'booth-filter-bar-disabled' : ''}`}>
      <div className="booth-filter-header">
        <div className="booth-filter-title">
          <Sparkles size={13} className="text-cyan" />
          <span>SELECT FILTER STYLE</span>
        </div>
        {selectedFilter === 'random' ? (
          <div className="booth-filter-sublabel text-purple">
            <Dices size={13} />
            <span>Surprise Every Shot</span>
          </div>
        ) : (
          <div className="booth-filter-sublabel text-muted">
            <span>{FILTER_LIST.find((f) => f.id === selectedFilter)?.name || 'Normal'}</span>
          </div>
        )}
      </div>

      <div className="booth-filter-carousel" role="radiogroup" aria-label="Booth filter selection">
        {FILTER_LIST.map((preset) => {
          const isSelected = selectedFilter === preset.id
          const isRandom = preset.id === 'random'

          return (
            <button
              key={preset.id}
              type="button"
              role="radio"
              aria-checked={isSelected}
              disabled={disabled}
              className={`booth-filter-card ${isSelected ? 'selected' : ''} ${
                isRandom ? 'filter-card-random' : ''
              }`}
              style={{
                borderColor: isSelected
                  ? isRandom
                    ? 'var(--color-purple)'
                    : preset.accentColor
                  : 'var(--border-subtle)',
              }}
              onClick={() => onSelectFilter(preset.id)}
            >
              <div className="card-top-row">
                {isRandom ? (
                  <Dices size={16} className="text-purple" />
                ) : (
                  <span
                    className="card-color-dot"
                    style={{ backgroundColor: preset.accentColor }}
                  />
                )}
                {isSelected && (
                  <span
                    className="card-check-pill"
                    style={{
                      backgroundColor: isRandom ? 'var(--color-purple)' : preset.accentColor,
                    }}
                  >
                    <Check size={10} color="#000" />
                  </span>
                )}
              </div>

              <div className="card-info">
                <span className="card-name">{preset.name}</span>
                <span className="card-tagline">{preset.tagline}</span>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
