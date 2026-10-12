package com.example.stickman.game

import androidx.compose.ui.graphics.Color

internal object GamePalette {
    val Background = Color(0xFFBFE3FF)
    val Ink = Color(0xFF141414)
    val Terrain = Color(0xFF6D4C41)
    val ButtonEnabled = Ink.copy(alpha = 0.9f)
    val ButtonDisabled = Ink.copy(alpha = 0.33f)
}
