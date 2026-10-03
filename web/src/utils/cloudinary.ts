/**
 * Cloudinary & Session Storage Utility for TRC Photo Booth Web.
 *
 * Supported Environment Variables (defined in .env or Vite build):
 * - VITE_CLOUDINARY_CLOUD_NAME
 * - VITE_CLOUDINARY_API_KEY
 * - VITE_CLOUDINARY_API_SECRET
 * - VITE_CLOUDINARY_UPLOAD_PRESET
 */

export interface CloudinaryConfig {
  cloudName: string
  apiKey: string
  apiSecret: string
  uploadPreset: string
  isConfigured: boolean
}

export function getCloudinaryConfig(): CloudinaryConfig {
  const cloudName = (import.meta.env.VITE_CLOUDINARY_CLOUD_NAME as string | undefined) || ''
  const apiKey = (import.meta.env.VITE_CLOUDINARY_API_KEY as string | undefined) || ''
  const apiSecret = (import.meta.env.VITE_CLOUDINARY_API_SECRET as string | undefined) || ''
  const uploadPreset = (import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET as string | undefined) || ''

  const isConfigured = Boolean(cloudName && (uploadPreset || (apiKey && apiSecret)))

  return {
    cloudName,
    apiKey,
    apiSecret,
    uploadPreset,
    isConfigured,
  }
}

export interface SessionPhoto {
  blob: Blob
  fileName: string
  filterId: string
  quadrantIndex: number
}

export interface CloudinaryUploadResult {
  secure_url: string
  public_id: string
  bytes: number
  format: string
  created_at: string
}

/**
 * Upload a photo blob directly to Cloudinary using unsigned upload preset or client credentials.
 */
export async function uploadPhotoToCloudinary(
  blob: Blob,
  fileName: string,
  folder = 'trc-photobooth/sessions',
): Promise<CloudinaryUploadResult> {
  const config = getCloudinaryConfig()
  if (!config.cloudName) {
    throw new Error('Cloudinary Cloud Name is not configured. Set VITE_CLOUDINARY_CLOUD_NAME.')
  }

  const formData = new FormData()
  formData.append('file', blob, fileName)
  if (config.uploadPreset) {
    formData.append('upload_preset', config.uploadPreset)
  }
  if (config.apiKey) {
    formData.append('api_key', config.apiKey)
  }
  formData.append('folder', folder)
  formData.append('public_id', fileName.replace(/\.[^/.]+$/, ''))

  const endpoint = `https://api.cloudinary.com/v1_1/${config.cloudName}/image/upload`

  const response = await fetch(endpoint, {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(
      errorData?.error?.message || `Cloudinary upload failed with status ${response.status}`,
    )
  }

  return (await response.json()) as CloudinaryUploadResult
}

/**
 * Downloads a single photo blob with specified filename.
 */
export function downloadPhoto(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

/**
 * Downloads all photos in a session sequentially.
 */
export async function downloadAllSessionPhotos(photos: SessionPhoto[]): Promise<void> {
  for (let i = 0; i < photos.length; i++) {
    const photo = photos[i]
    downloadPhoto(photo.blob, photo.fileName)
    // Small stagger to ensure browser handles all downloads cleanly
    await new Promise((r) => setTimeout(r, 250))
  }
}

/**
 * Save session photos to a local directory using the modern File System Access API
 * (supported in Chromium browsers: Chrome, Edge, Opera).
 * Falls back to standard multi-file download if unsupported or cancelled.
 */
export async function saveSessionToDirectory(
  sessionTimestamp: string,
  photos: SessionPhoto[],
): Promise<{ success: boolean; pathDescription: string }> {
  const hasDirectoryPicker = typeof window !== 'undefined' && 'showDirectoryPicker' in window

  if (hasDirectoryPicker) {
    try {
      // Prompt user to pick a destination directory
      const rootHandle = await (window as unknown as {
        showDirectoryPicker: (opts?: { mode: 'readwrite' }) => Promise<FileSystemDirectoryHandle>
      }).showDirectoryPicker({ mode: 'readwrite' })

      // Create session subfolder
      const sessionHandle = await rootHandle.getDirectoryHandle(sessionTimestamp, { create: true })

      for (const photo of photos) {
        const fileHandle = await sessionHandle.getFileHandle(photo.fileName, { create: true })
        const writable = await (fileHandle as unknown as {
          createWritable: () => Promise<FileSystemWritableFileStream>
        }).createWritable()
        await writable.write(photo.blob)
        await writable.close()
      }

      return {
        success: true,
        pathDescription: `${rootHandle.name}/${sessionTimestamp}`,
      }
    } catch (err) {
      if ((err as Error)?.name === 'AbortError') {
        return { success: false, pathDescription: 'Cancelled' }
      }
      // If error, fall through to browser downloads
    }
  }

  // Fallback to sequential browser downloads
  await downloadAllSessionPhotos(photos)
  return {
    success: true,
    pathDescription: `Downloads/${sessionTimestamp}`,
  }
}
