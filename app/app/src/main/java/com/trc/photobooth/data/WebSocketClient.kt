package com.trc.photobooth.data

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.trc.photobooth.data.models.CameraConfig
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.data.models.CountdownState
import com.trc.photobooth.data.models.GifRecordingState
import com.trc.photobooth.data.models.SystemStats
import com.trc.photobooth.data.models.VideoDevice
import com.trc.photobooth.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.min

class WebSocketClient(
    private val scope: CoroutineScope,
) {
    private val tag = "WebSocketClient"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var pingTimestampMs = 0L
    private var reconnectAttempt = 0
    private var currentUrl: String = ""

    // Observable states
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private val _cameraConfig = MutableStateFlow(CameraConfig())
    val cameraConfig: StateFlow<CameraConfig> = _cameraConfig.asStateFlow()

    private val _systemStats = MutableStateFlow<SystemStats?>(null)
    val systemStats: StateFlow<SystemStats?> = _systemStats.asStateFlow()

    private val _countdown = MutableStateFlow<CountdownState?>(null)
    val countdown: StateFlow<CountdownState?> = _countdown.asStateFlow()

    private val _gifRecording = MutableStateFlow<GifRecordingState?>(null)
    val gifRecording: StateFlow<GifRecordingState?> = _gifRecording.asStateFlow()

    private val _isStreamPaused = MutableStateFlow(false)
    val isStreamPaused: StateFlow<Boolean> = _isStreamPaused.asStateFlow()

    // Shared events
    private val _captureResult = MutableSharedFlow<CaptureMetadata>(extraBufferCapacity = 1)
    val captureResult: SharedFlow<CaptureMetadata> = _captureResult.asSharedFlow()

    private val _flashEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashEvent: SharedFlow<Unit> = _flashEvent.asSharedFlow()

    // Rolling FPS calculation
    private val frameTimestamps = ArrayDeque<Long>()
    private var lastFpsCalcTime = 0L

    fun connect(wsUrl: String) {
        currentUrl = wsUrl
        reconnectAttempt = 0
        disconnectInternal()
        startConnection()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectInternal()
    }

    private fun disconnectInternal() {
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {}
        webSocket = null
        _status.value = ConnectionStatus.DISCONNECTED
    }

    private fun startConnection() {
        if (currentUrl.isBlank()) return

        _status.value = ConnectionStatus.CONNECTING
        Log.i(tag, "Connecting to WebSocket: $currentUrl (attempt: $reconnectAttempt)")

        val request = Request.Builder().url(currentUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected successfully to $currentUrl")
                _status.value = ConnectionStatus.CONNECTED
                reconnectAttempt = 0

                // Request initial state & start heartbeat
                webSocket.send("ping")
                webSocket.send("{\"action\":\"get_system_stats\"}")
                webSocket.send("{\"action\":\"get_devices\"}")
                webSocket.send("{\"action\":\"get_stream_status\"}")
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary frame -> JPEG
                val byteArray = bytes.toByteArray()
                handleBinaryFrame(byteArray)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                _status.value = ConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                _status.value = ConnectionStatus.ERROR
                scheduleReconnect()
            }
        })
    }

    private fun handleBinaryFrame(bytes: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        synchronized(frameTimestamps) {
            frameTimestamps.addLast(now)
            while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < now - 1000L) {
                frameTimestamps.removeFirst()
            }
            if (now - lastFpsCalcTime >= 400L) {
                _fps.value = frameTimestamps.size
                lastFpsCalcTime = now
            }
        }

        // Decode on background pool
        val bitmap = BitmapUtils.decodeJpeg(bytes)
        if (bitmap != null) {
            _lastFrame.value = bitmap
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            val element = json.parseToJsonElement(text).jsonObject
            val type = element["type"]?.jsonPrimitive?.contentOrNull
                ?: element["action"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "pong" -> {
                    if (pingTimestampMs > 0L) {
                        _latencyMs.value = (SystemClock.elapsedRealtime() - pingTimestampMs).toInt().coerceAtLeast(1)
                    }
                }
                "countdown_tick" -> {
                    val sec = element["seconds_left"]?.jsonPrimitive?.intOrNull ?: 0
                    val act = element["action"]?.jsonPrimitive?.contentOrNull ?: "photo"
                    if (sec > 0) {
                        _countdown.value = CountdownState(secondsLeft = sec, action = act)
                    } else {
                        _countdown.value = CountdownState(secondsLeft = 0, action = act)
                        _flashEvent.tryEmit(Unit)
                        scope.launch {
                            delay(800)
                            _countdown.value = null
                        }
                    }
                }
                "capture_result" -> {
                    _countdown.value = null
                    _flashEvent.tryEmit(Unit)
                    element["data"]?.let { dataEl ->
                        val meta = json.decodeFromJsonElement<CaptureMetadata>(dataEl)
                        _captureResult.tryEmit(meta)
                    }
                }
                "gif_recording" -> {
                    val frames = element["frames"]?.jsonPrimitive?.intOrNull ?: 10
                    val interval = element["interval_ms"]?.jsonPrimitive?.intOrNull ?: 150
                    _gifRecording.value = GifRecordingState(frames, interval)
                }
                "gif_result" -> {
                    _gifRecording.value = null
                    _countdown.value = null
                    _flashEvent.tryEmit(Unit)
                    element["data"]?.let { dataEl ->
                        val meta = json.decodeFromJsonElement<CaptureMetadata>(dataEl)
                        _captureResult.tryEmit(meta)
                    }
                }
                "config" -> {
                    val prev = _cameraConfig.value
                    val devs = element["available_devices"]?.let { devArray ->
                        try {
                            json.decodeFromJsonElement<List<VideoDevice>>(devArray)
                        } catch (_: Exception) { null }
                    }
                    _cameraConfig.value = prev.copy(
                        fps = element["fps"]?.jsonPrimitive?.intOrNull ?: prev.fps,
                        quality = element["quality"]?.jsonPrimitive?.intOrNull ?: prev.quality,
                        resolution = element["resolution"]?.jsonPrimitive?.contentOrNull ?: prev.resolution,
                        aspectRatio = element["aspect_ratio"]?.jsonPrimitive?.contentOrNull ?: prev.aspectRatio,
                        width = element["width"]?.jsonPrimitive?.intOrNull ?: prev.width,
                        height = element["height"]?.jsonPrimitive?.intOrNull ?: prev.height,
                        flipHorizontal = element["flip_horizontal"]?.jsonPrimitive?.booleanOrNull ?: prev.flipHorizontal,
                        swapRb = element["swap_rb"]?.jsonPrimitive?.booleanOrNull ?: prev.swapRb,
                        webcamDevice = element["webcam_device"]?.jsonPrimitive?.intOrNull ?: prev.webcamDevice,
                        devicePath = element["device_path"]?.jsonPrimitive?.contentOrNull ?: prev.devicePath,
                        availableDevices = devs ?: prev.availableDevices,
                    )
                }
                "devices" -> {
                    val prev = _cameraConfig.value
                    val currentDev = element["webcam_device"]?.jsonPrimitive?.intOrNull
                    val path = element["device_path"]?.jsonPrimitive?.contentOrNull
                    val devs = element["available_devices"]?.let { devArray ->
                        try {
                            json.decodeFromJsonElement<List<VideoDevice>>(devArray)
                        } catch (_: Exception) { null }
                    }
                    _cameraConfig.value = prev.copy(
                        webcamDevice = currentDev ?: prev.webcamDevice,
                        devicePath = path ?: prev.devicePath,
                        availableDevices = devs ?: prev.availableDevices,
                    )
                }
                "system_stats" -> {
                    element["data"]?.let { dataEl ->
                        val stats = json.decodeFromJsonElement<SystemStats>(dataEl)
                        _systemStats.value = stats
                    }
                }
                "stream_status" -> {
                    val paused = element["is_paused"]?.jsonPrimitive?.booleanOrNull ?: false
                    _isStreamPaused.value = paused
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Non-JSON or unhandled message: $text")
        }
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(4000)
                if (_status.value == ConnectionStatus.CONNECTED) {
                    pingTimestampMs = SystemClock.elapsedRealtime()
                    webSocket?.send("ping")
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoff = min(1000L * (1L shl reconnectAttempt.coerceAtMost(4)), 8000L)
            reconnectAttempt++
            Log.d(tag, "Scheduling reconnect in ${backoff}ms")
            delay(backoff)
            startConnection()
        }
    }

    // Command dispatchers
    fun sendCommand(payloadJson: String): Boolean {
        return webSocket?.send(payloadJson) ?: false
    }

    fun triggerPhoto(countdownSec: Int = 0) {
        sendCommand("{\"action\":\"trigger_capture\",\"countdown\":$countdownSec}")
    }

    fun triggerGif(countdownSec: Int = 0, frames: Int = 10, intervalMs: Int = 150) {
        sendCommand("{\"action\":\"trigger_gif\",\"countdown\":$countdownSec,\"frames\":$frames,\"interval_ms\":$intervalMs}")
    }

    fun setFps(fps: Int) {
        sendCommand("{\"action\":\"set_fps\",\"value\":$fps}")
    }

    fun setQuality(quality: Int) {
        sendCommand("{\"action\":\"set_quality\",\"value\":$quality}")
    }

    fun setResolution(res: String, aspectRatio: String = "4:3") {
        sendCommand("{\"action\":\"set_resolution\",\"value\":\"$res\",\"aspect_ratio\":\"$aspectRatio\"}")
    }

    fun toggleFlip(value: Boolean? = null) {
        val payload = if (value != null) {
            "{\"action\":\"flip_horizontal\",\"value\":$value}"
        } else {
            "{\"action\":\"flip_horizontal\"}"
        }
        sendCommand(payload)
    }

    fun toggleSwapRb(value: Boolean? = null) {
        val payload = if (value != null) {
            "{\"action\":\"swap_rb\",\"value\":$value}"
        } else {
            "{\"action\":\"swap_rb\"}"
        }
        sendCommand(payload)
    }

    fun requestSystemStats() {
        sendCommand("{\"action\":\"get_system_stats\"}")
    }

    fun setCameraDevice(device: String) {
        sendCommand("{\"action\":\"set_device\",\"device\":\"$device\"}")
    }

    fun requestDevices() {
        sendCommand("{\"action\":\"get_devices\"}")
    }

    fun toggleStreamPause() {
        val next = !_isStreamPaused.value
        _isStreamPaused.value = next
        sendCommand("{\"action\":\"toggle_pause\"}")
    }

    fun setStreamPaused(paused: Boolean) {
        _isStreamPaused.value = paused
        val action = if (paused) "pause_stream" else "resume_stream"
        sendCommand("{\"action\":\"$action\"}")
    }

    fun requestStreamStatus() {
        sendCommand("{\"action\":\"get_stream_status\"}")
    }
}
