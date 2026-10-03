/**
 * Audio synthesis engine for photo booth countdown and shutter sound effects.
 * Built with Web Audio API for zero-latency, zero-dependency, self-contained playback.
 */

let audioCtx: AudioContext | null = null

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    if (AudioContextClass) {
      audioCtx = new AudioContextClass()
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume().catch(() => {})
  }
  return audioCtx
}

const SOUND_STORAGE_KEY = 'trc_photobooth_sound_enabled'

export function isSoundEnabled(): boolean {
  if (typeof window === 'undefined') return true
  const stored = localStorage.getItem(SOUND_STORAGE_KEY)
  return stored !== 'false'
}

export function setSoundEnabled(enabled: boolean): void {
  if (typeof window === 'undefined') return
  localStorage.setItem(SOUND_STORAGE_KEY, enabled ? 'true' : 'false')
}

/**
 * Play countdown tick beep (880 Hz, A5).
 */
export function playCountdownTick(): void {
  if (!isSoundEnabled()) return
  const ctx = getAudioContext()
  if (!ctx) return

  try {
    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sine'
    osc.frequency.setValueAtTime(880, now)

    gain.gain.setValueAtTime(0.001, now)
    gain.gain.exponentialRampToValueAtTime(0.25, now + 0.01)
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.08)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.085)
  } catch {
    // Ignore audio errors
  }
}

/**
 * Play high chime chord on "SMILE!" (C6 + E6 harmonic).
 */
export function playSmileChime(): void {
  if (!isSoundEnabled()) return
  const ctx = getAudioContext()
  if (!ctx) return

  try {
    const now = ctx.currentTime
    const freqs = [1046.5, 1318.51, 1567.98] // C6, E6, G6 major triad

    freqs.forEach((freq, idx) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()

      osc.type = 'triangle'
      osc.frequency.setValueAtTime(freq, now + idx * 0.02)

      const start = now + idx * 0.02
      gain.gain.setValueAtTime(0.001, start)
      gain.gain.exponentialRampToValueAtTime(0.18, start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.35)

      osc.connect(gain)
      gain.connect(ctx.destination)

      osc.start(start)
      osc.stop(start + 0.36)
    })
  } catch {
    // Ignore audio errors
  }
}

/**
 * Play realistic mechanical camera shutter snap sound.
 * Combines high-pass filtered noise burst with two distinct mechanical curtain clicks.
 */
export function playShutterSound(): void {
  if (!isSoundEnabled()) return
  const ctx = getAudioContext()
  if (!ctx) return

  try {
    const now = ctx.currentTime

    // 1. First curtain click (mirror pop)
    const osc1 = ctx.createOscillator()
    const gain1 = ctx.createGain()
    osc1.type = 'triangle'
    osc1.frequency.setValueAtTime(420, now)
    osc1.frequency.exponentialRampToValueAtTime(60, now + 0.04)

    gain1.gain.setValueAtTime(0.4, now)
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.04)
    osc1.connect(gain1)
    gain1.connect(ctx.destination)
    osc1.start(now)
    osc1.stop(now + 0.045)

    // 2. White noise shutter curtain flutter
    const bufferSize = Math.floor(ctx.sampleRate * 0.06)
    const noiseBuffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
    const output = noiseBuffer.getChannelData(0)
    for (let i = 0; i < bufferSize; i++) {
      output[i] = Math.random() * 2 - 1
    }

    const whiteNoise = ctx.createBufferSource()
    whiteNoise.buffer = noiseBuffer

    const filter = ctx.createBiquadFilter()
    filter.type = 'bandpass'
    filter.frequency.setValueAtTime(2400, now)
    filter.Q.setValueAtTime(3.0, now)

    const noiseGain = ctx.createGain()
    noiseGain.gain.setValueAtTime(0.25, now + 0.01)
    noiseGain.gain.exponentialRampToValueAtTime(0.001, now + 0.06)

    whiteNoise.connect(filter)
    filter.connect(noiseGain)
    noiseGain.connect(ctx.destination)
    whiteNoise.start(now + 0.01)
    whiteNoise.stop(now + 0.065)

    // 3. Second curtain close snap
    const osc2 = ctx.createOscillator()
    const gain2 = ctx.createGain()
    osc2.type = 'square'
    osc2.frequency.setValueAtTime(320, now + 0.06)
    osc2.frequency.exponentialRampToValueAtTime(40, now + 0.1)

    gain2.gain.setValueAtTime(0.35, now + 0.06)
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.1)
    osc2.connect(gain2)
    gain2.connect(ctx.destination)
    osc2.start(now + 0.06)
    osc2.stop(now + 0.105)
  } catch {
    // Ignore audio errors
  }
}
