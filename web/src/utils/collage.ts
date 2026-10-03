import QRCode from 'qrcode'

/**
 * Creates a 2x2 grid collage strictly of the 4 captured photos.
 * No black background, borders, headers, or footers - strictly the 4 photos tiled together.
 */
export async function createCollageGridBlob(
  photoBlobs: (Blob | null)[],
  _sessionTimestamp: string = '',
  _title = 'TRC PHOTO BOOTH',
): Promise<Blob> {
  // Load and draw the 4 photos
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

  const firstImg = loadedImages.find((img) => img !== null)
  const cellWidth = firstImg ? (firstImg.naturalWidth || firstImg.width) : 640
  const cellHeight = firstImg ? (firstImg.naturalHeight || firstImg.height) : 480

  const width = cellWidth * 2
  const height = cellHeight * 2
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')

  if (!ctx) {
    throw new Error('Failed to acquire 2D canvas context for collage')
  }

  // Clean neutral white canvas - never black
  ctx.fillStyle = '#FFFFFF'
  ctx.fillRect(0, 0, width, height)

  const coords = [
    { x: 0, y: 0 },
    { x: cellWidth, y: 0 },
    { x: 0, y: cellHeight },
    { x: cellWidth, y: cellHeight },
  ]

  for (let i = 0; i < 4; i++) {
    const { x, y } = coords[i]
    const img = loadedImages[i]

    if (img) {
      // Center-crop image into destination cell
      const srcW = img.naturalWidth || img.width
      const srcH = img.naturalHeight || img.height
      const targetRatio = cellWidth / cellHeight
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

      ctx.drawImage(img, sx, sy, sw, sh, x, y, cellWidth, cellHeight)
    } else {
      ctx.fillStyle = '#FFFFFF'
      ctx.fillRect(x, y, cellWidth, cellHeight)
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
