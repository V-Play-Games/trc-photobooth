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
    val flashEvent: SharedFlow<Unit> = wsClient.flashEvent
    val captureResult: SharedFlow<CaptureMetadata> = wsClient.captureResult

    init {
        // Collect new captures pushed over WebSocket
        scope.launch {
            wsClient.captureResult.collect { newCapture ->
                _captures.value = listOf(newCapture) + _captures.value.filter { it.id != newCapture.id }
            }
        }
    }

    fun setHost(newHost: String) {
        val cleanHost = newHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("ws://").removePrefix("wss://")
        _hostAddress.value = cleanHost
        prefs.edit().putString("server_host", cleanHost).apply()
        reconnect()
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
        copies: Int = 1,
    ): Result<PrintResponse> = withContext(Dispatchers.IO) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val bytes = baos.toByteArray()

            var url = "${getBaseHttpUrl()}/api/print?copies=$copies"
            if (printerName != null) url += "&printer_name=$printerName"
            if (colorMode != null) url += "&color_mode=$colorMode"

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
        copies: Int = 1,
    ): Result<PrintResponse> = withContext(Dispatchers.IO) {
        try {
            var url = "${getBaseHttpUrl()}/api/print/capture/$captureId?copies=$copies"
            if (printerName != null) url += "&printer_name=$printerName"
            if (colorMode != null) url += "&color_mode=$colorMode"

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
            var url = "${getBaseHttpUrl()}/api/print/test"
            val queryParams = mutableListOf<String>()
            if (printerName != null) queryParams.add("printer_name=$printerName")
            if (colorMode != null) queryParams.add("color_mode=$colorMode")
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
