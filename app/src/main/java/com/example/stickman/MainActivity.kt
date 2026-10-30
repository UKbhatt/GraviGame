package com.example.stickman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.stickman.game.GameMode
import com.example.stickman.game.GameScreen
import com.example.stickman.ui.theme.StickManTheme

sealed interface Screen {
    data object Menu : Screen
    data class Game(val mode: GameMode) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StickManTheme {
                GrumpApp()
            }
        }
    }
}

@Composable
private fun GrumpApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    when (val current = screen) {
        Screen.Menu -> MenuScreen(onModeChosen = { screen = Screen.Game(it) }, Modifier.fillMaxSize())
        is Screen.Game -> {
            BackHandler { screen = Screen.Menu }
            GameScreen(current.mode, Modifier.fillMaxSize())
        }
    }
}
