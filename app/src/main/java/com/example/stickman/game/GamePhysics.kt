package com.example.stickman.game

import kotlin.math.sqrt

/**
 * Pure game domain — no Android or Compose imports live here on purpose, so the
 * physics can be unit-tested without an emulator.
 *
 * Gravity is a 2D vector that points wherever real-world "down" is (fed in from the
 * device's gravity sensor). The blob is confined to the screen and collides with any
 * hand-drawn [Stroke]s the player laid down in build mode.
 */

/** A plain 2D vector so the domain stays free of Compose's Offset type. */
data class Vec2(val x: Float, val y: Float)

/** One hand-drawn line: a polyline. Each consecutive pair of points is a collidable segment. */
data class Stroke(val points: List<Vec2>) {
    inline fun forEachSegment(action: (start: Vec2, end: Vec2) -> Unit) {
        for (i in 0 until points.size - 1) action(points[i], points[i + 1])
    }
}

/** Tunable feel of the game. Distances in pixels, time in seconds. */
object Physics {
    const val GRAVITY_SCALE = 340f       // converts sensor m/s² into on-screen px/s²
    const val HALF_EXTENT = 100f         // blob collision radius; also keeps it on-screen
    const val OBSTACLE_HALF_WIDTH = 13f  // half the thickness of a drawn stroke
    const val WALL_RESTITUTION = 0.2f    // bounciness of walls and drawn terrain (0 = dead stop)
    const val CONTACT_FRICTION = 0.85f   // sliding damping applied on any contact
    const val FACING_THRESHOLD = 5f      // horizontal speed before the eyes change direction
    const val CONTACT_DISTANCE = HALF_EXTENT + OBSTACLE_HALF_WIDTH
    const val TERRAIN_SETTLE_PASSES = 2  // settles corners where several segments meet
    const val SPAWN_SEARCH_STEP = 60f
}

private const val CENTRED_ON_LINE_EPSILON = 1e-3f
private const val DEGENERATE_SEGMENT_EPSILON = 1e-6f

/** The playfield — the whole screen. The min/max values bound the blob's centre. */
data class World(val width: Float, val height: Float) {
    val minX: Float get() = Physics.HALF_EXTENT
    val maxX: Float get() = width - Physics.HALF_EXTENT
    val minY: Float get() = Physics.HALF_EXTENT
    val maxY: Float get() = height - Physics.HALF_EXTENT

    fun contains(pos: Vec2): Boolean = pos.x in minX..maxX && pos.y in minY..maxY

    fun clamp(pos: Vec2): Vec2 = Vec2(pos.x.coerceIn(minX, maxX), pos.y.coerceIn(minY, maxY))
}

/** The complete, immutable game state. [cx]/[cy] is the centre of the blob. */
data class GameState(
    val cx: Float,
    val cy: Float,
    val velocityX: Float,
    val velocityY: Float,
    val facingRight: Boolean,
) {
    val position: Vec2 get() = Vec2(cx, cy)

    companion object {
        fun spawn(world: World): GameState = at(Vec2(world.width / 2f, world.height / 2f))

        private fun at(pos: Vec2) =
            GameState(cx = pos.x, cy = pos.y, velocityX = 0f, velocityY = 0f, facingRight = true)

        /**
         * Summons the blob to a spot that clears all terrain: the [preferred] position if
         * it's free, otherwise the nearest free grid cell, otherwise the top-centre.
         */
        fun spawnFree(world: World, obstacles: List<Stroke>, preferred: Vec2): GameState {
            val start = world.clamp(preferred)
            if (isFree(start, world, obstacles)) return at(start)

            val nearestFree = gridPoints(world, Physics.SPAWN_SEARCH_STEP)
                .filter { isFree(it, world, obstacles) }
                .minByOrNull { squaredDistance(it, start) }
            return at(nearestFree ?: Vec2(world.width / 2f, world.minY))
        }
    }
}

/**
 * Advances the world by [dt] seconds under [gravity] (screen axes, sensor units),
 * resolving against the walls and every drawn [obstacles] stroke.
 */
fun GameState.tick(gravity: Vec2, world: World, obstacles: List<Stroke>, dt: Float): GameState =
    integrate(gravity, dt)
        .resolveWalls(world)
        .resolveTerrain(obstacles)
        .clampInside(world)
        .withFacing()

private fun GameState.integrate(gravity: Vec2, dt: Float): GameState {
    val vx = velocityX + gravity.x * Physics.GRAVITY_SCALE * dt
    val vy = velocityY + gravity.y * Physics.GRAVITY_SCALE * dt
    return copy(cx = cx + vx * dt, cy = cy + vy * dt, velocityX = vx, velocityY = vy)
}

