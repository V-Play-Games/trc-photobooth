package com.trc.photobooth.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

enum class AndroidLens {
    FRONT,
    BACK
}

enum class CameraSource {
    RASPI,
    ANDROID
}

class LocalCameraManager(private val context: Context) {
    private val tag = "LocalCameraManager"
    private val prefs = context.getSharedPreferences("trc_photobooth_prefs", Context.MODE_PRIVATE)

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var currentLifecycleOwner: LifecycleOwner? = null

    // State flows
    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val initialLens = if (prefs.getString("android_lens", AndroidLens.FRONT.name) == AndroidLens.BACK.name) {
        AndroidLens.BACK
    } else {
        AndroidLens.FRONT
    }
    private val _lens = MutableStateFlow(initialLens)
    val lens: StateFlow<AndroidLens> = _lens.asStateFlow()

    private val _isFlipped = MutableStateFlow(prefs.getBoolean("android_flipped", false))
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _isStreamPaused = MutableStateFlow(false)
    val isStreamPaused: StateFlow<Boolean> = _isStreamPaused.asStateFlow()

    // FPS calculation tracking
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var lastProcessTimestamp = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setLens(newLens: AndroidLens) {
        if (_lens.value == newLens) return
        _lens.value = newLens
        prefs.edit().putString("android_lens", newLens.name).apply()
        currentLifecycleOwner?.let { startCamera(it) }
    }

    fun toggleLens() {
        val next = if (_lens.value == AndroidLens.FRONT) AndroidLens.BACK else AndroidLens.FRONT
        setLens(next)
    }

    fun toggleFlip(value: Boolean? = null) {
        val next = value ?: !_isFlipped.value
        _isFlipped.value = next
        prefs.edit().putBoolean("android_flipped", next).apply()
    }

    fun setStreamPaused(paused: Boolean) {
        _isStreamPaused.value = paused
    }

    fun toggleStreamPause() {
        _isStreamPaused.value = !_isStreamPaused.value
    }

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        currentLifecycleOwner = lifecycleOwner
        if (!hasCameraPermission()) {
            Log.w(tag, "Cannot start camera: CAMERA permission not granted")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val desiredSelector = if (_lens.value == AndroidLens.FRONT) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                // Fallback if desired camera not present on device
                val cameraSelector = if (provider.hasCamera(desiredSelector)) {
                    desiredSelector
                } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    Log.e(tag, "No camera available on this device")
                    return@addListener
                }

                // 4:3 target resolution matching photobooth viewfinder
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 960))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }

                val capture = ImageCapture.Builder()
                    .setTargetResolution(Size(1920, 1440))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis,
                    capture
                )
                _isRunning.value = true
                Log.i(tag, "Camera successfully bound to lifecycle (${_lens.value})")
            } catch (e: Exception) {
                Log.e(tag, "Error binding camera to lifecycle", e)
                _isRunning.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            _isRunning.value = false
            currentLifecycleOwner = null
            Log.i(tag, "Camera stopped and unbound")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping camera", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (_isStreamPaused.value) {
                return
            }

            // Throttle to max ~30 FPS to avoid excessive CPU/battery drain
            val now = System.currentTimeMillis()
            if (now - lastProcessTimestamp < 30L) {
                return
            }
            lastProcessTimestamp = now

            if (!isProcessingFrame.compareAndSet(false, true)) {
                return
            }

            val rawBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }

            // Front cameras should be mirrored by default for standard natural preview
            val isFront = (_lens.value == AndroidLens.FRONT)
            val shouldMirror = if (isFront) !_isFlipped.value else _isFlipped.value
            if (shouldMirror) {
                matrix.postScale(-1f, 1f)
            }

            val finalBitmap = if (matrix.isIdentity) {
                rawBitmap
            } else {
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            }

            _lastFrame.value = finalBitmap
            updateFps()
        } catch (e: Exception) {
            Log.e(tag, "Error processing camera frame", e)
        } finally {
            isProcessingFrame.set(false)
            imageProxy.close()
        }
    }

    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTimestamp
        if (elapsed >= 1000L) {
            _fps.value = ((frameCount * 1000L) / elapsed).toInt()
            frameCount = 0
            lastFpsTimestamp = now
        }
    }

    suspend fun capturePhoto(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resume(_lastFrame.value)
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rawBitmap = imageProxy.toBitmap()
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                        val matrix = Matrix()
                        if (rotationDegrees != 0) {
                            matrix.postRotate(rotationDegrees.toFloat())
                        }
                        val isFront = (_lens.value == AndroidLens.FRONT)
                        val shouldMirror = if (isFront) !_isFlipped.value else _isFlipped.value
                        if (shouldMirror) {
                            matrix.postScale(-1f, 1f)
                        }

                        val finalBitmap = if (matrix.isIdentity) {
                            rawBitmap
                        } else {
                            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        }
                        continuation.resume(finalBitmap)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to transform captured photo", e)
                        continuation.resume(_lastFrame.value)
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture error: ${exception.message}", exception)
                    continuation.resume(_lastFrame.value)
                }
            }
        )
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
