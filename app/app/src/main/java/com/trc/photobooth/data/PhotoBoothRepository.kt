package com.trc.photobooth.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.trc.photobooth.data.models.CameraConfig
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.data.models.CountdownState
import com.trc.photobooth.data.models.GifRecordingState
import com.trc.photobooth.data.models.PrintResponse
import com.trc.photobooth.data.models.PrinterStatus
import com.trc.photobooth.data.models.SystemStats
import androidx.lifecycle.LifecycleOwner
import com.trc.photobooth.camera.AndroidLens
import com.trc.photobooth.camera.CameraSource
import com.trc.photobooth.camera.LocalCameraManager
import com.trc.photobooth.util.BitmapUtils
import com.trc.photobooth.util.SoundHelper
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PhotoBoothRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val tag = "PhotoBoothRepository"
    private val prefs = context.getSharedPreferences("trc_photobooth_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder().build()
    private val wsClient = WebSocketClient(scope)

    // Current host address (e.g. "10.138.75.42:8000" or "10.0.2.2:8000")
    private val _hostAddress = MutableStateFlow(
        prefs.getString("server_host", "10.0.2.2:8000") ?: "10.0.2.2:8000"
    )
    val hostAddress: StateFlow<String> = _hostAddress.asStateFlow()

    // Printer settings
    private val _printerName = MutableStateFlow(
        prefs.getString("printer_name", "TRC_Printer") ?: "TRC_Printer"
    )
    val printerName: StateFlow<String> = _printerName.asStateFlow()

    private val _printerColorMode = MutableStateFlow(
        prefs.getString("printer_color_mode", "monochrome") ?: "monochrome"
    )
    val printerColorMode: StateFlow<String> = _printerColorMode.asStateFlow()

    private val _printerCopies = MutableStateFlow(
        prefs.getInt("printer_copies", 1)
    )
    val printerCopies: StateFlow<Int> = _printerCopies.asStateFlow()

    // Captures list
    private val _captures = MutableStateFlow<List<CaptureMetadata>>(emptyList())
    val captures: StateFlow<List<CaptureMetadata>> = _captures.asStateFlow()

    private val _isLoadingCaptures = MutableStateFlow(false)
    val isLoadingCaptures: StateFlow<Boolean> = _isLoadingCaptures.asStateFlow()

    val localCameraManager = LocalCameraManager(context)

    private val initialCameraSource = try {
        CameraSource.valueOf(
            prefs.getString("camera_source", CameraSource.RASPI.name) ?: CameraSource.RASPI.name
        )
    } catch (e: Exception) {
        CameraSource.RASPI
    }
    private val _cameraSource = MutableStateFlow(initialCameraSource)
    val cameraSource: StateFlow<CameraSource> = _cameraSource.asStateFlow()

    val androidLens: StateFlow<AndroidLens> = localCameraManager.lens
    val isTorchEnabled: StateFlow<Boolean> = localCameraManager.isTorchEnabled

    // Unified connection status (CONNECTED if device camera is in use)
    private val _status = MutableStateFlow(
        if (initialCameraSource == CameraSource.ANDROID) ConnectionStatus.CONNECTED else wsClient.status.value
    )
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    // Unified live frame
    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame.asStateFlow()

    // Unified FPS
    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    // Unified Latency
    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    // Unified CameraConfig
    private val _cameraConfig = MutableStateFlow(
        if (initialCameraSource == CameraSource.ANDROID)
            CameraConfig(width = 1280, height = 960, fps = 30, flipHorizontal = localCameraManager.isFlipped.value)
        else wsClient.cameraConfig.value
    )
    val cameraConfig: StateFlow<CameraConfig> = _cameraConfig.asStateFlow()

    val systemStats: StateFlow<SystemStats?> = wsClient.systemStats

    private val _countdown = MutableStateFlow<CountdownState?>(null)
    val countdown: StateFlow<CountdownState?> = _countdown.asStateFlow()

    private val _gifRecording = MutableStateFlow<GifRecordingState?>(null)
    val gifRecording: StateFlow<GifRecordingState?> = _gifRecording.asStateFlow()

    private val _isStreamPaused = MutableStateFlow(false)
    val isStreamPaused: StateFlow<Boolean> = _isStreamPaused.asStateFlow()

    private val _flashEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val flashEvent: SharedFlow<Unit> = _flashEvent.asSharedFlow()

    private val _captureResult = MutableSharedFlow<CaptureMetadata>(extraBufferCapacity = 5)
    val captureResult: SharedFlow<CaptureMetadata> = _captureResult.asSharedFlow()

    private val _localCountdown = MutableStateFlow<CountdownState?>(null)
    private val _localGifRecording = MutableStateFlow<GifRecordingState?>(null)

    // Action Logs (observable by Admin Panel)
    private val _actionLogs = MutableStateFlow<List<ActionLog>>(listOf(
        ActionLog(
            timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            message = "Admin Console initialized in Light Mode",
            type = LogType.STATUS
        )
    ))
    val actionLogs: StateFlow<List<ActionLog>> = _actionLogs.asStateFlow()

    fun logAction(message: String, type: LogType = LogType.INFO) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = ActionLog(timestamp = time, message = message, type = type)
        _actionLogs.value = (_actionLogs.value + entry).takeLast(100)
        Log.d(tag, "[$time] [${type.name}] $message")
    }

    // Sound effects enabled preference
    private val _isSoundEnabled = MutableStateFlow(
        prefs.getBoolean("sound_effects_enabled", true)
    )
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        prefs.edit().putBoolean("sound_effects_enabled", enabled).apply()
        SoundHelper.getInstance(context).isSoundEnabled = enabled
        logAction("Sound effects ${if (enabled) "enabled" else "disabled"}", LogType.INFO)
    }

    fun toggleSoundEnabled() {
        setSoundEnabled(!_isSoundEnabled.value)
    }

    fun clearLogs() {
        _actionLogs.value = emptyList()
    }

    init {
        // Load initial local device captures from disk
        _captures.value = loadLocalCaptures()

        // Sync Pi captures pushed over WebSocket
        scope.launch {
            wsClient.captureResult.collect { newCapture ->
                _captures.value = listOf(newCapture) + _captures.value.filter { it.id != newCapture.id }
                if (_cameraSource.value == CameraSource.RASPI) {
                    _captureResult.emit(newCapture)
                }
                logAction("High-res capture saved: ${newCapture.filename}", LogType.COMPLETE)
            }
        }

        // Bridge status: always CONNECTED when using local camera, otherwise wsClient status
        scope.launch {
            combine(_cameraSource, wsClient.status) { src, wsSt ->
                if (src == CameraSource.ANDROID) ConnectionStatus.CONNECTED else wsSt
            }.collect { st ->
                _status.value = st
            }
        }

        // Bridge lastFrame
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    localCameraManager.lastFrame.collect { frame ->
                        _lastFrame.value = frame
                    }
                } else {
                    wsClient.lastFrame.collect { frame ->
                        _lastFrame.value = frame
                    }
                }
            }
        }

        // Bridge FPS
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    localCameraManager.fps.collect { f ->
                        _fps.value = f
                    }
                } else {
                    wsClient.fps.collect { f ->
                        _fps.value = f
                    }
                }
            }
        }

        // Bridge Latency
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    _latencyMs.value = 0
                } else {
                    wsClient.latencyMs.collect { lat ->
                        _latencyMs.value = lat
                    }
                }
            }
        }

        // Bridge CameraConfig
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    combine(localCameraManager.fps, localCameraManager.isFlipped) { fpsVal, flip ->
                        CameraConfig(width = 1280, height = 960, fps = fpsVal, flipHorizontal = flip)
                    }.collect { cfg ->
                        _cameraConfig.value = cfg
                    }
                } else {
                    wsClient.cameraConfig.collect { cfg ->
                        _cameraConfig.value = cfg
                    }
                }
            }
        }

        // Bridge isStreamPaused
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    localCameraManager.isStreamPaused.collect { p ->
                        _isStreamPaused.value = p
                    }
                } else {
                    wsClient.isStreamPaused.collect { p ->
                        _isStreamPaused.value = p
                    }
                }
            }
        }

        // Bridge countdown
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    _localCountdown.collect { c ->
                        _countdown.value = c
                    }
                } else {
                    wsClient.countdown.collect { c ->
                        _countdown.value = c
                    }
                }
            }
        }

        // Bridge gifRecording
        scope.launch {
            _cameraSource.collectLatest { src ->
                if (src == CameraSource.ANDROID) {
                    _localGifRecording.collect { g ->
                        _gifRecording.value = g
                    }
                } else {
                    wsClient.gifRecording.collect { g ->
                        _gifRecording.value = g
                    }
                }
            }
        }

        // Bridge flashEvent from Pi WebSocket
        scope.launch {
            wsClient.flashEvent.collect {
                if (_cameraSource.value == CameraSource.RASPI) {
                    _flashEvent.emit(Unit)
                }
            }
        }

        // Monitor connection status changes for logging
        scope.launch {
            wsClient.status.collect { st ->
                if (_cameraSource.value == CameraSource.RASPI) {
                    when (st) {
                        ConnectionStatus.CONNECTED -> logAction("Connected to Pi at ${_hostAddress.value}", LogType.STATUS)
                        ConnectionStatus.DISCONNECTED -> logAction("Disconnected from Pi (${_hostAddress.value})", LogType.STATUS)
                        ConnectionStatus.CONNECTING -> logAction("Connecting to Pi at ${_hostAddress.value}...", LogType.STATUS)
                        ConnectionStatus.ERROR -> logAction("Connection error with Pi at ${_hostAddress.value}", LogType.ERROR)
                    }
                }
            }
        }

        // Monitor camera on/off (pause state)
        scope.launch {
            var isFirst = true
            _isStreamPaused.collect { isPaused ->
                if (isFirst) {
                    isFirst = false
                    return@collect
                }
                if (isPaused) {
                    logAction("Camera turned OFF", LogType.CAMERA)
                } else {
                    logAction("Camera turned ON", LogType.CAMERA)
                }
            }
        }
    }

    fun setHost(newHost: String) {
        val cleanHost = newHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("ws://").removePrefix("wss://")
        _hostAddress.value = cleanHost
        prefs.edit().putString("server_host", cleanHost).apply()
        reconnect()
    }

    fun setPrinterSettings(name: String, colorMode: String, copies: Int) {
        val safeName = name.trim().ifEmpty { "TRC_Printer" }
        val safeMode = if (colorMode.lowercase() == "color") "color" else "monochrome"
        val safeCopies = copies.coerceIn(1, 10)

        _printerName.value = safeName
        _printerColorMode.value = safeMode
        _printerCopies.value = safeCopies

        prefs.edit()
            .putString("printer_name", safeName)
            .putString("printer_color_mode", safeMode)
            .putInt("printer_copies", safeCopies)
            .apply()

        // Sync with Pi in background if connected
        scope.launch(Dispatchers.IO) {
            try {
                syncPrinterSettingsToPi(safeName, safeMode)
            } catch (e: Exception) {
                Log.w(tag, "Could not sync printer settings to Pi: ${e.message}")
            }
        }
    }

    suspend fun syncPrinterSettingsToPi(name: String, colorMode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseHttpUrl()}/api/print/config"
            val payload = json.encodeToString(
                com.trc.photobooth.data.models.PrinterConfigUpdate.serializer(),
                com.trc.photobooth.data.models.PrinterConfigUpdate(printerName = name, colorMode = colorMode)
            )
            val body = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to update printer config on Pi (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun reconnect() {
        val host = _hostAddress.value
        val wsUrl = "ws://$host/ws/feed"
        wsClient.connect(wsUrl)
        fetchCaptures()
    }

    fun connectIfNeeded() {
        if (status.value == ConnectionStatus.DISCONNECTED) {
            reconnect()
        }
    }

    fun disconnect() {
        wsClient.disconnect()
    }

    fun getBaseHttpUrl(): String {
        return "http://${_hostAddress.value}"
    }

    fun getFullMediaUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("file://")) {
            pathOrUrl
        } else {
            val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
            "http://${_hostAddress.value}$prefix$pathOrUrl"
        }
    }

    // Camera source management
    fun setCameraSource(source: CameraSource) {
        if (_cameraSource.value == source) return
        _cameraSource.value = source
        prefs.edit().putString("camera_source", source.name).apply()
        if (source == CameraSource.ANDROID) {
            logAction("Switched to Device Camera (Android)", LogType.CAMERA)
        } else {
            logAction("Switched to Raspberry Pi Camera", LogType.CAMERA)
            reconnect()
        }
    }

    fun toggleCameraSource() {
        val next = if (_cameraSource.value == CameraSource.RASPI) CameraSource.ANDROID else CameraSource.RASPI
        setCameraSource(next)
    }

    fun setAndroidLens(lens: AndroidLens) {
        localCameraManager.setLens(lens)
        logAction("Device camera lens switched to ${lens.name}", LogType.CAMERA)
    }

    fun toggleAndroidLens() {
        localCameraManager.toggleLens()
        logAction("Device camera lens toggled (${localCameraManager.lens.value.name})", LogType.CAMERA)
    }

    fun startLocalCamera(lifecycleOwner: LifecycleOwner) {
        localCameraManager.startCamera(lifecycleOwner)
    }

    fun stopLocalCamera() {
        localCameraManager.stopCamera()
    }

    fun toggleTorch(): Boolean {
        val enabled = localCameraManager.toggleTorch()
        logAction("Device camera torch ${if (enabled) "ON" else "OFF"}", LogType.CAMERA)
        return enabled
    }

    fun setTorch(enabled: Boolean) {
        localCameraManager.setTorch(enabled)
    }

    fun focusLocalCamera(xNorm: Float, yNorm: Float) {
        localCameraManager.focusAt(xNorm, yNorm)
    }

    // Capture controls
    fun triggerPhoto(countdownSec: Int = 0) {
        if (_cameraSource.value == CameraSource.RASPI) {
            wsClient.triggerPhoto(countdownSec)
            return
        }
        scope.launch {
            if (countdownSec > 0) {
                for (s in countdownSec downTo 1) {
                    _localCountdown.value = CountdownState(secondsLeft = s, action = "photo")
                    delay(1000)
                }
            }
            _localCountdown.value = CountdownState(secondsLeft = 0, action = "photo")
            _flashEvent.emit(Unit)
            delay(80)

            val bitmap = localCameraManager.capturePhoto() ?: localCameraManager.lastFrame.value
            _localCountdown.value = null

            if (bitmap != null) {
                saveDeviceCapture(bitmap, isGif = false)
            } else {
                logAction("Failed to capture frame from device camera", LogType.ERROR)
            }
        }
    }

    fun triggerGif(countdownSec: Int = 0, frames: Int = 10, intervalMs: Int = 150) {
        if (_cameraSource.value == CameraSource.RASPI) {
            wsClient.triggerGif(countdownSec, frames, intervalMs)
            return
        }
        scope.launch {
            if (countdownSec > 0) {
                for (s in countdownSec downTo 1) {
                    _localCountdown.value = CountdownState(secondsLeft = s, action = "gif")
                    delay(1000)
                }
            }
            _localCountdown.value = CountdownState(secondsLeft = 0, action = "gif")
            _flashEvent.emit(Unit)
            _localGifRecording.value = GifRecordingState(frames = frames, intervalMs = intervalMs)

            val capturedList = mutableListOf<Bitmap>()
            for (i in 0 until frames) {
                localCameraManager.lastFrame.value?.let { capturedList.add(it) }
                delay(intervalMs.toLong())
            }
            _localGifRecording.value = null
            _localCountdown.value = null

            if (capturedList.isNotEmpty()) {
                saveDeviceCapture(capturedList.first(), isGif = true)
            }
        }
    }

    private fun saveDeviceCapture(bitmap: Bitmap, isGif: Boolean) {
        val file = BitmapUtils.saveSinglePhoto(context, bitmap, prefix = if (isGif) "device_burst" else "device_photo")
        if (file != null) {
            val metadata = CaptureMetadata(
                id = "local_${System.currentTimeMillis()}",
                type = if (isGif) "gif" else "photo",
                filename = file.name,
                url = "file://${file.absolutePath}",
                thumbnailUrl = "file://${file.absolutePath}",
                createdAt = System.currentTimeMillis() / 1000.0,
                sizeBytes = file.length(),
                width = bitmap.width,
                height = bitmap.height
            )
            _captures.value = listOf(metadata) + _captures.value.filter { it.id != metadata.id }
            scope.launch {
                _captureResult.emit(metadata)
            }
            logAction("Device photo saved: ${file.name}", LogType.COMPLETE)
        }
    }

    fun loadLocalCaptures(): List<CaptureMetadata> {
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            "TRCPhotoBooth/captures"
        )
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            CaptureMetadata(
                id = "local_${f.nameWithoutExtension}",
                type = if (f.name.contains("burst") || f.extension.lowercase() == "gif") "gif" else "photo",
                filename = f.name,
                url = "file://${f.absolutePath}",
                thumbnailUrl = "file://${f.absolutePath}",
                createdAt = f.lastModified() / 1000.0,
                sizeBytes = f.length(),
                width = 1280,
                height = 960
            )
        }
    }

    fun setFps(fps: Int) = wsClient.setFps(fps)
    fun setQuality(quality: Int) = wsClient.setQuality(quality)
    fun setResolution(res: String, aspectRatio: String = "16:9") = wsClient.setResolution(res, aspectRatio)

    fun toggleFlip(value: Boolean? = null) {
        if (_cameraSource.value == CameraSource.RASPI) {
            wsClient.toggleFlip(value)
        } else {
            localCameraManager.toggleFlip(value)
            _cameraConfig.value = _cameraConfig.value.copy(flipHorizontal = localCameraManager.isFlipped.value)
        }
    }

    fun toggleSwapRb(value: Boolean? = null) = wsClient.toggleSwapRb(value)
    fun requestSystemStats() = wsClient.requestSystemStats()
    fun setCameraDevice(device: String) = wsClient.setCameraDevice(device)
    fun requestDevices() = wsClient.requestDevices()

    fun toggleStreamPause() {
        if (_cameraSource.value == CameraSource.RASPI) {
            wsClient.toggleStreamPause()
        } else {
            localCameraManager.toggleStreamPause()
            _isStreamPaused.value = localCameraManager.isStreamPaused.value
        }
    }

    fun setStreamPaused(paused: Boolean) {
        if (_cameraSource.value == CameraSource.RASPI) {
            wsClient.setStreamPaused(paused)
        } else {
            localCameraManager.setStreamPaused(paused)
            _isStreamPaused.value = paused
        }
    }

    // REST operations
    fun fetchCaptures() {
        scope.launch(Dispatchers.IO) {
            _isLoadingCaptures.value = true
            try {
                val url = "${getBaseHttpUrl()}/api/captures"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = json.parseToJsonElement(body).jsonObject
                            val itemsJson = root["captures"]
                            if (itemsJson != null) {
                                val list = json.decodeFromJsonElement<List<CaptureMetadata>>(itemsJson)
                                val local = loadLocalCaptures()
                                _captures.value = (list + local).distinctBy { it.id }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed fetching captures: ${e.message}")
            } finally {
                _isLoadingCaptures.value = false
            }
        }
    }

    suspend fun deleteCapture(id: String): Boolean = withContext(Dispatchers.IO) {
        if (id.startsWith("local_")) {
            val item = _captures.value.find { it.id == id }
            if (item != null) {
                val filePath = item.url.removePrefix("file://")
                val f = File(filePath)
                if (f.exists()) f.delete()
            }
            _captures.value = _captures.value.filter { it.id != id }
            return@withContext true
        }
        try {
            val url = "${getBaseHttpUrl()}/api/captures/$id"
            val request = Request.Builder().url(url).delete().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _captures.value = _captures.value.filter { it.id != id }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed deleting capture $id", e)
            false
        }
    }

    suspend fun printBitmap(
        bitmap: Bitmap,
        filename: String = "collage.jpg",
        printerName: String? = null,
        colorMode: String? = null,
        copies: Int? = null,
    ): Result<PrintResponse> = withContext(Dispatchers.IO) {
        try {
            val resolvedPrinter = printerName ?: _printerName.value.ifBlank { null }
            val resolvedColorMode = colorMode ?: _printerColorMode.value.ifBlank { null }
            val resolvedCopies = copies ?: _printerCopies.value

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val bytes = baos.toByteArray()

            var url = "${getBaseHttpUrl()}/api/print?copies=$resolvedCopies"
            if (resolvedPrinter != null) url += "&printer_name=$resolvedPrinter"
            if (resolvedColorMode != null) url += "&color_mode=$resolvedColorMode"

            val body = bytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val printResp = json.decodeFromString<PrintResponse>(bodyStr)
                    Result.success(printResp)
                } else {
                    val errorMsg = try {
                        val root = json.parseToJsonElement(bodyStr).jsonObject
                        root["detail"]?.toString()?.trim('"') ?: bodyStr
                    } catch (e: Exception) {
                        bodyStr
                    }
                    Result.failure(Exception("Print failed (${response.code}): $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed printing bitmap: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun printCapture(
        captureId: String,
        printerName: String? = null,
        colorMode: String? = null,
        copies: Int? = null,
    ): Result<PrintResponse> = withContext(Dispatchers.IO) {
        try {
            val resolvedPrinter = printerName ?: _printerName.value.ifBlank { null }
            val resolvedColorMode = colorMode ?: _printerColorMode.value.ifBlank { null }
            val resolvedCopies = copies ?: _printerCopies.value

            var url = "${getBaseHttpUrl()}/api/print/capture/$captureId?copies=$resolvedCopies"
            if (resolvedPrinter != null) url += "&printer_name=$resolvedPrinter"
            if (resolvedColorMode != null) url += "&color_mode=$resolvedColorMode"

            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val printResp = json.decodeFromString<PrintResponse>(bodyStr)
                    Result.success(printResp)
                } else {
                    val errorMsg = try {
                        val root = json.parseToJsonElement(bodyStr).jsonObject
                        root["detail"]?.toString()?.trim('"') ?: bodyStr
                    } catch (e: Exception) {
                        bodyStr
                    }
                    Result.failure(Exception("Print failed (${response.code}): $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed printing capture $captureId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun testPrint(
        printerName: String? = null,
        colorMode: String? = null,
    ): Result<PrintResponse> = withContext(Dispatchers.IO) {
        try {
            val resolvedPrinter = printerName ?: _printerName.value.ifBlank { null }
            val resolvedColorMode = colorMode ?: _printerColorMode.value.ifBlank { null }

            var url = "${getBaseHttpUrl()}/api/print/test"
            val queryParams = mutableListOf<String>()
            if (resolvedPrinter != null) queryParams.add("printer_name=$resolvedPrinter")
            if (resolvedColorMode != null) queryParams.add("color_mode=$resolvedColorMode")
            if (queryParams.isNotEmpty()) {
                url += "?" + queryParams.joinToString("&")
            }

            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val printResp = json.decodeFromString<PrintResponse>(bodyStr)
                    Result.success(printResp)
                } else {
                    val errorMsg = try {
                        val root = json.parseToJsonElement(bodyStr).jsonObject
                        root["detail"]?.toString()?.trim('"') ?: bodyStr
                    } catch (e: Exception) {
                        bodyStr
                    }
                    Result.failure(Exception("Test print failed (${response.code}): $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed test print: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPrinterStatus(): Result<PrinterStatus> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseHttpUrl()}/api/print/status"
            val request = Request.Builder().url(url).get().build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val status = json.decodeFromString<PrinterStatus>(bodyStr)
                    Result.success(status)
                } else {
                    Result.failure(Exception("Failed getting printer status (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed getting printer status: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: PhotoBoothRepository? = null

        fun getInstance(context: Context): PhotoBoothRepository {
            return instance ?: synchronized(this) {
                instance ?: PhotoBoothRepository(
                    context.applicationContext,
                    CoroutineScope(SupervisorJob() + Dispatchers.Default)
                ).also {
                    instance = it
                }
            }
        }
    }
}

data class ActionLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO,
)

enum class LogType {
    INFO,
    CAPTURE,
    STATUS,
    CAMERA,
    COMPLETE,
    ERROR,
}

