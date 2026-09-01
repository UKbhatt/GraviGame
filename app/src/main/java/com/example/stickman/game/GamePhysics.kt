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
data class Stroke(val points: List<Vec2>)

/** Tunable feel of the game. Distances in pixels, time in seconds. */
object Physics {
    const val GRAVITY_SCALE = 340f       // converts sensor m/s² into on-screen px/s²
    const val HALF_EXTENT = 100f         // blob collision radius; also keeps it on-screen
    const val OBSTACLE_HALF_WIDTH = 13f  // half the thickness of a drawn stroke
    const val WALL_RESTITUTION = 0.2f    // bounciness of walls and drawn terrain (0 = dead stop)
    const val CONTACT_FRICTION = 0.85f   // sliding damping applied on any contact
    const val FACING_THRESHOLD = 5f      // horizontal speed before the eyes change direction
}

/** The playfield — the whole screen. */
data class World(val width: Float, val height: Float)

/** The complete, immutable game state. [cx]/[cy] is the centre of the blob. */
data class GameState(
    val cx: Float,
    val cy: Float,
    val velocityX: Float,
    val velocityY: Float,
    val facingRight: Boolean,
) {
    companion object {
        fun spawn(world: World): GameState = at(world.width / 2f, world.height / 2f)

        private fun at(x: Float, y: Float) =
            GameState(cx = x, cy = y, velocityX = 0f, velocityY = 0f, facingRight = true)

        /**
         * Summons the blob to a spot that clears all terrain: the [preferred] position if
         * it's free, otherwise the nearest free grid cell, otherwise the top-centre.
         */
        fun spawnFree(world: World, obstacles: List<Stroke>, preferred: Vec2): GameState {
            val start = clampToBounds(preferred, world)
            if (isFree(start, world, obstacles)) return at(start.x, start.y)

            var best: Vec2? = null
            var bestDistance = Float.MAX_VALUE
            val step = 60f
            var gx = Physics.HALF_EXTENT
            while (gx <= world.width - Physics.HALF_EXTENT) {
                var gy = Physics.HALF_EXTENT
                while (gy <= world.height - Physics.HALF_EXTENT) {
                    val candidate = Vec2(gx, gy)
                    if (isFree(candidate, world, obstacles)) {
                        val d = squaredDistance(candidate, start)
                        if (d < bestDistance) {
                            bestDistance = d
                            best = candidate
                        }
                    }
                    gy += step
                }
                gx += step
            }
            val chosen = best ?: Vec2(world.width / 2f, Physics.HALF_EXTENT)
            return at(chosen.x, chosen.y)
        }
    }
}

/**
 * Advances the world by [dt] seconds under [gravity] (screen axes, sensor units),
 * resolving against the walls and every drawn [obstacles] stroke.
 */
fun GameState.tick(gravity: Vec2, world: World, obstacles: List<Stroke>, dt: Float): GameState {
    var vx = velocityX + gravity.x * Physics.GRAVITY_SCALE * dt
    var vy = velocityY + gravity.y * Physics.GRAVITY_SCALE * dt
    var x = cx + vx * dt
    var y = cy + vy * dt

    // Screen-edge walls.
    val minX = Physics.HALF_EXTENT
    val maxX = world.width - Physics.HALF_EXTENT
    val minY = Physics.HALF_EXTENT
    val maxY = world.height - Physics.HALF_EXTENT
    if (x < minX) { x = minX; vx = -vx * Physics.WALL_RESTITUTION; vy *= Physics.CONTACT_FRICTION }
    else if (x > maxX) { x = maxX; vx = -vx * Physics.WALL_RESTITUTION; vy *= Physics.CONTACT_FRICTION }
    if (y < minY) { y = minY; vy = -vy * Physics.WALL_RESTITUTION; vx *= Physics.CONTACT_FRICTION }
    else if (y > maxY) { y = maxY; vy = -vy * Physics.WALL_RESTITUTION; vx *= Physics.CONTACT_FRICTION }

    // Drawn terrain. Two passes settle corners where several segments meet.
    val minDistance = Physics.HALF_EXTENT + Physics.OBSTACLE_HALF_WIDTH
    repeat(2) {
        for (stroke in obstacles) {
            val points = stroke.points
            for (i in 0 until points.size - 1) {
                val closest = closestPointOnSegment(x, y, points[i], points[i + 1])
                var dx = x - closest.x
                var dy = y - closest.y
                var distance = sqrt(dx * dx + dy * dy)
                if (distance >= minDistance) continue

                if (distance < 1e-3f) { dx = 0f; dy = -1f; distance = 1f } // centre on the line
                val nx = dx / distance
                val ny = dy / distance
                x = closest.x + nx * minDistance
                y = closest.y + ny * minDistance

                val normalSpeed = vx * nx + vy * ny
                if (normalSpeed < 0f) {
                    vx -= (1f + Physics.WALL_RESTITUTION) * normalSpeed * nx
                    vy -= (1f + Physics.WALL_RESTITUTION) * normalSpeed * ny
                    vx *= Physics.CONTACT_FRICTION
                    vy *= Physics.CONTACT_FRICTION
                }
            }
        }
    }

    // Terrain push-out can nudge past a wall; clamp position back inside without touching velocity.
    x = x.coerceIn(minX, maxX)
    y = y.coerceIn(minY, maxY)

    val facing = when {
        vx < -Physics.FACING_THRESHOLD -> false
        vx > Physics.FACING_THRESHOLD -> true
        else -> facingRight
    }

    return copy(cx = x, cy = y, velocityX = vx, velocityY = vy, facingRight = facing)
}

/** True when a blob centred at [pos] overlaps no wall and no drawn stroke. */
private fun isFree(pos: Vec2, world: World, obstacles: List<Stroke>): Boolean {
    if (pos.x < Physics.HALF_EXTENT || pos.x > world.width - Physics.HALF_EXTENT) return false
    if (pos.y < Physics.HALF_EXTENT || pos.y > world.height - Physics.HALF_EXTENT) return false
    val minDistance = Physics.HALF_EXTENT + Physics.OBSTACLE_HALF_WIDTH
    for (stroke in obstacles) {
        val points = stroke.points
        for (i in 0 until points.size - 1) {
            val closest = closestPointOnSegment(pos.x, pos.y, points[i], points[i + 1])
            if (squaredDistance(pos, closest) < minDistance * minDistance) return false
        }
    }
    return true
}

private fun closestPointOnSegment(px: Float, py: Float, a: Vec2, b: Vec2): Vec2 {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSquared = abx * abx + aby * aby
    if (lengthSquared < 1e-6f) return a
    val t = (((px - a.x) * abx + (py - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
    return Vec2(a.x + abx * t, a.y + aby * t)
}

private fun clampToBounds(pos: Vec2, world: World): Vec2 = Vec2(
    pos.x.coerceIn(Physics.HALF_EXTENT, world.width - Physics.HALF_EXTENT),
    pos.y.coerceIn(Physics.HALF_EXTENT, world.height - Physics.HALF_EXTENT),
)

private fun squaredDistance(a: Vec2, b: Vec2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
