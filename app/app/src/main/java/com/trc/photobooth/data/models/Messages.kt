package com.trc.photobooth.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

enum class CaptureType {
    @SerialName("photo")
    PHOTO,
    @SerialName("gif")
    GIF
}

@Serializable
data class CaptureMetadata(
    val id: String,
    val type: String,
    val filename: String,
    val url: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Double,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)

@Serializable
data class MemoryStats(
    @SerialName("total_mb")
    val totalMb: Double = 0.0,
    @SerialName("used_mb")
    val usedMb: Double = 0.0,
    @SerialName("free_mb")
    val freeMb: Double = 0.0,
    val percent: Double = 0.0,
)

@Serializable
data class SystemStats(
    @SerialName("cpu_percent")
    val cpuPercent: Double? = null,
    @SerialName("cpu_temp_c")
    val cpuTempC: Double? = null,
    @SerialName("cpu_count")
    val cpuCount: Int? = null,
    val memory: MemoryStats? = null,
    @SerialName("load_avg")
    val loadAvg: List<Double>? = null,
    val throttled: String? = null,
    val timestamp: Double? = null,
)

@Serializable
data class VideoDevice(
    val device: String,
    val index: Int,
    val name: String = "",
    val available: Boolean = true,
)

@Serializable
data class CameraConfig(
    val fps: Int? = 15,
    val quality: Int? = 70,
    val resolution: String? = "480p",
    @SerialName("aspect_ratio")
    val aspectRatio: String? = "4:3",
    val width: Int? = 640,
    val height: Int? = 480,
    @SerialName("flip_horizontal")
    val flipHorizontal: Boolean? = false,
    @SerialName("swap_rb")
    val swapRb: Boolean? = false,
    @SerialName("webcam_device")
    val webcamDevice: Int? = 0,
    @SerialName("device_path")
    val devicePath: String? = "/dev/video0",
    @SerialName("available_devices")
    val availableDevices: List<VideoDevice> = emptyList(),
)

data class CountdownState(
    val secondsLeft: Int,
    val action: String = "photo",
)

data class GifRecordingState(
    val frames: Int = 10,
    val intervalMs: Int = 150,
)

@Serializable
data class PrintResponse(
    val success: Boolean = true,
    val message: String = "",
    @SerialName("job_id")
    val jobId: String? = null,
    val printer: String = "TRC_Printer",
    val command: String? = null,
)

@Serializable
data class PrinterStatus(
    @SerialName("printer_name")
    val printerName: String = "TRC_Printer",
    @SerialName("color_mode")
    val colorMode: String = "monochrome",
    @SerialName("lp_installed")
    val lpInstalled: Boolean = false,
    @SerialName("available_printers")
    val availablePrinters: List<String> = emptyList(),
    @SerialName("is_ready")
    val isReady: Boolean = false,
    @SerialName("status_message")
    val statusMessage: String = "ok",
)

