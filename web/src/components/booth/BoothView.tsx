import React, { useCallback, useEffect, useRef, useState } from 'react'
import confetti from 'canvas-confetti'
import {
  Camera,
  CheckCircle,
  Download,
  FolderDown,
  RefreshCw,
  RotateCcw,
  Timer,
} from 'lucide-react'
import { BoothQuadrantGrid } from './BoothQuadrantGrid'
import { BoothFilterBar } from './BoothFilterBar'
import { BoothCountdownOverlay } from './BoothCountdownOverlay'
import { FILTER_PRESETS, getRandomConcreteFilter, bakeFilterFromImageBitmap } from '../../filters/presets'
import {
  downloadAllSessionPhotos,
  downloadPhoto,
  getCloudinaryConfig,
  saveSessionToDirectory,
  uploadPhotoToCloudinary,
  type SessionPhoto,
} from '../../utils/cloudinary'
import { createCollageGridBlob, generateQrCodeDataUrl } from '../../utils/collage'
import { playCountdownTick, playShutterSound, playSmileChime } from '../../utils/audio'
import type { BoothPhase, FilterId } from '../../types'

interface BoothViewProps {
  lastFrameBitmap: ImageBitmap | null
  isConnected: boolean
  isFlipped: boolean
  onBackToAdmin?: () => void
  onShowToast: (msg: string) => void
}

