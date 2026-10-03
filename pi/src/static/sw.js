// Service worker for TRC Photo Booth PWA installability
const CACHE_NAME = 'trc-photobooth-v1'

self.addEventListener('install', (event) => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('fetch', (event) => {
  // Let WebSocket and live API requests pass through cleanly
  if (event.request.url.includes('/ws/') || event.request.url.includes('/api/')) {
    return
  }
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  )
})
