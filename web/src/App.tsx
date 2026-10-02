import { useCallback, useState } from 'react'
import confetti from 'canvas-confetti'
import { Header } from './components/Header'
import { LivePreview } from './components/LivePreview'
import { FilterBar } from './components/FilterBar'
import { CaptureControls } from './components/CaptureControls'
import { CountdownOverlay } from './components/CountdownOverlay'
import { GalleryDrawer } from './components/GalleryDrawer'
import { LightboxModal } from './components/LightboxModal'
import { SettingsModal } from './components/SettingsModal'
import { ErrorBoundary } from './components/ErrorBoundary'
import { useWebSocket } from './hooks/useWebSocket'
import { useCaptures } from './hooks/useCaptures'
import type { CaptureMetadata, CaptureType, FilterId } from './types'

export function App() {
  const [activeFilter, setActiveFilter] = useState<FilterId>('none')
  const [showGuides, setShowGuides] = useState<boolean>(false)
  const [captureMode, setCaptureMode] = useState<CaptureType>('photo')
  const [countdownSetting, setCountdownSetting] = useState<number>(3)
  const [gifFrames, setGifFrames] = useState<number>(10)
  const [gifIntervalMs, setGifIntervalMs] = useState<number>(150)
  const [isFlashing, setIsFlashing] = useState<boolean>(false)

  // Drawer / Modals state
  const [isGalleryOpen, setIsGalleryOpen] = useState<boolean>(false)
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false)
  const [selectedLightboxCapture, setSelectedLightboxCapture] = useState<CaptureMetadata | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg)
    setTimeout(() => {
      setToastMessage((current) => (current === msg ? null : current))
    }, 3200)
  }, [])

  const triggerFlash = useCallback(() => {
    setIsFlashing(true)
    setTimeout(() => setIsFlashing(false), 380)
  }, [])

  const {
    captures,
    isLoading: isLoadingCaptures,
    addCapture,
    deleteCapture,
    refreshCaptures,
  } = useCaptures()

  const handleCaptureResult = useCallback(
    (cap: CaptureMetadata) => {
      addCapture(cap)
      triggerFlash()

      // Celebrate with confetti
      try {
        confetti({
          particleCount: 75,
          spread: 80,
          origin: { y: 0.6 },
          colors: ['#ff3366', '#00f0ff', '#ffb703', '#ffffff', '#a855f7'],
        })
      } catch {
        // Ignore
      }

      showToast(
        cap.type === 'gif'
          ? '🎉 Animated GIF recorded successfully!'
          : '📸 High-res photo captured!',
      )
    },
    [addCapture, triggerFlash, showToast],
  )

  const {
    status,
    fps,
    latency,
    lastFrameBitmap,
    countdown,
    gifRecording,
    cameraConfig,
    systemStats,
    triggerPhoto,
    triggerGif,
    setFpsSetting,
    setQualitySetting,
    setResolutionSetting,
    toggleFlip,
    toggleSwapRb,
    requestSystemStats,
  } = useWebSocket({
    onCaptureResult: handleCaptureResult,
    onFlashTrigger: triggerFlash,
  })

  const handleTriggerCapture = () => {
    if (captureMode === 'photo') {
      triggerPhoto(countdownSetting)
    } else {
      triggerGif(countdownSetting, gifFrames, gifIntervalMs)
    }
  }

  const isFlipped = cameraConfig.flip_horizontal ?? false

  const handleToggleFlip = () => {
    toggleFlip(!isFlipped)
  }

  const handleDownloadCapture = (capture: CaptureMetadata, e: React.MouseEvent) => {
    e.stopPropagation()
    const link = document.createElement('a')
    link.href = capture.url
    link.download = capture.filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const handleDeleteCapture = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (confirm('Delete this capture?')) {
      const ok = await deleteCapture(id)
      if (ok) {
        showToast('Capture deleted')
        if (selectedLightboxCapture?.id === id) {
          setSelectedLightboxCapture(null)
        }
      }
    }
  }

  return (
    <div className="photobooth-app">
      {/* Top Navigation */}
      <Header
        connectionStatus={status}
        fps={fps}
        latency={latency}
        capturesCount={captures.length}
        showGuides={showGuides}
        isFlipped={isFlipped}
        onToggleGuides={() => setShowGuides((prev) => !prev)}
        onToggleFlip={handleToggleFlip}
        onOpenGallery={() => setIsGalleryOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />

      {/* Main Studio Workspace */}
      <main className="studio-workspace">
        <LivePreview
          lastFrameBitmap={lastFrameBitmap}
          activeFilter={activeFilter}
          isFlipped={isFlipped}
          showGuides={showGuides}
          fps={fps}
          resolution={
            cameraConfig.width && cameraConfig.height
              ? `${cameraConfig.width}×${cameraConfig.height}`
              : ''
          }
          isConnected={status === 'connected'}
          gifRecording={gifRecording}
        />
      </main>

      {/* Filter Selector Strip */}
      <FilterBar
        activeFilter={activeFilter}
        onSelectFilter={(id) => setActiveFilter(id)}
      />

      {/* Capture Controls Footer */}
      <CaptureControls
        mode={captureMode}
        countdownSetting={countdownSetting}
        isCountingDown={countdown !== null}
        isRecordingGif={gifRecording !== null}
        countdownSeconds={countdown?.secondsLeft ?? null}
        disabled={status !== 'connected'}
        latestCapture={captures[0] || null}
        gifFrames={gifFrames}
        gifIntervalMs={gifIntervalMs}
        onModeChange={(m) => setCaptureMode(m)}
        onCountdownSettingChange={(sec) => setCountdownSetting(sec)}
        onGifFramesChange={(f) => setGifFrames(f)}
        onGifIntervalChange={(ms) => setGifIntervalMs(ms)}
        onTrigger={handleTriggerCapture}
        onOpenGallery={() => setIsGalleryOpen(true)}
      />

      {/* Fullscreen Countdown & Flash Overlay */}
      <CountdownOverlay countdown={countdown} isFlashing={isFlashing} />

      {/* Recent Captures Slide-out Drawer */}
      <GalleryDrawer
        isOpen={isGalleryOpen}
        captures={captures}
        isLoading={isLoadingCaptures}
        onClose={() => setIsGalleryOpen(false)}
        onRefresh={refreshCaptures}
        onSelectCapture={(cap) => setSelectedLightboxCapture(cap)}
        onDeleteCapture={handleDeleteCapture}
        onDownloadCapture={handleDownloadCapture}
      />

      {/* Expanded Lightbox Modal */}
      <LightboxModal
        capture={selectedLightboxCapture}
        capturesList={captures}
        defaultFilter={activeFilter}
        onClose={() => setSelectedLightboxCapture(null)}
        onDelete={deleteCapture}
        onNavigate={(cap) => setSelectedLightboxCapture(cap)}
      />

      {/* Hardware Telemetry & Settings Modal */}
      <ErrorBoundary fallbackTitle="Settings Error">
        <SettingsModal
          isOpen={isSettingsOpen}
          cameraConfig={cameraConfig}
          systemStats={systemStats}
          onClose={() => setIsSettingsOpen(false)}
          onSetFps={setFpsSetting}
          onSetQuality={setQualitySetting}
          onSetResolution={setResolutionSetting}
          onToggleFlip={toggleFlip}
          onToggleSwapRb={toggleSwapRb}
          onRequestStats={requestSystemStats}
        />
      </ErrorBoundary>

      {/* Toast Notification */}
      {toastMessage && (
        <div className="photobooth-toast" role="status">
          <span className="toast-text">{toastMessage}</span>
        </div>
      )}
    </div>
  )
}

export default App
