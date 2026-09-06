package com.kursi.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kursi.shared.KursiApp

fun main() =
    application {
        // Open at a desktop size so the windowed app uses the wide DesktopLayout.
        val windowState = rememberWindowState(size = DpSize(1320.dp, 880.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Gaddi",
        ) {
            KursiApp()
        }
    }
