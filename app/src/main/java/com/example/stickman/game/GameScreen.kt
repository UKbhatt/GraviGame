package com.example.stickman.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Mode {
    data object Play : Mode
    data class Build(val currentStroke: List<Vec2> = emptyList()) : Mode
}

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val gravity by rememberDeviceGravity()

    BoxWithConstraintsFullScreen(modifier) { world ->
        var mode by remember(world) { mutableStateOf<Mode>(Mode.Play) }
        var strokes by remember(world) { mutableStateOf(emptyList<Stroke>()) }
        var state by remember(world) { mutableStateOf(GameState.spawn(world)) }

        LaunchedEffect(world) {
            forEachFrame { dt ->
                if (mode is Mode.Play) state = state.tick(gravity, world, strokes, dt)
            }
        }

        val drawingModifier = if (mode is Mode.Build) {
            Modifier.terrainDrawing(
                onStrokeChange = { points -> mode = Mode.Build(points) },
                onStrokeFinished = { points ->
                    if (points.size > 1) strokes = strokes + Stroke(points)
                    mode = Mode.Build()
                },
            )
        } else {
            Modifier
        }

        val currentMode = mode
        Canvas(Modifier.fillMaxSize().then(drawingModifier)) {
            drawTerrain(strokes)
            when (currentMode) {
                is Mode.Build -> drawPolyline(currentMode.currentStroke)
                Mode.Play -> drawCharacter(state, gravity)
            }
        }

        ControlButton(
            label = if (mode is Mode.Play) "✏ Build" else "▶ Play",
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
        ) {
            if (mode is Mode.Play) {
                mode = Mode.Build()
            } else {
                state = GameState.spawnFree(world, strokes, state.position)
                mode = Mode.Play
            }
        }

        if (mode is Mode.Build) {
            BuildControls(
                canEdit = strokes.isNotEmpty(),
                onUndo = { strokes = strokes.dropLast(1) },
                onClear = { strokes = emptyList() },
            )
        }
    }
}

@Composable
private fun BoxScope.BuildControls(canEdit: Boolean, onUndo: () -> Unit, onClear: () -> Unit) {
    Text(
        text = "Draw your playground",
        color = GamePalette.Ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
    )
    Row(
        modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlButton(label = "↶ Undo", enabled = canEdit, onClick = onUndo)
        ControlButton(label = "✕ Clear", enabled = canEdit, onClick = onClear)
    }
}

/** Thin wrapper that exposes the pixel-sized [World] to the content. */
@Composable
private fun BoxWithConstraintsFullScreen(
    modifier: Modifier,
    content: @Composable BoxScope.(World) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(GamePalette.Background)) {
        val world = remember(constraints.maxWidth, constraints.maxHeight) {
            World(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }
        content(world)
    }
}

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (enabled) GamePalette.ButtonEnabled else GamePalette.ButtonDisabled
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
