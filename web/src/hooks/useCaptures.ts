import { useCallback, useEffect, useState } from 'react'
import type { CaptureMetadata } from '../types'

export function useCaptures() {
  const [captures, setCaptures] = useState<CaptureMetadata[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchCaptures = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/captures')
      if (!res.ok) throw new Error(`HTTP error ${res.status}`)
      const data: CaptureMetadata[] = await res.json()
      setCaptures(data)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch captures'
      setError(msg)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchCaptures()
  }, [fetchCaptures])

  const addCapture = useCallback((newCap: CaptureMetadata) => {
    setCaptures((prev) => {
      // Avoid duplicate IDs
      const filtered = prev.filter((c) => c.id !== newCap.id)
      return [newCap, ...filtered]
    })
  }, [])

  const deleteCapture = useCallback(async (captureId: string) => {
    try {
      const res = await fetch(`/api/captures/${captureId}`, {
        method: 'DELETE',
      })
      if (!res.ok) throw new Error(`Failed to delete capture: ${res.statusText}`)
      setCaptures((prev) => prev.filter((c) => c.id !== captureId))
      return true
    } catch (err) {
      console.error('Error deleting capture:', err)
      return false
    }
  }, [])

  return {
    captures,
    isLoading,
    error,
    refreshCaptures: fetchCaptures,
    addCapture,
    deleteCapture,
  }
}
