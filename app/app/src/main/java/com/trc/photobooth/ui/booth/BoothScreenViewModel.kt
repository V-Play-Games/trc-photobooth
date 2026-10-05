package com.trc.photobooth.ui.booth

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LifecycleOwner
import com.trc.photobooth.camera.AndroidLens
import com.trc.photobooth.camera.CameraSource
import com.trc.photobooth.data.CloudinaryConfig
import com.trc.photobooth.data.CloudinaryUploader
import com.trc.photobooth.data.PhotoBoothRepository
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.util.BitmapUtils
import com.trc.photobooth.util.HapticHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.trc.photobooth.data.models.CameraConfig
import com.trc.photobooth.data.models.PhotoBoothTemplate
import com.trc.photobooth.data.models.SystemStats
import com.trc.photobooth.util.NetworkDiscovery

enum class BoothState {
    IDLE,
    CAPTURING,
    COMPLETE,
}

sealed interface BoothUploadState {
    data object Idle : BoothUploadState
    data class Generating(val message: String = "Preparing photo strip...") : BoothUploadState
    data class Uploading(val message: String = "Uploading photo strips to Cloudinary...") : BoothUploadState
    data class Success(val url: String, val blankUrl: String = url) : BoothUploadState
    data class Error(val error: String) : BoothUploadState
}

sealed interface PrintState {
    data object Idle : PrintState
    data class Printing(val message: String = "Sending to TRC_Printer...") : PrintState
    data class Success(val message: String, val jobId: String? = null) : PrintState
    data class Error(val error: String) : PrintState
}

class BoothScreenViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PhotoBoothRepository.getInstance(application)
    val networkDiscovery = NetworkDiscovery(application)
    val hapticHelper = HapticHelper(application)

    init {
        repository.connectIfNeeded()

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

    fun reconnect() {
        repository.reconnect()
    }

    // Settings state
    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    fun openSettings() {
        if (_boothState.value == BoothState.CAPTURING) return
        repository.requestSystemStats()
        repository.requestDevices()
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    val cameraConfig: StateFlow<CameraConfig> = repository.cameraConfig
    val systemStats: StateFlow<SystemStats?> = repository.systemStats
    val hostAddress: StateFlow<String> = repository.hostAddress
    val printerName: StateFlow<String> = repository.printerName
    val printerColorMode: StateFlow<String> = repository.printerColorMode
    val printerCopies: StateFlow<Int> = repository.printerCopies
    val isStreamPaused: StateFlow<Boolean> = repository.isStreamPaused
    val cameraSource: StateFlow<CameraSource> = repository.cameraSource
    val androidLens: StateFlow<AndroidLens> = repository.androidLens
    val isTorchEnabled: StateFlow<Boolean> = repository.isTorchEnabled

    fun setCameraSource(source: CameraSource) = repository.setCameraSource(source)
    fun toggleCameraSource() = repository.toggleCameraSource()
    fun setAndroidLens(lens: AndroidLens) = repository.setAndroidLens(lens)
    fun toggleAndroidLens() = repository.toggleAndroidLens()
    fun toggleTorch() = repository.toggleTorch()
    fun focusCamera(xNorm: Float, yNorm: Float) = repository.focusLocalCamera(xNorm, yNorm)
    fun startLocalCamera(owner: LifecycleOwner) = repository.startLocalCamera(owner)
    fun stopLocalCamera() = repository.stopLocalCamera()

    fun setHost(host: String) = repository.setHost(host)
    fun startNsdSearch() = networkDiscovery.startDiscovery()
    fun toggleStreamPause() = repository.toggleStreamPause()
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
    fun refreshStats() = repository.requestSystemStats()
    fun setCameraDevice(device: String) = repository.setCameraDevice(device)
    fun refreshDevices() = repository.requestDevices()
    fun updatePrinterSettings(name: String, colorMode: String, copies: Int) {
        repository.setPrinterSettings(name, colorMode, copies)
    }

    override fun onCleared() {
        super.onCleared()
        networkDiscovery.stopDiscovery()
    }

    // State machine status
    private val _boothState = MutableStateFlow(BoothState.IDLE)
    val boothState: StateFlow<BoothState> = _boothState.asStateFlow()

    // 0 = Top-Left, 1 = Top-Right, 2 = Bottom-Left, 3 = Bottom-Right
    private val _activeQuadrant = MutableStateFlow(0)
    val activeQuadrant: StateFlow<Int> = _activeQuadrant.asStateFlow()

    // 4 Photo Slots: null if not yet captured
    private val _capturedPhotos = MutableStateFlow<List<Bitmap?>>(listOf(null, null, null, null))
    val capturedPhotos: StateFlow<List<Bitmap?>> = _capturedPhotos.asStateFlow()

    // Filter selected by the user (can be RANDOM)
    private val _selectedFilter = MutableStateFlow(FilterPresets.NONE)
    val selectedFilter: StateFlow<FilterPreset> = _selectedFilter.asStateFlow()

    // Active concrete filter applied to the live feed for the current quadrant
    private val _currentFeedFilter = MutableStateFlow(FilterPresets.NONE)
    val currentFeedFilter: StateFlow<FilterPreset> = _currentFeedFilter.asStateFlow()

    // Filter used for each of the 4 photos (recorded as they are taken)
    private val _resolvedFilters = MutableStateFlow<List<FilterPreset>>(
        listOf(FilterPresets.NONE, FilterPresets.NONE, FilterPresets.NONE, FilterPresets.NONE)
    )
    val resolvedFilters: StateFlow<List<FilterPreset>> = _resolvedFilters.asStateFlow()

    // Countdown setting: 3, 5, or 10 seconds
    private val _timerSeconds = MutableStateFlow(3)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    // Live countdown value during capture (null when not ticking)
    private val _currentCountdown = MutableStateFlow<Int?>(null)
    val currentCountdown: StateFlow<Int?> = _currentCountdown.asStateFlow()

    // Flash event trigger
    private val _flashEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashEvent: SharedFlow<Unit> = _flashEvent.asSharedFlow()

    // Toast message trigger
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Saved session files on disk
    private val _savedSessionFiles = MutableStateFlow<List<File>>(emptyList())
    val savedSessionFiles: StateFlow<List<File>> = _savedSessionFiles.asStateFlow()

    private val _sessionTimestamp = MutableStateFlow<String?>(null)
    val sessionTimestamp: StateFlow<String?> = _sessionTimestamp.asStateFlow()

    // Selected frame template
    private val _selectedTemplate = MutableStateFlow<PhotoBoothTemplate>(PhotoBoothTemplate.DEFAULT)
    val selectedTemplate: StateFlow<PhotoBoothTemplate> = _selectedTemplate.asStateFlow()

    // 1x4 Composite Collage (Themed)
    private val _collageBitmap = MutableStateFlow<Bitmap?>(null)
    val collageBitmap: StateFlow<Bitmap?> = _collageBitmap.asStateFlow()

    private val _collageFile = MutableStateFlow<File?>(null)
    val collageFile: StateFlow<File?> = _collageFile.asStateFlow()

    // 1x4 Composite Collage (Blank / Classic)
    private val _blankCollageBitmap = MutableStateFlow<Bitmap?>(null)
    val blankCollageBitmap: StateFlow<Bitmap?> = _blankCollageBitmap.asStateFlow()

    private val _blankCollageFile = MutableStateFlow<File?>(null)
    val blankCollageFile: StateFlow<File?> = _blankCollageFile.asStateFlow()

    // Cloudinary URLs and QR Codes for both
    private val _cloudinaryUrl = MutableStateFlow<String?>(null)
    val cloudinaryUrl: StateFlow<String?> = _cloudinaryUrl.asStateFlow()

    private val _blankCloudinaryUrl = MutableStateFlow<String?>(null)
    val blankCloudinaryUrl: StateFlow<String?> = _blankCloudinaryUrl.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    private val _blankQrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val blankQrCodeBitmap: StateFlow<Bitmap?> = _blankQrCodeBitmap.asStateFlow()

    private val _uploadState = MutableStateFlow<BoothUploadState>(BoothUploadState.Idle)
    val uploadState: StateFlow<BoothUploadState> = _uploadState.asStateFlow()

    private val _printState = MutableStateFlow<PrintState>(PrintState.Idle)
    val printState: StateFlow<PrintState> = _printState.asStateFlow()

    // Pass-through connection flows from repository
    val connectionStatus: StateFlow<ConnectionStatus> = repository.status
    val lastFrame: StateFlow<Bitmap?> = repository.lastFrame

    private var captureJob: Job? = null

    fun selectFilter(filter: FilterPreset) {
        if (_boothState.value != BoothState.IDLE) return
        _selectedFilter.value = filter
        repository.logAction("Booth filter selected: ${filter.name}", com.trc.photobooth.data.LogType.INFO)
        if (filter.id != FilterPresets.RANDOM.id) {
            _currentFeedFilter.value = filter
        } else {
            // For random mode, preview with NONE or a random preset in idle
            _currentFeedFilter.value = FilterPresets.NONE
        }
    }

    fun setTimerSeconds(seconds: Int) {
        if (_boothState.value != BoothState.IDLE) return
        _timerSeconds.value = seconds
        repository.logAction("Booth timer set to ${seconds}s", com.trc.photobooth.data.LogType.INFO)
    }

    /**
     * Begins the unstoppable 4-photo capture sequence.
     * Captures 4 photos sequentially:
     * Quadrant 0 (Top-Left) -> Quadrant 1 (Top-Right) -> Quadrant 2 (Bottom-Left) -> Quadrant 3 (Bottom-Right).
     * Once started, this sequence cannot be cancelled or stopped.
     */
    fun startSession() {
        if (_boothState.value != BoothState.IDLE) return
        if (captureJob?.isActive == true) return

        // Turn camera back on automatically once new session starts
        if (repository.isStreamPaused.value) {
            repository.setStreamPaused(false)
            repository.logAction("Camera turned back on automatically for new session", com.trc.photobooth.data.LogType.STATUS)
        }

        captureJob = viewModelScope.launch {
            _boothState.value = BoothState.CAPTURING
            _capturedPhotos.value = listOf(null, null, null, null)
            repository.logAction(
                "Booth 4-shot session started (Timer: ${_timerSeconds.value}s, Filter: ${_selectedFilter.value.name})",
                com.trc.photobooth.data.LogType.CAPTURE
            )

            val resolved = mutableListOf<FilterPreset>()
            val captures = mutableListOf<Bitmap>()

            for (quadrant in 0..3) {
                _activeQuadrant.value = quadrant

                // 1. Resolve concrete filter for this quadrant
                val concreteFilter = if (_selectedFilter.value.id == FilterPresets.RANDOM.id) {
                    FilterPresets.getRandomConcreteFilter()
                } else {
                    _selectedFilter.value
                }
                _currentFeedFilter.value = concreteFilter

                repository.logAction(
                    "Clicking photo ${quadrant + 1}/4 (Filter: ${concreteFilter.name})...",
                    com.trc.photobooth.data.LogType.CAPTURE
                )

                // Update resolved list
                val updatedResolved = _resolvedFilters.value.toMutableList()
                if (quadrant < updatedResolved.size) {
                    updatedResolved[quadrant] = concreteFilter
                }
                _resolvedFilters.value = updatedResolved
                resolved.add(concreteFilter)

                // 2. Countdown timer loop
                val totalSecs = _timerSeconds.value
                for (sec in totalSecs downTo 1) {
                    _currentCountdown.value = sec
                    hapticHelper.tick()
                    delay(1000)
                }
                _currentCountdown.value = 0

                // 3. Capture frame: grab lastFrame from repository with brief retry if null
                var frame = repository.lastFrame.value
                var retries = 0
                while (frame == null && retries < 10) {
                    delay(50)
                    frame = repository.lastFrame.value
                    retries++
                }

                // Flash and shutter haptic
                _flashEvent.emit(Unit)
                hapticHelper.shutterSnap()

                // 4. Bake filter
                val bakedBitmap = if (frame != null) {
                    BitmapUtils.bakeFilter(frame, concreteFilter)
                } else {
                    // Fallback black bitmap if feed was lost
                    Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).also {
                        BitmapUtils.bakeFilter(it, concreteFilter)
                    }
                }
                captures.add(bakedBitmap)

                // Update captured photos list
                val updatedCaptures = _capturedPhotos.value.toMutableList()
                updatedCaptures[quadrant] = bakedBitmap
                _capturedPhotos.value = updatedCaptures

                repository.logAction(
                    "Clicked photo ${quadrant + 1}/4 (${concreteFilter.name})",
                    com.trc.photobooth.data.LogType.CAPTURE
                )

                _currentCountdown.value = null

                // Pause so guest sees their captured quadrant before switching to next
                delay(700)
            }

            // 5. Sequence complete! Auto-save all 4 photos to directory
            hapticHelper.captureComplete()
            repository.logAction("All 4 photos clicked! Generating photo strip previews...", com.trc.photobooth.data.LogType.CAPTURE)
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            _sessionTimestamp.value = timestamp

            val files = BitmapUtils.saveSessionPhotos(
                context = getApplication(),
                sessionTimestamp = timestamp,
                photos = captures,
                filters = resolved,
            )
            _savedSessionFiles.value = files
            _boothState.value = BoothState.COMPLETE

            // Send camera off after every session
            repository.setStreamPaused(true)
            repository.logAction("Camera turned off after session completion", com.trc.photobooth.data.LogType.STATUS)

            // Reset timer back to 3s after every session
            _timerSeconds.value = 3

            // 6. Generate Preview Collages (Themed + Blank) — show preview first, do not upload yet!
            _uploadState.value = BoothUploadState.Idle
            val defaultTpl = _selectedTemplate.value
            val themedTplOverlay = BitmapUtils.loadTemplateBitmap(getApplication(), defaultTpl.drawableResId)
            val themedCollage = BitmapUtils.createThemedCollage(captures, themedTplOverlay)
            _collageBitmap.value = themedCollage
            val cFile = BitmapUtils.saveCollage(getApplication(), themedCollage, timestamp, "collage_${defaultTpl.id}_${timestamp}.jpg")
            _collageFile.value = cFile

            val blankTplOverlay = BitmapUtils.loadTemplateBitmap(getApplication(), PhotoBoothTemplate.BLANK.drawableResId)
            val blankCollage = BitmapUtils.createThemedCollage(captures, blankTplOverlay)
            _blankCollageBitmap.value = blankCollage
            val bFile = BitmapUtils.saveCollage(getApplication(), blankCollage, timestamp, "collage_blank_${timestamp}.jpg")
            _blankCollageFile.value = bFile

            repository.logAction("Photo strip preview ready! Select template to preview and upload.", com.trc.photobooth.data.LogType.COMPLETE)
        }
    }

    /**
     * Updates the selected template and immediately refreshes the collage preview.
     */
    fun selectTemplate(template: PhotoBoothTemplate) {
        if (_selectedTemplate.value == template) return
        _selectedTemplate.value = template
        hapticHelper.tick()

        val photos = _capturedPhotos.value.filterNotNull()
        val ts = _sessionTimestamp.value
        if (photos.size >= 4 && ts != null) {
            val tplOverlay = BitmapUtils.loadTemplateBitmap(getApplication(), template.drawableResId)
            val themedCollage = BitmapUtils.createThemedCollage(photos, tplOverlay)
            _collageBitmap.value = themedCollage
            val cFile = BitmapUtils.saveCollage(getApplication(), themedCollage, ts, "collage_${template.id}_${ts}.jpg")
            _collageFile.value = cFile
            repository.logAction("Template selected: ${template.title}", com.trc.photobooth.data.LogType.INFO)

            // If user previously uploaded and changes selection, reset upload state so they can re-upload
            if (_uploadState.value is BoothUploadState.Success) {
                _uploadState.value = BoothUploadState.Idle
                _cloudinaryUrl.value = null
                _blankCloudinaryUrl.value = null
                _qrCodeBitmap.value = null
                _blankQrCodeBitmap.value = null
            }
        }
    }

    /**
     * Uploads both the selected themed photo strip and the blank template strip to Cloudinary,
     * generating QR codes for both.
     */
    fun uploadCollages() {
        val themedCollage = _collageBitmap.value
        val blankCollage = _blankCollageBitmap.value
        val ts = _sessionTimestamp.value
        val template = _selectedTemplate.value

        if (themedCollage == null || blankCollage == null || ts == null) {
            viewModelScope.launch {
                _toastMessage.emit("Preview not ready yet")
            }
            return
        }
        if (_uploadState.value is BoothUploadState.Uploading) return

        viewModelScope.launch {
            val config = CloudinaryConfig.fromBuildConfig()
            if (!config.isConfigured) {
                _uploadState.value = BoothUploadState.Error(
                    "Cloudinary not configured. Set CLOUDINARY_CLOUD_NAME and CLOUDINARY_UPLOAD_PRESET."
                )
                return@launch
            }

            _uploadState.value = BoothUploadState.Uploading("Uploading photo strips to Cloudinary...")

            // 1. Upload selected themed collage
            val themedResult = CloudinaryUploader.uploadBitmap(
                bitmap = themedCollage,
                fileName = "collage_${template.id}_${ts}.jpg",
                folder = "trc-photobooth/sessions/$ts",
                config = config,
            )

            // 2. Upload blank template collage as well
            val blankResult = if (template == PhotoBoothTemplate.BLANK && themedResult.isSuccess) {
                themedResult
            } else {
                CloudinaryUploader.uploadBitmap(
                    bitmap = blankCollage,
                    fileName = "collage_blank_${ts}.jpg",
                    folder = "trc-photobooth/sessions/$ts",
                    config = config,
                )
            }

            if (themedResult.isSuccess && blankResult.isSuccess) {
                val themedUrl = themedResult.getOrThrow()
                val blankUrl = blankResult.getOrThrow()

                _cloudinaryUrl.value = themedUrl
                _blankCloudinaryUrl.value = blankUrl

                val themedQr = BitmapUtils.generateQrCodeBitmap(themedUrl, 512)
                val blankQr = BitmapUtils.generateQrCodeBitmap(blankUrl, 512)

                _qrCodeBitmap.value = themedQr
                _blankQrCodeBitmap.value = blankQr

                _uploadState.value = BoothUploadState.Success(url = themedUrl, blankUrl = blankUrl)
                hapticHelper.captureComplete()
                _toastMessage.emit("Photo strips uploaded! Scan QR Codes 📱")
            } else {
                val error = themedResult.exceptionOrNull() ?: blankResult.exceptionOrNull()
                val msg = error?.message ?: "Upload failed"
                _uploadState.value = BoothUploadState.Error(msg)
                _toastMessage.emit("Cloudinary upload failed: $msg")
            }
        }
    }

    /**
     * Retry uploading the collages if it failed or was offline.
     */
    fun retryUpload() {
        uploadCollages()
    }

    /**
     * Prints the 2x2 collage via the Raspberry Pi CUPS printer API.
     */
    fun printCollage() {
        val collage = _collageBitmap.value
        if (collage == null) {
            viewModelScope.launch {
                _toastMessage.emit("Collage not ready yet")
            }
            return
        }
        if (_printState.value is PrintState.Printing) return

        viewModelScope.launch {
            _printState.value = PrintState.Printing("Sending to TRC_Printer...")
            hapticHelper.tick()
            val ts = _sessionTimestamp.value ?: "session"
            val result = repository.printBitmap(collage, "collage_${ts}.jpg")
            result.fold(
                onSuccess = { res ->
                    _printState.value = PrintState.Success(res.message, res.jobId)
                    hapticHelper.captureComplete()
                    val jobInfo = if (res.jobId != null) " (Job: ${res.jobId})" else ""
                    _toastMessage.emit("🖨️ Sent to TRC_Printer!$jobInfo")
                },
                onFailure = { err ->
                    val errorMsg = err.message ?: "Printing failed"
                    _printState.value = PrintState.Error(errorMsg)
                    _toastMessage.emit("❌ Pi Print Error: $errorMsg")
                }
            )
        }
    }

    /**
     * Reset back to IDLE state for a new session.
     * Only permitted when in COMPLETE state (cannot be cancelled while CAPTURING).
     */
    fun resetSession() {
        if (_boothState.value == BoothState.CAPTURING) return

        _capturedPhotos.value = listOf(null, null, null, null)
        _activeQuadrant.value = 0
        _currentCountdown.value = null
        _savedSessionFiles.value = emptyList()
        _sessionTimestamp.value = null
        _collageBitmap.value = null
        _blankCollageBitmap.value = null
        _collageFile.value = null
        _blankCollageFile.value = null
        _cloudinaryUrl.value = null
        _blankCloudinaryUrl.value = null
        _qrCodeBitmap.value = null
        _blankQrCodeBitmap.value = null
        _uploadState.value = BoothUploadState.Idle
        _printState.value = PrintState.Idle
        _selectedTemplate.value = PhotoBoothTemplate.DEFAULT
        _timerSeconds.value = 3
        _boothState.value = BoothState.IDLE

        // Turn camera back on automatically once new session starts
        repository.setStreamPaused(false)
        repository.logAction("Booth reset to IDLE; camera turned back on for next session", com.trc.photobooth.data.LogType.STATUS)

        if (_selectedFilter.value.id != FilterPresets.RANDOM.id) {
            _currentFeedFilter.value = _selectedFilter.value
        } else {
            _currentFeedFilter.value = FilterPresets.NONE
        }
    }
}
