import type { FilterId, FilterPreset } from '../types'

export const FILTER_PRESETS: Record<FilterId, FilterPreset> = {
  none: {
    id: 'none',
    name: 'Normal',
    tagline: 'Raw & Natural',
    cssFilter: 'none',
    canvasFilter: 'none',
    accentColor: '#94a3b8',
    description: 'Direct camera feed with natural colors and exposure',
    badgeText: 'RAW',
  },
  bw: {
    id: 'bw',
    name: 'Monochrome',
    tagline: 'Timeless B&W',
    cssFilter: 'grayscale(100%) contrast(120%) brightness(102%)',
    canvasFilter: 'grayscale(100%) contrast(120%) brightness(102%)',
    accentColor: '#e2e8f0',
    description: 'High-definition deep black & white with punchy contrast',
    badgeText: 'B&W',
  },
  sepia: {
    id: 'sepia',
    name: 'Sepia',
    tagline: 'Early 1900s',
    cssFilter: 'sepia(85%) contrast(105%) brightness(95%)',
    canvasFilter: 'sepia(85%) contrast(105%) brightness(95%)',
    accentColor: '#d97706',
    description: 'Warm antique sepia tone reminiscent of early photography',
    badgeText: 'SEPIA',
  },
  vintage: {
    id: 'vintage',
    name: 'Vintage',
    tagline: '70s Film Roll',
    cssFilter: 'sepia(40%) contrast(118%) saturate(135%) brightness(105%) hue-rotate(-10deg)',
    canvasFilter: 'sepia(40%) contrast(118%) saturate(135%) brightness(105%) hue-rotate(-10deg)',
    accentColor: '#f59e0b',
    description: 'Warm, sun-faded color saturation of classic 35mm film',
    badgeText: '70s FILM',
  },
  cool: {
    id: 'cool',
    name: 'Cool Tone',
    tagline: 'Arctic Cyan',
    cssFilter: 'saturate(115%) hue-rotate(185deg) brightness(105%) contrast(110%)',
    canvasFilter: 'saturate(115%) hue-rotate(185deg) brightness(105%) contrast(110%)',
    accentColor: '#06b6d4',
    description: 'Crisp cool blue & cyan palette with vivid clarity',
    badgeText: 'COOL',
  },
  warm: {
    id: 'warm',
    name: 'Golden Hour',
    tagline: 'Sunset Glow',
    cssFilter: 'sepia(30%) saturate(145%) brightness(105%) hue-rotate(-15deg)',
    canvasFilter: 'sepia(30%) saturate(145%) brightness(105%) hue-rotate(-15deg)',
    accentColor: '#f97316',
    description: 'Warm, golden illumination flattering for portraits',
    badgeText: 'GOLDEN',
  },
  high_contrast: {
    id: 'high_contrast',
    name: 'Dramatic',
    tagline: 'Noir Contrast',
    cssFilter: 'contrast(165%) brightness(95%) saturate(125%)',
    canvasFilter: 'contrast(165%) brightness(95%) saturate(125%)',
    accentColor: '#ec4899',
    description: 'Deep shadows, punchy highlights, and cinematic presence',
    badgeText: 'NOIR',
  },
  vignette: {
    id: 'vignette',
    name: 'Vignette',
    tagline: 'Edge Shadowing',
    cssFilter: 'contrast(115%) brightness(102%)',
    canvasFilter: 'contrast(115%) brightness(102%)',
    accentColor: '#8b5cf6',
    description: 'Soft edge darkening that draws attention straight to the center',
    badgeText: 'VIGNETTE',
  },
  film_grain: {
    id: 'film_grain',
    name: 'Film Grain',
    tagline: 'Textured ISO',
    cssFilter: 'contrast(115%) brightness(105%)',
    canvasFilter: 'contrast(115%) brightness(105%)',
    accentColor: '#10b981',
    description: 'Analog film noise texture for an authentic retro feel',
    badgeText: 'GRAIN',
  },
  polaroid: {
    id: 'polaroid',
    name: 'Polaroid',
    tagline: 'Instant Print',
    cssFilter: 'contrast(108%) brightness(108%) saturate(88%) sepia(22%)',
    canvasFilter: 'contrast(108%) brightness(108%) saturate(88%) sepia(22%)',
    accentColor: '#38bdf8',
    description: 'Classic instant camera color grading with optional vintage border',
    badgeText: 'POLAROID',
  },
  random: {
    id: 'random',
    name: 'Random',
    tagline: 'Surprise Each Shot',
    cssFilter: 'none',
    canvasFilter: 'none',
    accentColor: '#a855f7',
    description: 'Picks a random filter for each photo in the sequence',
    badgeText: '🎲 RANDOM',
  },
}

