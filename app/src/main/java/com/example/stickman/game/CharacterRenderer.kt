package com.example.stickman.game

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
import kotlin.math.atan2

private const val BODY_WIDTH = 150f
private const val BODY_HEIGHT = 165f
private const val BODY_CORNER = 46f

private const val LEG_LENGTH = 38f
private const val LEG_STROKE = 24f
private const val LEG_SPREAD = 34f
private const val LEG_TOP_INSET = 4f
private const val LEG_FOOT_FLARE = 6f

private const val EYE_SPACING = 30f
private const val EYE_HEIGHT_RATIO = 0.42f
private const val EYE_HALF_WIDTH = 15f
private const val EYE_HALF_HEIGHT = 24f
private const val EYE_SLANT = 22f
private const val GAZE_SHIFT = 8f

private const val TERRAIN_WIDTH = Physics.OBSTACLE_HALF_WIDTH * 2f

internal fun DrawScope.drawTerrain(strokes: List<Stroke>) {
    strokes.forEach { drawPolyline(it.points) }
}

internal fun DrawScope.drawPolyline(points: List<Vec2>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(GamePalette.Terrain, radius = Physics.OBSTACLE_HALF_WIDTH, center = points[0].toOffset())
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(
        path = path,
        color = GamePalette.Terrain,
        style = StrokeStyle(width = TERRAIN_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * The chunky black blob with two angry eyes and stubby legs. It rotates so its legs
 * always point toward the current ground (the direction [gravity] pulls).
 */
internal fun DrawScope.drawCharacter(state: GameState, gravity: Vec2) {
    val angleDeg = -Math.toDegrees(atan2(gravity.x.toDouble(), gravity.y.toDouble())).toFloat()

    rotate(degrees = angleDeg, pivot = state.position.toOffset()) {
        val bodyTop = state.cy - BODY_HEIGHT / 2f
        val bodyBottom = state.cy + BODY_HEIGHT / 2f

        drawLeg(hipX = state.cx - LEG_SPREAD, hipY = bodyBottom - LEG_TOP_INSET, footX = state.cx - LEG_SPREAD - LEG_FOOT_FLARE, footY = bodyBottom + LEG_LENGTH)
        drawLeg(hipX = state.cx + LEG_SPREAD, hipY = bodyBottom - LEG_TOP_INSET, footX = state.cx + LEG_SPREAD + LEG_FOOT_FLARE, footY = bodyBottom + LEG_LENGTH)

        drawRoundRect(
            color = GamePalette.Ink,
            topLeft = Offset(state.cx - BODY_WIDTH / 2f, bodyTop),
            size = Size(BODY_WIDTH, BODY_HEIGHT),
            cornerRadius = CornerRadius(BODY_CORNER, BODY_CORNER),
        )

        val gaze = if (state.facingRight) GAZE_SHIFT else -GAZE_SHIFT
        val eyeY = bodyTop + BODY_HEIGHT * EYE_HEIGHT_RATIO
        drawEye(state.cx - EYE_SPACING + gaze, eyeY, innerOnRight = true)
        drawEye(state.cx + EYE_SPACING + gaze, eyeY, innerOnRight = false)
    }
}

private fun DrawScope.drawLeg(hipX: Float, hipY: Float, footX: Float, footY: Float) {
    drawLine(GamePalette.Ink, Offset(hipX, hipY), Offset(footX, footY), strokeWidth = LEG_STROKE, cap = StrokeCap.Round)
}

/** One white scowling eye. [innerOnRight] true means the left eye (its inner edge faces right). */
private fun DrawScope.drawEye(ex: Float, ey: Float, innerOnRight: Boolean) {
    val innerX = if (innerOnRight) ex + EYE_HALF_WIDTH else ex - EYE_HALF_WIDTH
    val outerX = if (innerOnRight) ex - EYE_HALF_WIDTH else ex + EYE_HALF_WIDTH
    val eye = Path().apply {
        moveTo(outerX, ey - EYE_HALF_HEIGHT)
        lineTo(innerX, ey - EYE_HALF_HEIGHT + EYE_SLANT)
        lineTo(innerX, ey + EYE_HALF_HEIGHT)
        lineTo(outerX, ey + EYE_HALF_HEIGHT)
        close()
    }
    drawPath(eye, Color.White)
}

private fun Vec2.toOffset() = Offset(x, y)
