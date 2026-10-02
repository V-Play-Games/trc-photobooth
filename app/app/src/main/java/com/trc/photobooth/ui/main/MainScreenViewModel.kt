package com.trc.photobooth.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trc.photobooth.data.PhotoBoothRepository
import com.trc.photobooth.data.models.CameraConfig
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.data.models.CaptureType
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.data.models.CountdownState
import com.trc.photobooth.data.models.GifRecordingState
import com.trc.photobooth.data.models.SystemStats
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.util.NetworkDiscovery
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PhotoBoothRepository(application, viewModelScope)
    val networkDiscovery = NetworkDiscovery(application)

    // UI Configuration & Tool States
    private val _activeFilter = MutableStateFlow(FilterPresets.NONE)
    val activeFilter: StateFlow<FilterPreset> = _activeFilter.asStateFlow()

    private val _showGuides = MutableStateFlow(false)
    val showGuides: StateFlow<Boolean> = _showGuides.asStateFlow()

    private val _captureMode = MutableStateFlow(CaptureType.PHOTO)
    val captureMode: StateFlow<CaptureType> = _captureMode.asStateFlow()

    private val _countdownSetting = MutableStateFlow(3)
    val countdownSetting: StateFlow<Int> = _countdownSetting.asStateFlow()

    private val _gifFrames = MutableStateFlow(10)
    val gifFrames: StateFlow<Int> = _gifFrames.asStateFlow()

    private val _gifIntervalMs = MutableStateFlow(150)
    val gifIntervalMs: StateFlow<Int> = _gifIntervalMs.asStateFlow()

    // Dialog & Navigation states
    private val _isGalleryOpen = MutableStateFlow(false)
    val isGalleryOpen: StateFlow<Boolean> = _isGalleryOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _lightboxCapture = MutableStateFlow<CaptureMetadata?>(null)
    val lightboxCapture: StateFlow<CaptureMetadata?> = _lightboxCapture.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Pass-through Repository flows
    val status: StateFlow<ConnectionStatus> = repository.status
    val lastFrame = repository.lastFrame
    val fps: StateFlow<Int> = repository.fps
    val latencyMs: StateFlow<Int> = repository.latencyMs
    val cameraConfig: StateFlow<CameraConfig> = repository.cameraConfig
    val systemStats: StateFlow<SystemStats?> = repository.systemStats
    val countdown: StateFlow<CountdownState?> = repository.countdown
    val gifRecording: StateFlow<GifRecordingState?> = repository.gifRecording
    val flashEvent: SharedFlow<Unit> = repository.flashEvent
    val captures: StateFlow<List<CaptureMetadata>> = repository.captures
    val isLoadingCaptures: StateFlow<Boolean> = repository.isLoadingCaptures
    val hostAddress: StateFlow<String> = repository.hostAddress

    init {
        // Start connection with saved host
        repository.reconnect()

        // Handle incoming capture celebrations
        viewModelScope.launch {
            repository.captureResult.collect { capture ->
                val typeName = if (capture.type == "gif") "Animated GIF" else "High-res photo"
                _toastMessage.tryEmit("📸 $typeName captured!")
            }
        }

        // Listen for NSD discovered Pi
        viewModelScope.launch {
            networkDiscovery.discoveredHost.collect { resolved ->
                if (resolved != null && resolved != repository.hostAddress.value) {
                    _toastMessage.tryEmit("✨ Found Photo Booth Pi at $resolved")
                    repository.setHost(resolved)
                }
            }
        }
    }

    fun selectFilter(preset: FilterPreset) {
        _activeFilter.value = preset
    }

    fun toggleGuides() {
        _showGuides.value = !_showGuides.value
    }

    fun setCaptureMode(mode: CaptureType) {
        _captureMode.value = mode
    }

    fun setCountdownSetting(sec: Int) {
        _countdownSetting.value = sec
    }

    fun setGifFrames(f: Int) {
        _gifFrames.value = f
    }

    fun setGifInterval(ms: Int) {
        _gifIntervalMs.value = ms
    }

    fun openGallery() {
        repository.fetchCaptures()
        _isGalleryOpen.value = true
    }

    fun closeGallery() {
        _isGalleryOpen.value = false
    }

    fun openSettings() {
        repository.requestSystemStats()
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openLightbox(capture: CaptureMetadata) {
        _lightboxCapture.value = capture
    }

    fun closeLightbox() {
        _lightboxCapture.value = null
    }

    fun triggerShutter() {
        if (status.value != ConnectionStatus.CONNECTED) return
        val sec = _countdownSetting.value
        if (_captureMode.value == CaptureType.PHOTO) {
            repository.triggerPhoto(sec)
        } else {
            repository.triggerGif(sec, _gifFrames.value, _gifIntervalMs.value)
        }
    }

    fun toggleFlip() {
        val current = cameraConfig.value.flipHorizontal ?: false
        repository.toggleFlip(!current)
    }

    fun toggleSwapRb() {
        val current = cameraConfig.value.swapRb ?: false
        repository.toggleSwapRb(!current)
    }

    fun setFps(fps: Int) = repository.setFps(fps)
    fun setQuality(q: Int) = repository.setQuality(q)
    fun setResolution(res: String) = repository.setResolution(res)
    fun setHost(host: String) = repository.setHost(host)
    fun refreshStats() = repository.requestSystemStats()
    fun startNsdSearch() = networkDiscovery.startDiscovery()
    fun deleteCapture(id: String) {
        viewModelScope.launch {
            if (repository.deleteCapture(id)) {
                _toastMessage.tryEmit("Capture deleted")
                if (_lightboxCapture.value?.id == id) {
                    _lightboxCapture.value = null
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
        networkDiscovery.stopDiscovery()
    }
}