export const CONCRETE_FILTER_IDS: FilterId[] = [
  'none',
  'bw',
  'sepia',
  'vintage',
  'cool',
  'warm',
  'high_contrast',
  'vignette',
  'film_grain',
  'polaroid',
]

export const FILTER_LIST: FilterPreset[] = Object.values(FILTER_PRESETS)

export function getRandomConcreteFilter(): FilterId {
  const index = Math.floor(Math.random() * CONCRETE_FILTER_IDS.length)
  return CONCRETE_FILTER_IDS[index]
}

/**
 * Procedurally render a vignette gradient on the given canvas context.
 */
export function drawVignetteOverlay(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  intensity = 0.68,
): void {
  const cx = width / 2
  const cy = height / 2
  const radius = Math.sqrt(cx * cx + cy * cy)
  const gradient = ctx.createRadialGradient(cx, cy, radius * 0.45, cx, cy, radius)
  gradient.addColorStop(0, 'rgba(0, 0, 0, 0)')
  gradient.addColorStop(0.7, `rgba(0, 0, 0, ${intensity * 0.4})`)
  gradient.addColorStop(1, `rgba(0, 0, 0, ${intensity})`)

  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)
}

/**
 * Procedurally render subtle film grain on the given canvas context.
 */
export function drawFilmGrainOverlay(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  grainOpacity = 0.08,
): void {
  // Use a smaller pattern canvas to avoid heavy CPU processing on large images
  const patternCanvas = document.createElement('canvas')
  const patternSize = 128
  patternCanvas.width = patternSize
  patternCanvas.height = patternSize
  const pCtx = patternCanvas.getContext('2d')
  if (!pCtx) return

  const imgData = pCtx.createImageData(patternSize, patternSize)
  const data = imgData.data
  for (let i = 0; i < data.length; i += 4) {
    const noise = (Math.random() * 255) | 0
    data[i] = noise
    data[i + 1] = noise
    data[i + 2] = noise
    data[i + 3] = (noise > 128 ? 255 : 0) * grainOpacity
  }
  pCtx.putImageData(imgData, 0, 0)

  const pattern = ctx.createPattern(patternCanvas, 'repeat')
  if (pattern) {
    ctx.save()
    ctx.globalCompositeOperation = 'overlay'
    ctx.fillStyle = pattern
    ctx.fillRect(0, 0, width, height)
    ctx.restore()
  }
}

/**
 * Bake a selected filter preset and aesthetic effects onto an offscreen canvas.
 * Returns a high-quality JPEG Blob for downloading or sharing.
 */
