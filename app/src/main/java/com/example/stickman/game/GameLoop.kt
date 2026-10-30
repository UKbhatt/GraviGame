package com.example.stickman.game

import androidx.compose.runtime.withFrameNanos

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 0.05f

internal suspend fun forEachFrame(onFrame: (dtSeconds: Float) -> Unit) {
    var lastFrameNanos = 0L
    while (true) {
        val now = withFrameNanos { it }
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / NANOS_PER_SECOND
        lastFrameNanos = now
        onFrame(dt.coerceIn(0f, MAX_FRAME_SECONDS))
    }
}
