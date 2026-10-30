package com.example.stickman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickman.game.ControlButton
import com.example.stickman.game.GameMode
import com.example.stickman.game.GamePalette

private val MENU_BUTTON_WIDTH = 220.dp

@Composable
fun MenuScreen(onModeChosen: (GameMode) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(GamePalette.Background),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = GamePalette.Ink,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 32.dp),
        )
        ControlButton(label = "Free Play", modifier = Modifier.width(MENU_BUTTON_WIDTH)) { onModeChosen(GameMode.FreePlay) }
        ControlButton(label = "Star Hunt", modifier = Modifier.width(MENU_BUTTON_WIDTH)) { onModeChosen(GameMode.StarHunt) }
    }
}