private fun GameState.resolveWalls(world: World): GameState {
    val afterSides = when {
        cx < world.minX -> bounceOffVerticalWall(world.minX)
        cx > world.maxX -> bounceOffVerticalWall(world.maxX)
        else -> this
    }
    return when {
        afterSides.cy < world.minY -> afterSides.bounceOffHorizontalWall(world.minY)
        afterSides.cy > world.maxY -> afterSides.bounceOffHorizontalWall(world.maxY)
        else -> afterSides
    }
}

private fun GameState.bounceOffVerticalWall(wallX: Float): GameState = copy(
    cx = wallX,
    velocityX = -velocityX * Physics.WALL_RESTITUTION,
    velocityY = velocityY * Physics.CONTACT_FRICTION,
)

private fun GameState.bounceOffHorizontalWall(wallY: Float): GameState = copy(
    cy = wallY,
    velocityY = -velocityY * Physics.WALL_RESTITUTION,
    velocityX = velocityX * Physics.CONTACT_FRICTION,
)

private fun GameState.resolveTerrain(obstacles: List<Stroke>): GameState {
    var resolved = this
    repeat(Physics.TERRAIN_SETTLE_PASSES) {
        for (stroke in obstacles) {
            stroke.forEachSegment { start, end -> resolved = resolved.pushOutOf(start, end) }
        }
    }
    return resolved
}

private fun GameState.pushOutOf(segmentStart: Vec2, segmentEnd: Vec2): GameState {
    val closest = closestPointOnSegment(position, segmentStart, segmentEnd)
    val dx = cx - closest.x
    val dy = cy - closest.y
    val distance = sqrt(dx * dx + dy * dy)
    if (distance >= Physics.CONTACT_DISTANCE) return this

    val normal = if (distance < CENTRED_ON_LINE_EPSILON) Vec2(0f, -1f) else Vec2(dx / distance, dy / distance)
    val pushedX = closest.x + normal.x * Physics.CONTACT_DISTANCE
    val pushedY = closest.y + normal.y * Physics.CONTACT_DISTANCE

    val normalSpeed = velocityX * normal.x + velocityY * normal.y
    if (normalSpeed >= 0f) return copy(cx = pushedX, cy = pushedY)

    val impulse = (1f + Physics.WALL_RESTITUTION) * normalSpeed
    return copy(
        cx = pushedX,
        cy = pushedY,
        velocityX = (velocityX - impulse * normal.x) * Physics.CONTACT_FRICTION,
        velocityY = (velocityY - impulse * normal.y) * Physics.CONTACT_FRICTION,
    )
}

/** Terrain push-out can nudge past a wall; clamp position back inside without touching velocity. */
private fun GameState.clampInside(world: World): GameState {
    val inside = world.clamp(position)
    return copy(cx = inside.x, cy = inside.y)
}

private fun GameState.withFacing(): GameState = copy(
    facingRight = when {
        velocityX < -Physics.FACING_THRESHOLD -> false
        velocityX > Physics.FACING_THRESHOLD -> true
        else -> facingRight
    },
)

/** True when a blob centred at [pos] overlaps no wall and no drawn stroke. */
private fun isFree(pos: Vec2, world: World, obstacles: List<Stroke>): Boolean =
    world.contains(pos) && obstacles.none { it.touches(pos) }

private fun Stroke.touches(pos: Vec2): Boolean {
    val contactDistanceSquared = Physics.CONTACT_DISTANCE * Physics.CONTACT_DISTANCE
    forEachSegment { start, end ->
        if (squaredDistance(pos, closestPointOnSegment(pos, start, end)) < contactDistanceSquared) return true
    }
    return false
}

private fun gridPoints(world: World, step: Float): Sequence<Vec2> =
    axisSteps(world.minX, world.maxX, step).flatMap { x ->
        axisSteps(world.minY, world.maxY, step).map { y -> Vec2(x, y) }
    }

private fun axisSteps(from: Float, to: Float, step: Float): Sequence<Float> =
    generateSequence(from) { it + step }.takeWhile { it <= to }

internal fun closestPointOnSegment(point: Vec2, a: Vec2, b: Vec2): Vec2 {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSquared = abx * abx + aby * aby
    if (lengthSquared < DEGENERATE_SEGMENT_EPSILON) return a
    val t = (((point.x - a.x) * abx + (point.y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
    return Vec2(a.x + abx * t, a.y + aby * t)
}

internal fun squaredDistance(a: Vec2, b: Vec2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
