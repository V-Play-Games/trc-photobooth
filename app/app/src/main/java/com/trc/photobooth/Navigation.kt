package com.trc.photobooth

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.trc.photobooth.ui.booth.BoothScreen
import com.trc.photobooth.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

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

