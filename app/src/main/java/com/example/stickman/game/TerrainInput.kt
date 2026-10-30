package com.example.stickman.game

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

private const val MIN_POINT_SPACING = 8f

internal fun Modifier.terrainDrawing(
    onStrokeChange: (List<Vec2>) -> Unit,
    onStrokeFinished: (List<Vec2>) -> Unit,
): Modifier = pointerInput(Unit) {
    var points = emptyList<Vec2>()
    detectDragGestures(
        onDragStart = { offset ->
            points = listOf(offset.toVec2())
            onStrokeChange(points)
        },
        onDrag = { change, _ ->
            val point = change.position.toVec2()
            if (points.isFarEnoughFrom(point)) {
                points = points + point
                onStrokeChange(points)
            }
        },
        onDragEnd = {
            onStrokeFinished(points)
            points = emptyList()
        },
        onDragCancel = {
            points = emptyList()
            onStrokeChange(points)
        },
    )
}

private fun List<Vec2>.isFarEnoughFrom(point: Vec2): Boolean {
    val last = lastOrNull() ?: return true
    return hypot(point.x - last.x, point.y - last.y) >= MIN_POINT_SPACING
}

private fun Offset.toVec2() = Vec2(x, y)
