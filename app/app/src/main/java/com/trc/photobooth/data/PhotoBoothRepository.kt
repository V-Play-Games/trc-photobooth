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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Pass-through WebSocket flows
    val status: StateFlow<ConnectionStatus> = wsClient.status
    val lastFrame: StateFlow<Bitmap?> = wsClient.lastFrame
    val fps: StateFlow<Int> = wsClient.fps
    val latencyMs: StateFlow<Int> = wsClient.latencyMs
    val cameraConfig: StateFlow<CameraConfig> = wsClient.cameraConfig
    val systemStats: StateFlow<SystemStats?> = wsClient.systemStats
    val countdown: StateFlow<CountdownState?> = wsClient.countdown
    val gifRecording: StateFlow<GifRecordingState?> = wsClient.gifRecording
    val isStreamPaused: StateFlow<Boolean> = wsClient.isStreamPaused
    val flashEvent: SharedFlow<Unit> = wsClient.flashEvent
    val captureResult: SharedFlow<CaptureMetadata> = wsClient.captureResult

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

    fun clearLogs() {
        _actionLogs.value = emptyList()
    }

    init {
        // Collect new captures pushed over WebSocket
        scope.launch {
            wsClient.captureResult.collect { newCapture ->
                _captures.value = listOf(newCapture) + _captures.value.filter { it.id != newCapture.id }
                logAction("High-res capture saved: ${newCapture.filename}", LogType.COMPLETE)
            }
        }

        // Monitor connection status changes for logging
        scope.launch {
            wsClient.status.collect { st ->
                when (st) {
                    ConnectionStatus.CONNECTED -> logAction("Connected to Pi at ${_hostAddress.value}", LogType.STATUS)
                    ConnectionStatus.DISCONNECTED -> logAction("Disconnected from Pi (${_hostAddress.value})", LogType.STATUS)
                    ConnectionStatus.CONNECTING -> logAction("Connecting to Pi at ${_hostAddress.value}...", LogType.STATUS)
                    ConnectionStatus.ERROR -> logAction("Connection error with Pi at ${_hostAddress.value}", LogType.ERROR)
                }
            }
        }

        // Monitor camera on/off (pause state)
        scope.launch {
            var isFirst = true
            wsClient.isStreamPaused.collect { isPaused ->
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
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
            "http://${_hostAddress.value}$prefix$pathOrUrl"
        }
    }

    // Capture controls
    fun triggerPhoto(countdownSec: Int = 0) = wsClient.triggerPhoto(countdownSec)
    fun triggerGif(countdownSec: Int = 0, frames: Int = 10, intervalMs: Int = 150) =
        wsClient.triggerGif(countdownSec, frames, intervalMs)

    fun setFps(fps: Int) = wsClient.setFps(fps)
    fun setQuality(quality: Int) = wsClient.setQuality(quality)
    fun setResolution(res: String, aspectRatio: String = "16:9") = wsClient.setResolution(res, aspectRatio)
    fun toggleFlip(value: Boolean? = null) = wsClient.toggleFlip(value)
    fun toggleSwapRb(value: Boolean? = null) = wsClient.toggleSwapRb(value)
    fun requestSystemStats() = wsClient.requestSystemStats()
    fun setCameraDevice(device: String) = wsClient.setCameraDevice(device)
    fun requestDevices() = wsClient.requestDevices()
    fun toggleStreamPause() = wsClient.toggleStreamPause()
    fun setStreamPaused(paused: Boolean) = wsClient.setStreamPaused(paused)

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
                                _captures.value = list
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

