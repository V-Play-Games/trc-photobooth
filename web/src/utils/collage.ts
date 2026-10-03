import QRCode from 'qrcode'

/**
 * Creates a high-resolution 2x2 grid collage of 4 photo blobs on an HTML5 canvas.
 * Dimensions: 1200 x 1400 px with branded header, 2x2 photo cells with borders, and footer.
 */
export async function createCollageGridBlob(
  photoBlobs: (Blob | null)[],
  sessionTimestamp: string,
  title = 'TRC PHOTO BOOTH',
): Promise<Blob> {
  const width = 1200
  const height = 1400
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')

  if (!ctx) {
    throw new Error('Failed to acquire 2D canvas context for collage')
  }

  // Dark cyberpunk background
  ctx.fillStyle = '#0B0F17'
  ctx.fillRect(0, 0, width, height)

  // Card outline border
  ctx.strokeStyle = '#1E293B'
  ctx.lineWidth = 4
  ctx.strokeRect(12, 12, width - 24, height - 24)

  // Accent top stripe
  ctx.fillStyle = '#FF3366'
  ctx.fillRect(24, 24, width - 48, 6)

  // Header Title
  ctx.textAlign = 'center'
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = '#FFFFFF'
  ctx.font = 'bold 44px monospace'
  ctx.fillText(title, width / 2, 95)

  // Subtitle
  ctx.fillStyle = '#00F0FF'
  ctx.font = 'bold 20px monospace'
  ctx.fillText('4-SHOT COMPOSITE MEMORY', width / 2, 130)

  // 2x2 Grid calculations
  const gridMarginLeft = 40
  const gridMarginTop = 160
  const gridGap = 20
  const availableWidth = width - gridMarginLeft * 2 - gridGap
  const cellWidth = availableWidth / 2
  const cellHeight = cellWidth * (3 / 4) // 4:3 photo ratio, ~412.5px

  const coords = [
    { x: gridMarginLeft, y: gridMarginTop },
    { x: gridMarginLeft + cellWidth + gridGap, y: gridMarginTop },
    { x: gridMarginLeft, y: gridMarginTop + cellHeight + gridGap },
    { x: gridMarginLeft + cellWidth + gridGap, y: gridMarginTop + cellHeight + gridGap },
  ]

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
      // Empty placeholder
      ctx.fillStyle = '#151C2C'
      ctx.fillRect(x, y, cellWidth, cellHeight)
    }

    // Cell border
    ctx.strokeStyle = '#334155'
    ctx.lineWidth = 3
    ctx.strokeRect(x, y, cellWidth, cellHeight)
  }

  // Footer Section
  const gridBottom = gridMarginTop + cellHeight * 2 + gridGap
  ctx.fillStyle = '#00E599'
  ctx.fillRect(24, height - 30, width - 48, 6)

  ctx.fillStyle = '#94A3B8'
  ctx.font = 'bold 22px monospace'
  ctx.fillText(
    `CAPTURED WITH TRC PHOTO BOOTH • ${sessionTimestamp}`,
    width / 2,
    gridBottom + 65,
  )

  ctx.fillStyle = '#64748B'
  ctx.font = '18px monospace'
  ctx.fillText('SCAN QR CODE TO VIEW DIGITAL ORIGINAL', width / 2, gridBottom + 105)

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new Error('Canvas toBlob returned null'))
      },
      'image/jpeg',
      0.92,
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
