import QRCode from 'qrcode'

export const STRIP_WIDTH_MM = 74.25
export const STRIP_HEIGHT_MM = 210.0
export const PRINT_DPI = 300

// Exact pixel dimensions at 300 DPI (74.25 mm x 210 mm)
export const STRIP_WIDTH_PX = 877
export const STRIP_HEIGHT_PX = 2480

// Cell and padding metrics
export const PHOTO_WIDTH_PX = 749
export const PHOTO_HEIGHT_PX = 562
export const MARGIN_X_PX = 64
export const MARGIN_TOP_PX = 44
export const GAP_Y_PX = 48

/**
 * Creates a 1x4 vertical photo strip strictly of 210 mm x 74.25 mm (877x2480 px at 300 DPI).
 * The 4 captured photos are arranged vertically in a single column with clean white padding
 * between images and around the borders.
 */
export async function createCollageGridBlob(
  photoBlobs: (Blob | null)[],
  _sessionTimestamp: string = '',
  _title = 'TRC PHOTO BOOTH',
): Promise<Blob> {
  // Load the 4 photos
  const loadImgPromises = photoBlobs.map((blob) => {
    if (!blob) return Promise.resolve(null)
    return new Promise<HTMLImageElement | null>((resolve) => {
      const img = new Image()
      const url = URL.createObjectURL(blob)
      img.onload = () => {
        URL.revokeObjectURL(url)
        resolve(img)
      }
      img.onerror = () => {
        URL.revokeObjectURL(url)
        resolve(null)
      }
      img.src = url
    })
  })

  const loadedImages = await Promise.all(loadImgPromises)

  const canvas = document.createElement('canvas')
  canvas.width = STRIP_WIDTH_PX
  canvas.height = STRIP_HEIGHT_PX
  const ctx = canvas.getContext('2d')

  if (!ctx) {
    throw new Error('Failed to acquire 2D canvas context for collage')
  }

  // Clean neutral white canvas for strip & padding
  ctx.fillStyle = '#FFFFFF'
  ctx.fillRect(0, 0, STRIP_WIDTH_PX, STRIP_HEIGHT_PX)

  // Configure high quality image rendering
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'

  for (let i = 0; i < 4; i++) {
    const x = MARGIN_X_PX
    const y = MARGIN_TOP_PX + i * (PHOTO_HEIGHT_PX + GAP_Y_PX)
    const img = loadedImages[i]

    if (img) {
      // Center-crop image into destination cell
      const srcW = img.naturalWidth || img.width
      const srcH = img.naturalHeight || img.height
      const targetRatio = PHOTO_WIDTH_PX / PHOTO_HEIGHT_PX
      const srcRatio = srcW / srcH

      let sx = 0
      let sy = 0
      let sw = srcW
      let sh = srcH

      if (srcRatio > targetRatio) {
        sw = srcH * targetRatio
        sx = (srcW - sw) / 2
      } else {
        sh = srcW / targetRatio
        sy = (srcH - sh) / 2
      }

      ctx.drawImage(img, sx, sy, sw, sh, x, y, PHOTO_WIDTH_PX, PHOTO_HEIGHT_PX)
    } else {
      ctx.fillStyle = '#FFFFFF'
      ctx.fillRect(x, y, PHOTO_WIDTH_PX, PHOTO_HEIGHT_PX)
    }
  }

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new Error('Canvas toBlob returned null'))
      },
      'image/jpeg',
      0.95,
    )
  })
}

/**
 * Semantic alias for 1x4 vertical photo strip.
 */
export const createPhotoStripBlob = createCollageGridBlob

/**
 * Generates a high-contrast QR Code as a Data URL for the given URL string.
 */
export async function generateQrCodeDataUrl(url: string): Promise<string> {
  return QRCode.toDataURL(url, {
    width: 512,
    margin: 1,
    color: {
      dark: '#000000',
      light: '#ffffff',
    },
    errorCorrectionLevel: 'M',
  })
}
