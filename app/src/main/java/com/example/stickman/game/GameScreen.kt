package com.example.stickman.game

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot

private val BackgroundColor = Color(0xFFBFE3FF)
private val InkColor = Color(0xFF141414)
private val TerrainColor = Color(0xFF6D4C41)

private const val BODY_WIDTH = 150f
private const val BODY_HEIGHT = 165f
private const val BODY_CORNER = 46f
private const val LEG_LENGTH = 38f
private const val LEG_STROKE = 24f
private const val LEG_SPREAD = 34f

private const val TERRAIN_WIDTH = Physics.OBSTACLE_HALF_WIDTH * 2f
private const val MIN_POINT_SPACING = 8f     // don't record points closer than this while drawing

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val gravity by rememberDeviceGravity()

    BoxWithConstraintsFullScreen(modifier) { world ->
        var buildMode by remember { mutableStateOf(false) }
        var strokes by remember(world) { mutableStateOf(emptyList<Stroke>()) }
        var currentStroke by remember { mutableStateOf(emptyList<Vec2>()) }
        var state by remember(world) { mutableStateOf(GameState.spawn(world)) }

        LaunchedEffect(world) {
            var lastFrame = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f)
                lastFrame = now
                if (!buildMode) {
                    state = state.tick(gravity, world, strokes, dt.coerceIn(0f, 0.05f))
                }
            }
        }

        val drawingModifier = if (buildMode) {
            Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> currentStroke = listOf(Vec2(offset.x, offset.y)) },
                    onDrag = { change, _ ->
                        val p = change.position
                        val last = currentStroke.lastOrNull()
                        if (last == null || hypot(p.x - last.x, p.y - last.y) >= MIN_POINT_SPACING) {
                            currentStroke = currentStroke + Vec2(p.x, p.y)
                        }
                    },
                    onDragEnd = {
                        if (currentStroke.size > 1) strokes = strokes + Stroke(currentStroke)
                        currentStroke = emptyList()
                    },
                    onDragCancel = { currentStroke = emptyList() },
                )
            }
        } else {
            Modifier
        }

        Canvas(Modifier.fillMaxSize().then(drawingModifier)) {
            drawTerrain(strokes)
            if (currentStroke.isNotEmpty()) drawPolyline(currentStroke)
            if (!buildMode) drawCharacter(state, gravity)
        }

        // Top-right: the mode toggle. Leaving build mode re-summons the blob to a clear spot.
        ControlButton(
            label = if (buildMode) "▶ Play" else "✏ Build",
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
        ) {
            if (buildMode) {
                state = GameState.spawnFree(world, strokes, Vec2(state.cx, state.cy))
            }
            buildMode = !buildMode
        }

        if (buildMode) {
            Text(
                text = "Draw your playground",
                color = InkColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControlButton(label = "↶ Undo", enabled = strokes.isNotEmpty()) {
                    strokes = strokes.dropLast(1)
                }
                ControlButton(label = "✕ Clear", enabled = strokes.isNotEmpty()) {
                    strokes = emptyList()
                }
            }
        }
    }
}

/** Thin wrapper that exposes the pixel-sized [World] to the content. */
@Composable
private fun BoxWithConstraintsFullScreen(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(World) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().background(BackgroundColor)) {
        val world = remember(constraints.maxWidth, constraints.maxHeight) {
            World(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }
        content(world)
    }
}

@Composable
private fun rememberDeviceGravity(): State<Vec2> {
    val context = LocalContext.current
    val gravity = remember { mutableStateOf(Vec2(0f, 9.81f)) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The gravity sensor reports the vector pointing UP (opposite the pull),
                // so negate to get "toward the ground". Device +y is up while screen y is
                // down, which flips that axis back, leaving (-x, +y).
                gravity.value = Vec2(-event.values[0], event.values[1])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    return gravity
}

private fun DrawScope.drawTerrain(strokes: List<Stroke>) {
    strokes.forEach { drawPolyline(it.points) }
}

private fun DrawScope.drawPolyline(points: List<Vec2>) {
    if (points.size == 1) {
        drawCircle(TerrainColor, radius = Physics.OBSTACLE_HALF_WIDTH, center = Offset(points[0].x, points[0].y))
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(
        path = path,
        color = TerrainColor,
        style = StrokeStyle(width = TERRAIN_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * The chunky black blob with two angry eyes and stubby legs. It rotates so its legs
 * always point toward the current ground (the direction [gravity] pulls).
 */
private fun DrawScope.drawCharacter(state: GameState, gravity: Vec2) {
    val angleDeg = -Math.toDegrees(atan2(gravity.x.toDouble(), gravity.y.toDouble())).toFloat()
    val center = Offset(state.cx, state.cy)

    rotate(degrees = angleDeg, pivot = center) {
        val bodyTop = state.cy - BODY_HEIGHT / 2f
        val bodyBottom = state.cy + BODY_HEIGHT / 2f

        drawLeg(state.cx - LEG_SPREAD, bodyBottom - 4f, state.cx - LEG_SPREAD - 6f, bodyBottom + LEG_LENGTH)
        drawLeg(state.cx + LEG_SPREAD, bodyBottom - 4f, state.cx + LEG_SPREAD + 6f, bodyBottom + LEG_LENGTH)

        drawRoundRect(
            color = InkColor,
            topLeft = Offset(state.cx - BODY_WIDTH / 2f, bodyTop),
            size = Size(BODY_WIDTH, BODY_HEIGHT),
            cornerRadius = CornerRadius(BODY_CORNER, BODY_CORNER),
        )

        val gaze = if (state.facingRight) 8f else -8f
        val eyeY = bodyTop + BODY_HEIGHT * 0.42f
        drawEye(state.cx - 30f + gaze, eyeY, innerOnRight = true)
        drawEye(state.cx + 30f + gaze, eyeY, innerOnRight = false)
    }
}

private fun DrawScope.drawLeg(topX: Float, topY: Float, footX: Float, footY: Float) {
    drawLine(InkColor, Offset(topX, topY), Offset(footX, footY), strokeWidth = LEG_STROKE, cap = StrokeCap.Round)
}

/** One white scowling eye. [innerOnRight] true means the left eye (its inner edge faces right). */
private fun DrawScope.drawEye(ex: Float, ey: Float, innerOnRight: Boolean) {
    val halfW = 15f
    val halfH = 24f
    val slant = 22f
    val eye = Path().apply {
        if (innerOnRight) {
            moveTo(ex - halfW, ey - halfH)
            lineTo(ex + halfW, ey - halfH + slant)
            lineTo(ex + halfW, ey + halfH)
            lineTo(ex - halfW, ey + halfH)
        } else {
            moveTo(ex + halfW, ey - halfH)
            lineTo(ex - halfW, ey - halfH + slant)
            lineTo(ex - halfW, ey + halfH)
            lineTo(ex + halfW, ey + halfH)
        }
        close()
    }
    drawPath(eye, Color.White)
}

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (enabled) Color(0xE6141414) else Color(0x55141414)
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
