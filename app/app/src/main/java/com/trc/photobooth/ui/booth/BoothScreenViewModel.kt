package com.trc.photobooth.ui.booth

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

enum class BoothState {
    IDLE,
    CAPTURING,
    COMPLETE,
}

sealed interface BoothUploadState {
    data object Idle : BoothUploadState
    data class Generating(val message: String = "Creating 2x2 collage...") : BoothUploadState
    data class Uploading(val message: String = "Uploading collage to Cloudinary...") : BoothUploadState
    data class Success(val url: String) : BoothUploadState
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
    val hapticHelper = HapticHelper(application)

    init {
        repository.connectIfNeeded()
    }

    fun reconnect() {
        repository.reconnect()
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

    // 2x2 Composite Collage
    private val _collageBitmap = MutableStateFlow<Bitmap?>(null)
    val collageBitmap: StateFlow<Bitmap?> = _collageBitmap.asStateFlow()

    private val _collageFile = MutableStateFlow<File?>(null)
    val collageFile: StateFlow<File?> = _collageFile.asStateFlow()

    // Cloudinary URL and QR Code
    private val _cloudinaryUrl = MutableStateFlow<String?>(null)
    val cloudinaryUrl: StateFlow<String?> = _cloudinaryUrl.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

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

        captureJob = viewModelScope.launch {
            _boothState.value = BoothState.CAPTURING
            _capturedPhotos.value = listOf(null, null, null, null)

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

                _currentCountdown.value = null

                // Pause so guest sees their captured quadrant before switching to next
                delay(700)
            }

            // 5. Sequence complete! Auto-save all 4 photos to directory
            hapticHelper.captureComplete()
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

            // 6. Generate 1x4 photo strip (210 mm x 74.25 mm)
            _uploadState.value = BoothUploadState.Generating("Creating 1x4 photo strip...")
            val collage = BitmapUtils.createCollage(captures, timestamp)
            _collageBitmap.value = collage
            val cFile = BitmapUtils.saveCollage(getApplication(), collage, timestamp)
            _collageFile.value = cFile

            // 7. Upload photo strip to Cloudinary & Generate QR code
            uploadCollageInternal(collage, timestamp)
        }
    }

    private fun uploadCollageInternal(collage: Bitmap, timestamp: String) {
        viewModelScope.launch {
            val config = CloudinaryConfig.fromBuildConfig()
            if (!config.isConfigured) {
                _uploadState.value = BoothUploadState.Error(
                    "Cloudinary not configured. Set CLOUDINARY_CLOUD_NAME and CLOUDINARY_UPLOAD_PRESET."
                )
                return@launch
            }

            _uploadState.value = BoothUploadState.Uploading("Uploading photo strip to Cloudinary...")
            val result = CloudinaryUploader.uploadBitmap(
                bitmap = collage,
                fileName = "collage_${timestamp}.jpg",
                folder = "trc-photobooth/sessions/$timestamp",
                config = config,
            )

            result.fold(
                onSuccess = { url ->
                    _cloudinaryUrl.value = url
                    val qr = BitmapUtils.generateQrCodeBitmap(url, 512)
                    _qrCodeBitmap.value = qr
                    _uploadState.value = BoothUploadState.Success(url)
                    _toastMessage.emit("Photo strip uploaded! Scan QR Code 📱")
                },
                onFailure = { err ->
                    val msg = err.message ?: "Upload failed"
                    _uploadState.value = BoothUploadState.Error(msg)
                    _toastMessage.emit("Cloudinary upload failed: $msg")
                }
            )
        }
    }

    /**
     * Retry uploading the collage if it failed or was offline.
     */
    fun retryUpload() {
        val collage = _collageBitmap.value ?: return
        val ts = _sessionTimestamp.value ?: return
        uploadCollageInternal(collage, ts)
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
        _collageFile.value = null
        _cloudinaryUrl.value = null
        _qrCodeBitmap.value = null
        _uploadState.value = BoothUploadState.Idle
        _printState.value = PrintState.Idle
        _boothState.value = BoothState.IDLE
        if (_selectedFilter.value.id != FilterPresets.RANDOM.id) {
            _currentFeedFilter.value = _selectedFilter.value
        } else {
            _currentFeedFilter.value = FilterPresets.NONE
        }
    }
}
