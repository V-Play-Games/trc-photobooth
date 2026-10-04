package com.trc.photobooth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.trc.photobooth.camera.CameraSource
import com.trc.photobooth.data.PhotoBoothRepository
import com.trc.photobooth.ui.booth.BoothScreen
import com.trc.photobooth.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val repository = remember { PhotoBoothRepository.getInstance(context) }
  val cameraSource by repository.cameraSource.collectAsStateWithLifecycle()
  val androidLens by repository.androidLens.collectAsStateWithLifecycle()

  // Maintain local device camera lifecycle across screens when in Android camera mode
  DisposableEffect(lifecycleOwner, cameraSource, androidLens) {
    if (cameraSource == CameraSource.ANDROID) {
      repository.startLocalCamera(lifecycleOwner)
    }
    onDispose {
      if (cameraSource == CameraSource.ANDROID) {
        repository.stopLocalCamera()
      }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigateToBooth = { backStack.add(Booth) }
          )
        }
        entry<Booth> {
          BoothScreen(
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}