export async function bakeFilterToImageBlob(
  sourceUrlOrBlob: string | Blob,
  filterId: FilterId,
  includePolaroidFrame = false,
): Promise<Blob> {
  const preset = FILTER_PRESETS[filterId] || FILTER_PRESETS.none

  // Load source image
  const img = new Image()
  img.crossOrigin = 'anonymous'

  const url =
    typeof sourceUrlOrBlob === 'string'
      ? sourceUrlOrBlob
      : URL.createObjectURL(sourceUrlOrBlob)

  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = (e) => reject(e)
    img.src = url
  })

  const srcWidth = img.naturalWidth || img.width
  const srcHeight = img.naturalHeight || img.height

  let targetWidth = srcWidth
  let targetHeight = srcHeight
  let imgX = 0
  let imgY = 0

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) throw new Error('Could not obtain canvas 2D context')

  if (includePolaroidFrame || filterId === 'polaroid') {
    // Polaroid frame proportions: 4% border left/top/right, 16% bottom border
    const borderHorizontal = Math.round(srcWidth * 0.05)
    const borderTop = Math.round(srcHeight * 0.05)
    const borderBottom = Math.round(srcHeight * 0.18)

    targetWidth = srcWidth + borderHorizontal * 2
    targetHeight = srcHeight + borderTop + borderBottom
    imgX = borderHorizontal
    imgY = borderTop

    canvas.width = targetWidth
    canvas.height = targetHeight

    // Fill Polaroid paper background (warm vintage white)
    ctx.fillStyle = '#f8f6f0'
    ctx.fillRect(0, 0, targetWidth, targetHeight)

    // Inner photo shadow
    ctx.save()
    ctx.shadowColor = 'rgba(0, 0, 0, 0.15)'
    ctx.shadowBlur = 10
    ctx.shadowOffsetY = 2
    ctx.fillStyle = '#000'
    ctx.fillRect(imgX, imgY, srcWidth, srcHeight)
    ctx.restore()
  } else {
    canvas.width = targetWidth
    canvas.height = targetHeight
  }

  // Draw filtered image
  ctx.save()
  if (preset.canvasFilter && preset.canvasFilter !== 'none') {
    ctx.filter = preset.canvasFilter
  }
  ctx.drawImage(img, imgX, imgY, srcWidth, srcHeight)
  ctx.restore()

  // Apply special overlays inside photo area
  ctx.save()
  ctx.beginPath()
  ctx.rect(imgX, imgY, srcWidth, srcHeight)
  ctx.clip()

  if (filterId === 'vignette') {
    ctx.translate(imgX, imgY)
    drawVignetteOverlay(ctx, srcWidth, srcHeight, 0.65)
  } else if (filterId === 'film_grain') {
    ctx.translate(imgX, imgY)
    drawFilmGrainOverlay(ctx, srcWidth, srcHeight, 0.1)
  }
  ctx.restore()

  // Add polaroid footer text if polaroid frame is active
  if (includePolaroidFrame || filterId === 'polaroid') {
    ctx.save()
    ctx.fillStyle = '#475569'
    ctx.font = `600 ${Math.max(16, Math.round(targetWidth * 0.028))}px 'Plus Jakarta Sans', sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const dateText = new Date().toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
    ctx.fillText(
      `TRC Photo Booth • ${dateText}`,
      targetWidth / 2,
      targetHeight - (targetHeight - (imgY + srcHeight)) / 2,
    )
    ctx.restore()
  }

  // Clean up object URL if created
  if (typeof sourceUrlOrBlob !== 'string') {
    URL.revokeObjectURL(url)
  }

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new Error('Canvas blob export failed'))
      },
      'image/jpeg',
      0.95,
    )
  })
}

/**
 * Bake a selected filter preset onto an ImageBitmap.
 * Used by the 4-quadrant photo booth sequence for instant capture baking.
 */
export async function bakeFilterFromImageBitmap(
  bitmap: ImageBitmap,
  filterId: FilterId,
  includePolaroidFrame = false,
): Promise<Blob> {
  const concreteId = filterId === 'random' ? getRandomConcreteFilter() : filterId
  const preset = FILTER_PRESETS[concreteId] || FILTER_PRESETS.none

  const srcWidth = bitmap.width
  const srcHeight = bitmap.height

  let targetWidth = srcWidth
  let targetHeight = srcHeight
  let imgX = 0
  let imgY = 0

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) throw new Error('Could not obtain canvas 2D context')

  if (includePolaroidFrame || concreteId === 'polaroid') {
    const borderHorizontal = Math.round(srcWidth * 0.05)
    const borderTop = Math.round(srcHeight * 0.05)
    const borderBottom = Math.round(srcHeight * 0.18)

    targetWidth = srcWidth + borderHorizontal * 2
    targetHeight = srcHeight + borderTop + borderBottom
    imgX = borderHorizontal
    imgY = borderTop

    canvas.width = targetWidth
    canvas.height = targetHeight

    ctx.fillStyle = '#f8f6f0'
    ctx.fillRect(0, 0, targetWidth, targetHeight)

    ctx.save()
    ctx.shadowColor = 'rgba(0, 0, 0, 0.15)'
    ctx.shadowBlur = 10
    ctx.shadowOffsetY = 2
    ctx.fillStyle = '#000'
    ctx.fillRect(imgX, imgY, srcWidth, srcHeight)
    ctx.restore()
  } else {
    canvas.width = targetWidth
    canvas.height = targetHeight
  }

  // Draw filtered image
  ctx.save()
  if (preset.canvasFilter && preset.canvasFilter !== 'none') {
    ctx.filter = preset.canvasFilter
  }
  ctx.drawImage(bitmap, imgX, imgY, srcWidth, srcHeight)
  ctx.restore()

  // Apply special overlays inside photo area
  ctx.save()
  ctx.beginPath()
  ctx.rect(imgX, imgY, srcWidth, srcHeight)
  ctx.clip()

  if (concreteId === 'vignette') {
    ctx.translate(imgX, imgY)
    drawVignetteOverlay(ctx, srcWidth, srcHeight, 0.65)
  } else if (concreteId === 'film_grain') {
    ctx.translate(imgX, imgY)
    drawFilmGrainOverlay(ctx, srcWidth, srcHeight, 0.1)
  }
  ctx.restore()

  if (includePolaroidFrame || concreteId === 'polaroid') {
    ctx.save()
    ctx.fillStyle = '#475569'
    ctx.font = `600 ${Math.max(16, Math.round(targetWidth * 0.028))}px 'Plus Jakarta Sans', sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const dateText = new Date().toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
    ctx.fillText(
      `TRC Photo Booth • ${dateText}`,
      targetWidth / 2,
      targetHeight - (targetHeight - (imgY + srcHeight)) / 2,
    )
    ctx.restore()
  }

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new Error('Canvas blob export failed'))
      },
      'image/jpeg',
      0.95,
    )
  })
}
