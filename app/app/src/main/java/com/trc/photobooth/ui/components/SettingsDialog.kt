package com.trc.photobooth.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trc.photobooth.camera.AndroidLens
import com.trc.photobooth.camera.CameraSource
import com.trc.photobooth.data.PhotoBoothRepository
import com.trc.photobooth.data.models.CameraConfig
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.data.models.SystemStats
import com.trc.photobooth.theme.current
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    currentHost: String,
    connectionStatus: ConnectionStatus,
    cameraConfig: CameraConfig,
    systemStats: SystemStats?,
    onSaveHost: (String) -> Unit,
    onStartNsdSearch: () -> Unit,
    onToggleFlip: () -> Unit,
    onToggleSwapRb: () -> Unit,
    onSetFps: (Int) -> Unit,
    onSetQuality: (Int) -> Unit,
    onSetResolution: (String) -> Unit,
    onRefreshStats: () -> Unit,
    onSetCameraDevice: (String) -> Unit = {},
    onRefreshDevices: () -> Unit = {},
    currentPrinterName: String = "TRC_Printer",
    currentColorMode: String = "monochrome",
    currentCopies: Int = 1,
    cameraSource: CameraSource = CameraSource.RASPI,
    onSelectCameraSource: (CameraSource) -> Unit = {},
    androidLens: AndroidLens = AndroidLens.FRONT,
    onSelectAndroidLens: (AndroidLens) -> Unit = {},
    onSavePrinterSettings: (name: String, colorMode: String, copies: Int) -> Unit = { _, _, _ -> },
    isSoundEnabled: Boolean = true,
    onToggleSound: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    val theme = MaterialTheme.current
    val context = LocalContext.current
    var hostInput by remember(currentHost) { mutableStateOf(currentHost) }
    var fpsSlider by remember(cameraConfig.fps) { mutableFloatStateOf((cameraConfig.fps ?: 20).toFloat()) }
    var qualitySlider by remember(cameraConfig.quality) { mutableFloatStateOf((cameraConfig.quality ?: 80).toFloat()) }
    var customDeviceInput by remember(cameraConfig.devicePath) {
        mutableStateOf(cameraConfig.devicePath ?: "/dev/video${cameraConfig.webcamDevice ?: 0}")
    }

    var printerNameInput by remember(currentPrinterName) { mutableStateOf(currentPrinterName) }
    var colorModeInput by remember(currentColorMode) { mutableStateOf(currentColorMode) }
    var copiesInput by remember(currentCopies) { mutableStateOf(currentCopies) }
    var isTestingPrint by remember { mutableStateOf(false) }
    var detectedPrinters by remember { mutableStateOf<List<String>>(emptyList()) }
    var printerStatusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            try {
                val repo = PhotoBoothRepository.getInstance(context)
                val statusResult = repo.getPrinterStatus()
                statusResult.onSuccess { status ->
                    detectedPrinters = status.availablePrinters
                    printerStatusMsg = status.statusMessage
                }
            } catch (ignored: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.scrim.copy(alpha = 0.6f))
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "STUDIO SETTINGS",
                            color = theme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Raspberry Pi Connection & Discovery
                SettingsSectionTitle("BOOTH CONNECTION & DISCOVERY")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .border(1.dp, theme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Raspberry Pi IP / Hostname",
                            color = theme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onSaveHost(hostInput) }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primary,
                                    unfocusedBorderColor = theme.outlineVariant,
                                    focusedTextColor = theme.onSurface,
                                    unfocusedTextColor = theme.onSurface
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = { onSaveHost(hostInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Auto NSD Discovery Button
                        Button(
                            onClick = onStartNsdSearch,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary.copy(alpha = 0.15f), contentColor = theme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Local Wi-Fi for Booth Pi (NSD)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Camera & Stream Configuration
                SettingsSectionTitle("CAMERA & STREAM")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .border(1.dp, theme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Camera Source: Raspberry Pi vs Device Camera (Android)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "CAMERA SOURCE",
                                color = theme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isRaspi = cameraSource == CameraSource.RASPI
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isRaspi) theme.primary else theme.surface)
                                        .border(1.dp, if (isRaspi) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { onSelectCameraSource(CameraSource.RASPI) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "🍓 Raspberry Pi",
                                        color = if (isRaspi) Color.White else theme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val isAndroid = cameraSource == CameraSource.ANDROID
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAndroid) theme.primary else theme.surface)
                                        .border(1.dp, if (isAndroid) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { onSelectCameraSource(CameraSource.ANDROID) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "📱 Device Camera",
                                        color = if (isAndroid) Color.White else theme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (cameraSource == CameraSource.ANDROID) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "DEVICE LENS",
                                    color = theme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isFront = androidLens == AndroidLens.FRONT
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isFront) theme.primary.copy(alpha = 0.2f) else theme.surface)
                                            .border(1.dp, if (isFront) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onSelectAndroidLens(AndroidLens.FRONT) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Front (Selfie)",
                                            color = if (isFront) theme.primary else theme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val isBack = androidLens == AndroidLens.BACK
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isBack) theme.primary.copy(alpha = 0.2f) else theme.surface)
                                            .border(1.dp, if (isBack) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onSelectAndroidLens(AndroidLens.BACK) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Back Camera",
                                            color = if (isBack) theme.primary else theme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                        // Section 2.1: Camera Hardware Device (/dev/video*)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "CAMERA HARDWARE (/dev/video*)",
                                        color = theme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.primary.copy(alpha = 0.15f))
                                        .clickable { onRefreshDevices() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan Devices",
                                        tint = theme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan", color = theme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            val currentDevPath = cameraConfig.devicePath ?: "/dev/video${cameraConfig.webcamDevice ?: 0}"
                            val devices = cameraConfig.availableDevices.ifEmpty {
                                listOf(
                                    com.trc.photobooth.data.models.VideoDevice(
                                        device = currentDevPath,
                                        index = cameraConfig.webcamDevice ?: 0,
                                        name = "Active Hardware Camera",
                                        available = true
                                    )
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                devices.forEach { dev ->
                                    val isSelected = dev.device == currentDevPath || dev.index == cameraConfig.webcamDevice
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surface)
                                            .border(1.dp, if (isSelected) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onSetCameraDevice(dev.device) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = dev.device,
                                                    color = if (isSelected) theme.primary else theme.onSurface,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(theme.primary)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("ACTIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                }
                                            }
                                            if (dev.name.isNotBlank()) {
                                                Text(
                                                    text = dev.name,
                                                    color = theme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isSelected) "Selected" else "Select",
                                            color = if (isSelected) theme.primary else theme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Manual device input fallback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customDeviceInput,
                                    onValueChange = { customDeviceInput = it },
                                    placeholder = { Text("e.g. /dev/video1", color = theme.onSurfaceVariant, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.primary,
                                        unfocusedBorderColor = theme.outlineVariant,
                                        focusedTextColor = theme.onSurface,
                                        unfocusedTextColor = theme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (customDeviceInput.isNotBlank()) {
                                            onSetCameraDevice(customDeviceInput.trim())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Resolution selector chips
                        Text("Stream Resolution", color = theme.onSurfaceVariant, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("640x480" to "480p SD", "960x720" to "720p HD", "1440x1080" to "1080p FHD").forEach { (res, label) ->
                                val curRes = "${cameraConfig.width ?: 640}x${cameraConfig.height ?: 480}"
                                val isSel = curRes == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) theme.primary.copy(alpha = 0.25f) else theme.surface)
                                        .border(1.dp, if (isSel) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { onSetResolution(res) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) theme.primary else theme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // FPS Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Target FPS", color = theme.onSurfaceVariant, fontSize = 12.sp)
                                Text("${fpsSlider.toInt()} fps", color = theme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = fpsSlider,
                                onValueChange = { fpsSlider = it },
                                onValueChangeFinished = { onSetFps(fpsSlider.toInt()) },
                                valueRange = 10f..30f,
                                steps = 19,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary
                                )
                            )
                        }

                        // JPEG Quality Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("JPEG Compression Quality", color = theme.onSurfaceVariant, fontSize = 12.sp)
                                Text("${qualitySlider.toInt()}%", color = theme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = qualitySlider,
                                onValueChange = { qualitySlider = it },
                                onValueChangeFinished = { onSetQuality(qualitySlider.toInt()) },
                                valueRange = 50f..95f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary
                                )
                            )
                        }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Swap Red / Blue Channels", color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Fixes blue-tinted Pi Camera feeds", color = theme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = cameraConfig.swapRb ?: false,
                                    onCheckedChange = { onToggleSwapRb() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = theme.primary,
                                        checkedTrackColor = theme.primary.copy(alpha = 0.35f)
                                    )
                                )
                            }
                        }

                        // Mirror Preview Toggle (applies to both camera sources)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Mirror Preview", color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Flips feed horizontally like a mirror", color = theme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Switch(
                                checked = cameraConfig.flipHorizontal ?: false,
                                onCheckedChange = { onToggleFlip() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.primary,
                                    checkedTrackColor = theme.primary.copy(alpha = 0.35f)
                                )
                            )
                        }

                        // Sound Effects Toggle (Timer beeps & camera shutter)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sound Effects", color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Play timer beeps and camera shutter sounds", color = theme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { onToggleSound() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.primary,
                                    checkedTrackColor = theme.primary.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Hardware Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsSectionTitle("PI HARDWARE TELEMETRY")
                    IconButton(onClick = onRefreshStats, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.onSurfaceVariant)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .border(1.dp, theme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    if (systemStats != null) {
                        val cpu = systemStats.cpuPercent ?: 0.0
                        val temp = systemStats.cpuTempC ?: 0.0
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TelemetryRow("CPU Usage", "${String.format("%.1f", cpu)}%", if (cpu > 80) theme.error else theme.primary)
                            TelemetryRow("CPU Temperature", "${String.format("%.1f", temp)} °C", if (temp > 70) theme.error else theme.secondary)
                            if (systemStats.cpuCount != null) {
                                TelemetryRow("CPU Cores", "${systemStats.cpuCount} Cores", theme.onSurface)
                            }
                            if (systemStats.memory != null) {
                                TelemetryRow("RAM Usage", "${systemStats.memory.percent}% (${systemStats.memory.usedMb.toInt()} / ${systemStats.memory.totalMb.toInt()} MB)", theme.onSurface)
                            }
                            if (systemStats.loadAvg != null) {
                                val loads = systemStats.loadAvg.joinToString(", ") { String.format("%.2f", it) }
                                TelemetryRow("Load Average", loads, theme.onSurfaceVariant)
                            }
                            if (!systemStats.throttled.isNullOrEmpty()) {
                                TelemetryRow("Throttling State", systemStats.throttled, if (systemStats.throttled == "0x0") theme.primary else theme.tertiary)
                            }
                        }
                    } else {
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED) "Requesting telemetry from Pi..." else "Connect to Raspberry Pi to view hardware telemetry",
                            color = theme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // -------------------------------------------------------------
                // TRC PRINTER (CUPS / LP)
                // -------------------------------------------------------------
                SettingsSectionTitle("TRC PRINTER (CUPS / LP)")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant)
                        .border(1.dp, theme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    val scope = rememberCoroutineScope()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Status indicator
                        if (printerStatusMsg != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary)
                                )
                                Text(
                                    text = printerStatusMsg!!,
                                    color = theme.primary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Printer Queue Name Field
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CUPS Printer Queue Name",
                                color = theme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedTextField(
                                value = printerNameInput,
                                onValueChange = { printerNameInput = it },
                                singleLine = true,
                                placeholder = { Text("e.g. TRC_Printer", color = theme.onSurfaceVariant) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primary,
                                    unfocusedBorderColor = theme.outlineVariant,
                                    focusedTextColor = theme.onSurface,
                                    unfocusedTextColor = theme.onSurface,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Quick Suggestions Chips (Defaults + Detected Printers)
                            val suggestions = remember(detectedPrinters) {
                                (listOf("TRC_Printer") + detectedPrinters).distinct()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                suggestions.forEach { suggestion ->
                                    val isSelected = printerNameInput == suggestion
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) theme.primary.copy(alpha = 0.25f) else theme.surface)
                                            .border(1.dp, if (isSelected) theme.primary else theme.outlineVariant, RoundedCornerShape(6.dp))
                                            .clickable { printerNameInput = suggestion }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = suggestion,
                                            color = if (isSelected) theme.primary else theme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }

                        // Color Mode
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Print Color Mode",
                                color = theme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("monochrome" to "Monochrome (B&W)", "color" to "Full Color").forEach { (mode, label) ->
                                    val isSel = colorModeInput == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) theme.primary.copy(alpha = 0.25f) else theme.surface)
                                            .border(1.dp, if (isSel) theme.primary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { colorModeInput = mode }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) theme.primary else theme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        // Copies
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Default Copies",
                                color = theme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(1, 2, 3, 4).forEach { count ->
                                    val isSel = copiesInput == count
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) theme.secondary.copy(alpha = 0.25f) else theme.surface)
                                            .border(1.dp, if (isSel) theme.secondary else theme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { copiesInput = count }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$count ${if (count == 1) "Copy" else "Copies"}",
                                            color = if (isSel) theme.secondary else theme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        // Command Preview
                        val queueName = printerNameInput.ifBlank { "TRC_Printer" }
                        TelemetryRow(
                            label = "Command Preview",
                            value = "lp -d $queueName -o print-color-mode=$colorModeInput${if (copiesInput > 1) " -n $copiesInput" else ""}",
                            valueColor = theme.primary,
                        )

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Save Settings Button
                            Button(
                                onClick = {
                                    val targetQueue = printerNameInput.ifBlank { "TRC_Printer" }
                                    onSavePrinterSettings(targetQueue, colorModeInput, copiesInput)
                                    Toast.makeText(context, "💾 Printer settings saved & synced to Pi!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                            ) {
                                Text(
                                    text = "SAVE SETTINGS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            // Test Print Button
                            Button(
                                onClick = {
                                    if (isTestingPrint) return@Button
                                    scope.launch {
                                        isTestingPrint = true
                                        val targetQueue = printerNameInput.ifBlank { "TRC_Printer" }
                                        Toast.makeText(context, "Executing test print on Pi to '$targetQueue'...", Toast.LENGTH_SHORT).show()
                                        val repo = PhotoBoothRepository.getInstance(context)
                                        val result = repo.testPrint(targetQueue, colorModeInput)
                                        isTestingPrint = false
                                        result.fold(
                                            onSuccess = { res ->
                                                val jobText = if (res.jobId != null) " (Job: ${res.jobId})" else ""
                                                Toast.makeText(context, "🖨️ Test Print Succeeded!$jobText", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "❌ Test Print Error: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.secondary,
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                            ) {
                                if (isTestingPrint) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TESTING...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TEST PRINT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.current.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.current.onSurfaceVariant, fontSize = 12.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