export const BoothView: React.FC<BoothViewProps> = ({
  lastFrameBitmap,
  isConnected,
  isFlipped,
  onShowToast,
}) => {
  const [phase, setPhase] = useState<BoothPhase>('idle')
  const [activeQuadrant, setActiveQuadrant] = useState<number>(0)
  const [capturedBlobs, setCapturedBlobs] = useState<(Blob | null)[]>([null, null, null, null])
  const [capturedUrls, setCapturedUrls] = useState<(string | null)[]>([null, null, null, null])
  const [selectedFilter, setSelectedFilter] = useState<FilterId>('none')
  const [currentFeedFilter, setCurrentFeedFilter] = useState<FilterId>('none')
  const [resolvedFilters, setResolvedFilters] = useState<FilterId[]>(['none', 'none', 'none', 'none'])
  const [timerSeconds, setTimerSeconds] = useState<number>(3)
  const [currentCountdown, setCurrentCountdown] = useState<number | null>(null)
  const [isFlashing, setIsFlashing] = useState<boolean>(false)
  const [sessionTimestamp, setSessionTimestamp] = useState<string | null>(null)

  // 2x2 Collage & QR Code state
  const [collageBlob, setCollageBlob] = useState<Blob | null>(null)
  const [collageUrl, setCollageUrl] = useState<string | null>(null)
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null)
  const [collageCloudinaryUrl, setCollageCloudinaryUrl] = useState<string | null>(null)
  const [collageUploadError, setCollageUploadError] = useState<string | null>(null)

  // Cloudinary Upload state
  const [isUploading, setIsUploading] = useState<boolean>(false)
  const [uploadProgress, setUploadProgress] = useState<string | null>(null)

  const lastFrameBitmapRef = useRef<ImageBitmap | null>(lastFrameBitmap)
  useEffect(() => {
    lastFrameBitmapRef.current = lastFrameBitmap
  }, [lastFrameBitmap])

  // Prevent accidental navigation away during the unstoppable sequence
  useEffect(() => {
    if (phase !== 'capturing') return

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [phase])

  // Update live preview filter when user changes filter in idle state
  const handleSelectFilter = (id: FilterId) => {
    if (phase !== 'idle') return
    setSelectedFilter(id)
    if (id !== 'random') {
      setCurrentFeedFilter(id)
    } else {
      // In idle, preview with 'none' for random mode
      setCurrentFeedFilter('none')
    }
  }

  // Trigger strobe flash
  const triggerFlash = useCallback(() => {
    setIsFlashing(true)
    setTimeout(() => setIsFlashing(false), 380)
  }, [])

  // Start the automated unstoppable 4-photo capture sequence
  const startSession = async () => {
    if (phase !== 'idle') return
    if (!isConnected || !lastFrameBitmapRef.current) {
      onShowToast('Camera feed is not ready. Please wait a moment.')
      return
    }

    // Clean up previous URLs
    capturedUrls.forEach((url) => {
      if (url) URL.revokeObjectURL(url)
    })

    setPhase('capturing')
    setCapturedBlobs([null, null, null, null])
    setCapturedUrls([null, null, null, null])
    setCollageCloudinaryUrl(null)
    setQrCodeDataUrl(null)
    setCollageBlob(null)
    setCollageUrl(null)
    setCollageUploadError(null)

    const currentBlobs: (Blob | null)[] = [null, null, null, null]
    const currentUrls: (string | null)[] = [null, null, null, null]
    const currentResolved: FilterId[] = ['none', 'none', 'none', 'none']

    for (let quadrant = 0; quadrant < 4; quadrant++) {
      setActiveQuadrant(quadrant)

      // 1. Resolve concrete filter for this quadrant
      const concreteFilter = selectedFilter === 'random' ? getRandomConcreteFilter() : selectedFilter
      setCurrentFeedFilter(concreteFilter)
      currentResolved[quadrant] = concreteFilter
      setResolvedFilters([...currentResolved])

      // 2. Countdown loop
      for (let sec = timerSeconds; sec >= 1; sec--) {
        setCurrentCountdown(sec)
        playCountdownTick()
        await new Promise((r) => setTimeout(r, 1000))
      }
      setCurrentCountdown(0)
      playSmileChime()

      // 3. Shutter snap & strobe flash
      triggerFlash()
      playShutterSound()

      // 4. Capture & bake frame
      const frame = lastFrameBitmapRef.current
      if (frame) {
        try {
          const bakedBlob = await bakeFilterFromImageBitmap(frame, concreteFilter)
          const blobUrl = URL.createObjectURL(bakedBlob)
          currentBlobs[quadrant] = bakedBlob
          currentUrls[quadrant] = blobUrl

          setCapturedBlobs([...currentBlobs])
          setCapturedUrls([...currentUrls])
        } catch (err) {
          console.error('Failed to bake filter onto capture:', err)
        }
      }

      setCurrentCountdown(null)

      // Pause to show captured snapshot before moving to next quadrant
      await new Promise((r) => setTimeout(r, 700))
    }

    // 5. Completion & 2x2 Collage Generation
    const now = new Date()
    const pad = (n: number) => n.toString().padStart(2, '0')
    const timestamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(
      now.getHours(),
    )}${pad(now.getMinutes())}${pad(now.getSeconds())}`

    setSessionTimestamp(timestamp)
    setPhase('complete')

    // Confetti celebration
    try {
      confetti({
        particleCount: 100,
        spread: 90,
        origin: { y: 0.6 },
        colors: ['#ff3366', '#00f0ff', '#ffb703', '#a855f7', '#00e599'],
      })
    } catch {
      // Ignore
    }

    onShowToast('🎉 4-Shot Photo Booth Session Complete!')

    // 6. Build 2x2 Collage & Upload to Cloudinary
    try {
      const collage = await createCollageGridBlob(currentBlobs, timestamp)
      setCollageBlob(collage)
      const cUrl = URL.createObjectURL(collage)
      setCollageUrl(cUrl)

      const config = getCloudinaryConfig()
      if (config.isConfigured) {
        setIsUploading(true)
        setUploadProgress('Uploading 2×2 collage to Cloudinary...')
        try {
          const res = await uploadPhotoToCloudinary(
            collage,
            `collage_${timestamp}.jpg`,
            `trc-photobooth/sessions/${timestamp}`,
          )
          setCollageCloudinaryUrl(res.secure_url)
          const qr = await generateQrCodeDataUrl(res.secure_url)
          setQrCodeDataUrl(qr)
          onShowToast('☁️ Collage uploaded! Scan QR Code 📱')
        } catch (err) {
          console.error('Failed to upload collage to Cloudinary:', err)
          setCollageUploadError((err as Error)?.message || 'Upload failed')
          onShowToast('Cloudinary upload failed: ' + ((err as Error)?.message || ''))
        } finally {
          setIsUploading(false)
          setUploadProgress(null)
        }
      }
    } catch (err) {
      console.error('Collage creation error:', err)
    }
  }

  // Reset back to idle for a new session
  const resetSession = () => {
    if (phase === 'capturing') return
    capturedUrls.forEach((url) => {
      if (url) URL.revokeObjectURL(url)
    })
    if (collageUrl) URL.revokeObjectURL(collageUrl)
    setCapturedBlobs([null, null, null, null])
    setCapturedUrls([null, null, null, null])
    setCollageBlob(null)
    setCollageUrl(null)
    setQrCodeDataUrl(null)
    setCollageCloudinaryUrl(null)
    setCollageUploadError(null)
    setIsUploading(false)
    setUploadProgress(null)
    setActiveQuadrant(0)
    setSessionTimestamp(null)
    setPhase('idle')
    if (selectedFilter !== 'random') {
      setCurrentFeedFilter(selectedFilter)
    } else {
      setCurrentFeedFilter('none')
    }
  }

  const handleRetryCollageUpload = async () => {
    if (!collageBlob || !sessionTimestamp) return
    const config = getCloudinaryConfig()
    if (!config.isConfigured) {
      onShowToast('Cloudinary not configured (set VITE_CLOUDINARY_* in .env)')
      return
    }

    setIsUploading(true)
    setCollageUploadError(null)
    setUploadProgress('Uploading 2×2 collage to Cloudinary...')
    try {
      const res = await uploadPhotoToCloudinary(
        collageBlob,
        `collage_${sessionTimestamp}.jpg`,
        `trc-photobooth/sessions/${sessionTimestamp}`,
      )
      setCollageCloudinaryUrl(res.secure_url)
      const qr = await generateQrCodeDataUrl(res.secure_url)
      setQrCodeDataUrl(qr)
      onShowToast('☁️ Collage uploaded! Scan QR Code 📱')
    } catch (err) {
      console.error('Failed to upload collage:', err)
      setCollageUploadError((err as Error)?.message || 'Upload failed')
      onShowToast('Cloudinary upload failed')
    } finally {
      setIsUploading(false)
      setUploadProgress(null)
    }
  }

  // Build SessionPhoto list for saving/uploading
  const getSessionPhotos = (): SessionPhoto[] => {
    const photos: SessionPhoto[] = []
    capturedBlobs.forEach((blob, index) => {
      if (blob) {
        const filterId = resolvedFilters[index] || 'raw'
        photos.push({
          blob,
          fileName: `photo_${index + 1}_${filterId}.jpg`,
          filterId,
          quadrantIndex: index,
        })
      }
    })
    return photos
  }

  // Handle Download All Photos
  const handleDownloadAll = async () => {
    const photos = getSessionPhotos()
    if (photos.length === 0) return
    onShowToast(`Downloading ${photos.length} photos...`)
    await downloadAllSessionPhotos(photos)
  }

  // Handle Download Collage
  const handleDownloadCollage = () => {
    if (!collageBlob || !sessionTimestamp) return
    downloadPhoto(collageBlob, `collage_${sessionTimestamp}.jpg`)
    onShowToast('Downloading 2×2 collage...')
  }

  // Handle Save to Folder (File System Access API or download)
  const handleSaveToDirectory = async () => {
    const photos = getSessionPhotos()
    if (collageBlob && sessionTimestamp) {
      photos.push({
        blob: collageBlob,
        fileName: `collage_${sessionTimestamp}.jpg`,
        filterId: 'collage',
        quadrantIndex: 4,
      })
    }
    if (photos.length === 0) return
    const ts = sessionTimestamp || 'session'
    const result = await saveSessionToDirectory(ts, photos)
    if (result.success) {
      onShowToast(`Saved to ${result.pathDescription}`)
    }
  }

  return (
    <div className="booth-view-container">
      {/* Top Header Bar: Only shown when NOT capturing */}
      {phase !== 'capturing' && (
        <header className="booth-header">
          <div className="booth-header-left">
            <div className="booth-badge-icon" title="TRC Photo Booth">
              <Camera size={18} />
            </div>

            <div className="booth-title-group">
              <span className="booth-main-title">TRC PHOTO BOOTH</span>
              <span className="booth-sub-badge">
                {phase === 'complete' ? 'SESSION COMPLETE' : '4-SHOT PHOTO EXPERIENCE'}
              </span>
            </div>
          </div>

          <div className="booth-header-right">
            <div className={`status-pill ${isConnected ? 'status-connected' : 'status-disconnected'}`}>
              <span className="status-dot" />
              <span className="status-label">{isConnected ? 'LIVE FEED READY' : 'FEED OFFLINE'}</span>
            </div>
          </div>
        </header>
      )}

      {/* Main Quadrant Viewport */}
      <main className="booth-viewport">
        <BoothQuadrantGrid
          lastFrameBitmap={lastFrameBitmap}
          activeQuadrant={activeQuadrant}
          capturedUrls={capturedUrls}
          resolvedFilters={resolvedFilters}
          activeFeedFilter={currentFeedFilter}
          isFlipped={isFlipped}
          isConnected={isConnected}
        />

        {/* Countdown & Flash Overlays during capture */}
        <BoothCountdownOverlay
          photoIndex={activeQuadrant}
          secondsLeft={currentCountdown}
          isFlashing={isFlashing}
          activeFilterName={FILTER_PRESETS[currentFeedFilter]?.name}
        />
      </main>

      {/* Bottom Controls Area */}
      {phase === 'idle' && (
        <footer className="booth-footer-dock">
          {/* Filter Selection Carousel */}
          <BoothFilterBar
            selectedFilter={selectedFilter}
            onSelectFilter={handleSelectFilter}
            disabled={false}
          />

          {/* Timer and Big Start Button Row */}
          <div className="booth-controls-row">
            {/* Timer Selector */}
            <div className="booth-timer-pill-group" role="group" aria-label="Timer selection">
              <div className="timer-icon-label">
                <Timer size={14} className="text-muted" />
              </div>
              {[3, 5, 10].map((sec) => (
                <button
                  key={sec}
                  type="button"
                  className={`booth-timer-pill ${timerSeconds === sec ? 'active' : ''}`}
                  onClick={() => setTimerSeconds(sec)}
                >
                  {sec}s
                </button>
              ))}
            </div>

            {/* Big Start Button */}
            <button
              type="button"
              className="booth-start-btn"
              disabled={!isConnected}
              onClick={startSession}
            >
              <Camera size={18} />
              <span>START PHOTO BOOTH</span>
            </button>
          </div>
        </footer>
      )}

      {phase === 'complete' && (
        <footer className="booth-completion-dock">
          <div className="booth-completion-card">
            <div className="completion-card-header">
              <div className="flex items-center gap-2">
                <CheckCircle size={20} className="text-green" />
                <h3 className="completion-title">4-Shot Collage Ready! 🎉</h3>
              </div>
              <span className="completion-path">
                Session: {sessionTimestamp || 'N/A'}
              </span>
            </div>

            {/* Uploading Progress */}
            {isUploading && (
              <div className="upload-progress-banner">
                <RefreshCw size={15} className="animate-spin text-cyan" />
                <span>{uploadProgress || 'Uploading to Cloudinary...'}</span>
              </div>
            )}

            {/* Cloudinary QR Code & Collage Presentation */}
            {qrCodeDataUrl ? (
              <div className="booth-qr-card">
                <div className="qr-image-wrapper">
                  <img src={qrCodeDataUrl} alt="Photo Booth QR Code" className="qr-image" />
                </div>
                <div className="qr-info-group">
                  <div className="qr-badge">
                    <CheckCircle size={16} className="text-green" />
                    <span>SCAN TO VIEW & DOWNLOAD</span>
                  </div>
                  <p className="qr-instructions">
                    Scan with your smartphone camera to access and download your 2×2 composite collage.
                  </p>
                  {collageCloudinaryUrl && (
                    <a
                      href={collageCloudinaryUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="qr-link"
                    >
                      {collageCloudinaryUrl}
                    </a>
                  )}
                </div>
                {collageUrl && (
                  <div className="collage-preview-wrapper" title="2×2 Composite Collage Preview">
                    <img src={collageUrl} alt="2×2 Collage" className="collage-thumb" />
                  </div>
                )}
              </div>
            ) : collageUploadError ? (
              <div className="upload-error-banner">
                <div className="error-text-group">
                  <span className="text-green font-bold">Saved locally! 🎉</span>
                  <span className="text-xs text-muted">{collageUploadError}</span>
                </div>
                <button
                  type="button"
                  className="btn-retry"
                  onClick={handleRetryCollageUpload}
                >
                  <RefreshCw size={13} />
                  <span>Retry Upload</span>
                </button>
              </div>
            ) : null}

            <div className="completion-actions-row">
              {/* Download Collage */}
              {collageBlob && (
                <button
                  type="button"
                  className="btn-booth-secondary"
                  onClick={handleDownloadCollage}
                  title="Download 2x2 collage"
                >
                  <Download size={15} />
                  <span>Download Collage</span>
                </button>
              )}

              {/* Download All Photos */}
              <button
                type="button"
                className="btn-booth-secondary"
                onClick={handleDownloadAll}
                title="Download all 4 photos"
              >
                <Download size={15} />
                <span>Download All</span>
              </button>

              <button
                type="button"
                className="btn-booth-secondary"
                onClick={handleSaveToDirectory}
                title="Save session to a chosen directory on your computer"
              >
                <FolderDown size={15} />
                <span>Save to Folder</span>
              </button>

              {/* Big Start New Session Button */}
              <button
                type="button"
                className="btn-booth-primary"
                onClick={resetSession}
              >
                <RotateCcw size={16} />
                <span>START NEW SESSION</span>
              </button>
            </div>
          </div>
        </footer>
      )}

      {phase === 'capturing' && (
        <div className="booth-capturing-banner">
          <span className="capturing-pulse-text">
            CAPTURING SEQUENCE IN PROGRESS • DO NOT EXIT
          </span>
        </div>
      )}
    </div>
  )
}
