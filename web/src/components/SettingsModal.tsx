import React, { useEffect, useState } from 'react'
import {
  Activity,
  Camera,
  Cpu,
  ExternalLink,
  Flame,
  HardDrive,
  RefreshCw,
  Sliders,
  Tv,
  X,
} from 'lucide-react'
import type { CameraConfig, SystemStats } from '../types'

interface SettingsModalProps {
  isOpen: boolean
  cameraConfig: Partial<CameraConfig>
  systemStats: SystemStats | null
  onClose: () => void
  onSetFps: (fps: number) => void
  onSetQuality: (quality: number) => void
  onSetResolution: (res: string, aspect?: string) => void
  onToggleFlip: (val?: boolean) => void
  onToggleSwapRb: (val?: boolean) => void
  onRequestStats: () => void
  onSetCameraDevice?: (device: string | number) => void
  onRefreshDevices?: () => void
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  cameraConfig,
  systemStats,
  onClose,
  onSetFps,
  onSetQuality,
  onSetResolution,
  onToggleFlip,
  onToggleSwapRb,
  onRequestStats,
  onSetCameraDevice,
  onRefreshDevices,
}) => {
  const currentFps = cameraConfig.fps ?? 15
  const currentQuality = cameraConfig.quality ?? 70
  const [customDevice, setCustomDevice] = useState<string>(
    cameraConfig.device_path || `/dev/video${cameraConfig.webcam_device ?? 0}`,
  )

  useEffect(() => {
    if (cameraConfig.device_path) {
      setCustomDevice(cameraConfig.device_path)
    }
  }, [cameraConfig.device_path])

  const activeRes: '480p' | '720p' | '1080p' = cameraConfig.width
    ? cameraConfig.width >= 1400
      ? '1080p'
      : cameraConfig.width >= 900
        ? '720p'
        : '480p'
    : '480p'

  // Poll system stats periodically while modal is open
  useEffect(() => {
    if (!isOpen) return
    onRequestStats()
    onRefreshDevices?.()
    const timer = setInterval(onRequestStats, 3000)
    return () => clearInterval(timer)
  }, [isOpen, onRequestStats, onRefreshDevices])

  if (!isOpen) return null

  const handleFpsChange = (val: number) => {
    onSetFps(val)
  }

  const handleQualityChange = (val: number) => {
    onSetQuality(val)
  }

  const handleResolutionSelect = (res: '480p' | '720p' | '1080p') => {
    onSetResolution(res, '4:3')
  }

  return (
    <div className="settings-backdrop" onClick={onClose}>
      <div className="settings-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="settings-header">
          <div className="settings-title-group">
            <Sliders size={20} className="settings-header-icon" />
            <div>
              <h2 className="settings-title">SETTINGS & TELEMETRY</h2>
              <span className="settings-subtitle">Raspberry Pi Camera & System Tuning</span>
            </div>
          </div>
          <button
            type="button"
            className="settings-close-btn"
            onClick={onClose}
            aria-label="Close settings"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="settings-body">
          {/* Section 1: Live Stream Tuning */}
          <section className="settings-section">
            <h3 className="section-title">
              <Tv size={15} />
              <span>PREVIEW STREAM CONTROLS</span>
            </h3>

            {/* Hardware Camera Device Selection */}
            <div className="setting-control-group">
              <div className="control-label-row">
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Camera size={14} style={{ color: '#00F0FF' }} />
                  <span className="control-label">Camera Hardware (/dev/video*)</span>
                </div>
                <button
                  type="button"
                  onClick={() => onRefreshDevices?.()}
                  title="Scan system for camera hardware"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    background: 'rgba(0, 240, 255, 0.15)',
                    border: '1px solid rgba(0, 240, 255, 0.3)',
                    color: '#00F0FF',
                    borderRadius: '6px',
                    padding: '3px 8px',
                    fontSize: '11px',
                    cursor: 'pointer',
                  }}
                >
                  <RefreshCw size={12} />
                  <span>Scan</span>
                </button>
              </div>

              {/* Device Chips */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                {(cameraConfig.available_devices && cameraConfig.available_devices.length > 0
                  ? cameraConfig.available_devices
                  : [
                      {
                        device: cameraConfig.device_path || `/dev/video${cameraConfig.webcam_device ?? 0}`,
                        index: cameraConfig.webcam_device ?? 0,
                        name: 'Active Hardware Camera',
                        available: true,
                      },
                    ]
                ).map((dev) => {
                  const currentSelected =
                    cameraConfig.device_path || `/dev/video${cameraConfig.webcam_device ?? 0}`
                  const isSelected =
                    dev.device === currentSelected || dev.index === cameraConfig.webcam_device

                  return (
                    <div
                      key={dev.device}
                      onClick={() => onSetCameraDevice?.(dev.device)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 12px',
                        borderRadius: '8px',
                        background: isSelected ? 'rgba(0, 240, 255, 0.12)' : 'rgba(255, 255, 255, 0.04)',
                        border: isSelected ? '1px solid #00F0FF' : '1px solid rgba(255, 255, 255, 0.08)',
                        cursor: 'pointer',
                        transition: 'all 0.15s ease',
                      }}
                    >
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span
                            style={{
                              fontFamily: 'monospace',
                              fontWeight: 700,
                              fontSize: '12px',
                              color: isSelected ? '#00F0FF' : '#E2E8F0',
                            }}
                          >
                            {dev.device}
                          </span>
                          {isSelected && (
                            <span
                              style={{
                                fontSize: '9px',
                                fontWeight: 800,
                                background: '#00F0FF',
                                color: '#000',
                                padding: '1px 5px',
                                borderRadius: '4px',
                                letterSpacing: '0.05em',
                              }}
                            >
                              ACTIVE
                            </span>
                          )}
                        </div>
                        {dev.name && (
                          <span style={{ fontSize: '11px', color: '#94A3B8' }}>{dev.name}</span>
                        )}
                      </div>
                      <span
                        style={{
                          fontSize: '11px',
                          fontWeight: 600,
                          color: isSelected ? '#00F0FF' : '#64748B',
                        }}
                      >
                        {isSelected ? 'Selected' : 'Select'}
                      </span>
                    </div>
                  )
                })}
              </div>

              {/* Custom device path input */}
              <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                <input
                  type="text"
                  value={customDevice}
                  onChange={(e) => setCustomDevice(e.target.value)}
                  placeholder="e.g. /dev/video1"
                  style={{
                    flex: 1,
                    background: 'rgba(15, 23, 42, 0.8)',
                    border: '1px solid rgba(255, 255, 255, 0.12)',
                    borderRadius: '6px',
                    padding: '6px 10px',
                    color: '#F8FAFC',
                    fontSize: '12px',
                    fontFamily: 'monospace',
                  }}
                />
                <button
                  type="button"
                  onClick={() => {
                    if (customDevice.trim()) {
                      onSetCameraDevice?.(customDevice.trim())
                    }
                  }}
                  style={{
                    background: '#00F0FF',
                    color: '#000',
                    fontWeight: 700,
                    fontSize: '12px',
                    borderRadius: '6px',
                    padding: '6px 14px',
                    border: 'none',
                    cursor: 'pointer',
                  }}
                >
                  Switch
                </button>
              </div>
            </div>

            {/* Target FPS Slider */}
            <div className="setting-control-group">
              <div className="control-label-row">
                <span className="control-label">Target Frame Rate</span>
                <span className="control-value-badge">{currentFps} FPS</span>
              </div>
              <input
                type="range"
                min="5"
                max="60"
                step="1"
                value={currentFps}
                onChange={(e) => handleFpsChange(Number(e.target.value))}
                className="setting-range-slider"
              />
              <div className="slider-ticks-row">
                <span>5 fps (Battery saver)</span>
                <span>15 fps (Default)</span>
                <span>30 fps</span>
                <span>60 fps (Fast WiFi)</span>
              </div>
            </div>

            {/* JPEG Compression Quality */}
            <div className="setting-control-group">
              <div className="control-label-row">
                <span className="control-label">JPEG Compression Quality</span>
                <span className="control-value-badge">{currentQuality}%</span>
              </div>
              <input
                type="range"
                min="30"
                max="95"
                step="5"
                value={currentQuality}
                onChange={(e) => handleQualityChange(Number(e.target.value))}
                className="setting-range-slider"
              />
              <div className="slider-ticks-row">
                <span>30% (Low Bandwidth)</span>
                <span>70% (Standard)</span>
                <span>95% (Crystal Sharp)</span>
              </div>
            </div>

            {/* Resolution Selector */}
            <div className="setting-control-group">
              <span className="control-label">Stream Resolution</span>
              <div className="resolution-pills-row">
                <button
                  type="button"
                  className={`res-pill ${activeRes === '480p' ? 'active' : ''}`}
                  onClick={() => handleResolutionSelect('480p')}
                >
                  <span className="res-name">480p (4:3)</span>
                  <span className="res-specs">640×480 • Low Lag</span>
                </button>
                <button
                  type="button"
                  className={`res-pill ${activeRes === '720p' ? 'active' : ''}`}
                  onClick={() => handleResolutionSelect('720p')}
                >
                  <span className="res-name">720p (4:3)</span>
                  <span className="res-specs">960×720 • HD Full Sensor</span>
                </button>
                <button
                  type="button"
                  className={`res-pill ${activeRes === '1080p' ? 'active' : ''}`}
                  onClick={() => handleResolutionSelect('1080p')}
                >
                  <span className="res-name">1080p (4:3)</span>
                  <span className="res-specs">1440×1080 • FHD Full Sensor</span>
                </button>
              </div>
            </div>

            {/* Mirror & Color Channels */}
            <div className="settings-toggles-grid">
              <div className="toggle-card">
                <div className="toggle-meta">
                  <span className="toggle-title">Selfie Mirror Flip</span>
                  <span className="toggle-desc">Horizontal reflection for natural viewing</span>
                </div>
                <input
                  type="checkbox"
                  checked={cameraConfig.flip_horizontal ?? false}
                  onChange={(e) => onToggleFlip(e.target.checked)}
                  className="toggle-checkbox"
                />
              </div>

              <div className="toggle-card">
                <div className="toggle-meta">
                  <span className="toggle-title">Swap Red / Blue Channels</span>
                  <span className="toggle-desc">Correct color for custom sensor modules</span>
                </div>
                <input
                  type="checkbox"
                  checked={cameraConfig.swap_rb ?? false}
                  onChange={(e) => onToggleSwapRb(e.target.checked)}
                  className="toggle-checkbox"
                />
              </div>
            </div>
          </section>

          {/* Section 2: Raspberry Pi Hardware Telemetry */}
          <section className="settings-section">
            <div className="section-title-with-action">
              <h3 className="section-title">
                <Activity size={15} />
                <span>RASPBERRY PI HARDWARE HEALTH</span>
              </h3>
              <button
                type="button"
                className="section-refresh-btn"
                onClick={onRequestStats}
                title="Refresh stats"
              >
                <RefreshCw size={13} />
                <span>Refresh</span>
              </button>
            </div>

            {systemStats ? (() => {
              const cpuPercent =
                typeof systemStats.cpu_percent === 'number'
                  ? Math.max(0, Math.min(100, systemStats.cpu_percent))
                  : null

              const ramPercent =
                typeof systemStats.memory?.percent === 'number'
                  ? Math.max(0, Math.min(100, systemStats.memory.percent))
                  : typeof systemStats.memory_percent === 'number'
                    ? Math.max(0, Math.min(100, systemStats.memory_percent))
                    : null

              const ramUsedMb =
                typeof systemStats.memory?.used_mb === 'number'
                  ? systemStats.memory.used_mb
                  : typeof systemStats.memory_used_mb === 'number'
                    ? systemStats.memory_used_mb
                    : null

              const ramTotalMb =
                typeof systemStats.memory?.total_mb === 'number'
                  ? systemStats.memory.total_mb
                  : typeof systemStats.memory_total_mb === 'number'
                    ? systemStats.memory_total_mb
                    : null

              const tempVal =
                typeof systemStats.cpu_temp_c === 'number'
                  ? systemStats.cpu_temp_c
                  : typeof systemStats.temperature_c === 'number'
                    ? systemStats.temperature_c
                    : null

              return (
                <div className="telemetry-cards-grid">
                  {/* CPU Utilization */}
                  <div className="telemetry-card">
                    <div className="telemetry-card-header">
                      <Cpu size={16} className="telemetry-icon" />
                      <span className="telemetry-label">CPU LOAD</span>
                    </div>
                    <div className="telemetry-metric">
                      <span className="metric-number">
                        {cpuPercent !== null ? `${cpuPercent.toFixed(1)}%` : 'N/A'}
                      </span>
                      {systemStats.cpu_count && (
                        <span className="metric-sub">{systemStats.cpu_count} Cores</span>
                      )}
                    </div>
                    <div className="progress-bar-bg">
                      <div
                        className="progress-bar-fill"
                        style={{
                          width: `${cpuPercent ?? 0}%`,
                          backgroundColor:
                            (cpuPercent ?? 0) > 80
                              ? '#ff3366'
                              : (cpuPercent ?? 0) > 50
                                ? '#ffb703'
                                : '#00e599',
                        }}
                      />
                    </div>
                  </div>

                  {/* Memory RAM */}
                  <div className="telemetry-card">
                    <div className="telemetry-card-header">
                      <HardDrive size={16} className="telemetry-icon" />
                      <span className="telemetry-label">RAM USAGE</span>
                    </div>
                    <div className="telemetry-metric">
                      <span className="metric-number">
                        {ramPercent !== null ? `${ramPercent.toFixed(1)}%` : 'N/A'}
                      </span>
                      <span className="metric-sub">
                        {ramUsedMb !== null && ramTotalMb !== null
                          ? `${Math.round(ramUsedMb)} / ${Math.round(ramTotalMb)} MB`
                          : 'N/A'}
                      </span>
                    </div>
                    <div className="progress-bar-bg">
                      <div
                        className="progress-bar-fill"
                        style={{
                          width: `${ramPercent ?? 0}%`,
                          backgroundColor: (ramPercent ?? 0) > 85 ? '#ff3366' : '#00f0ff',
                        }}
                      />
                    </div>
                  </div>

                  {/* SoC Temperature */}
                  <div className="telemetry-card">
                    <div className="telemetry-card-header">
                      <Flame size={16} className="telemetry-icon" />
                      <span className="telemetry-label">SOC TEMP</span>
                    </div>
                    <div className="telemetry-metric">
                      <span className="metric-number">
                        {tempVal !== null ? `${tempVal.toFixed(1)}°C` : 'N/A'}
                      </span>
                      <span className="metric-sub">
                        {tempVal !== null && tempVal > 75
                          ? 'High Temp (Throttling)'
                          : tempVal !== null
                            ? 'Normal Thermal Range'
                            : 'Sensor Unavailable'}
                      </span>
                    </div>
                  </div>
                </div>
              )
            })() : (
              <div className="telemetry-loading-placeholder">
                <span>Reading Pi telemetry sensors...</span>
              </div>
            )}
          </section>

          {/* Section 3: Diagnostic Tools */}
          <section className="settings-section">
            <h3 className="section-title">DIAGNOSTICS & SYSTEM TOOLS</h3>
            <div className="diagnostics-card">
              <div className="diag-meta">
                <span className="diag-title">Phase 1 & 2 Diagnostic Monitor</span>
                <span className="diag-desc">
                  Open the low-level developer monitor with manual FPS, pipeline charts, and test patterns.
                </span>
              </div>
              <a
                href="/test.html"
                target="_blank"
                rel="noopener noreferrer"
                className="diag-link-btn"
              >
                <span>Open test.html</span>
                <ExternalLink size={14} />
              </a>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
