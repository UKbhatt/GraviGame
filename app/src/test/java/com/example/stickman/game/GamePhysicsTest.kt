package com.example.stickman.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GamePhysicsTest {

    private val world = World(width = 1000f, height = 2000f)
    private val noGravity = Vec2(0f, 0f)
    private val downwardGravity = Vec2(0f, 9.81f)
    private val frame = 1f / 60f
    private val tolerance = 1e-3f

    private fun blobAt(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) =
        GameState(cx = x, cy = y, velocityX = vx, velocityY = vy, facingRight = true)

    private fun horizontalStroke(y: Float) = Stroke(listOf(Vec2(0f, y), Vec2(world.width, y)))

    @Test
    fun `blob resting on the floor stays on the floor under downward gravity`() {
        var state = blobAt(500f, world.maxY)

        repeat(300) { state = state.tick(downwardGravity, world, emptyList(), frame) }

        assertEquals(world.maxY, state.cy, tolerance)
        assertEquals(500f, state.cx, tolerance)
        assertTrue(world.contains(state.position))
    }

    @Test
    fun `blob hitting the right wall bounces back scaled by restitution`() {
        val state = blobAt(world.maxX - 1f, 500f, vx = 100f).tick(noGravity, world, emptyList(), dt = 0.1f)

        assertEquals(world.maxX, state.cx, tolerance)
        assertEquals(-100f * Physics.WALL_RESTITUTION, state.velocityX, tolerance)
    }

    @Test
    fun `blob falling onto a stroke is pushed out and bounces upward`() {
        val floor = horizontalStroke(1000f)
        val falling = blobAt(500f, 1000f - Physics.CONTACT_DISTANCE - 7f, vy = 500f)

        val landed = falling.tick(noGravity, world, listOf(floor), dt = 0.05f)

        assertEquals(1000f - Physics.CONTACT_DISTANCE, landed.cy, tolerance)
        assertTrue("expected upward velocity after bounce", landed.velocityY < 0f)
    }

    @Test
    fun `blob already touching a stroke with no approach speed is not bounced`() {
        val floor = horizontalStroke(1000f)
        val overlapping = blobAt(500f, 1000f - Physics.CONTACT_DISTANCE + 10f)

        val settled = overlapping.tick(noGravity, world, listOf(floor), frame)

        assertEquals(1000f - Physics.CONTACT_DISTANCE, settled.cy, tolerance)
        assertEquals(0f, settled.velocityY, tolerance)
    }

    @Test
    fun `zero-length stroke segment under the blob pushes it up without NaN`() {
        val dot = Stroke(listOf(Vec2(500f, 800f), Vec2(500f, 800f)))

        val state = blobAt(500f, 800f).tick(noGravity, world, listOf(dot), frame)

        assertFalse(state.cx.isNaN() || state.cy.isNaN())
        assertEquals(800f - Physics.CONTACT_DISTANCE, state.cy, tolerance)
    }

    @Test
    fun `facing flips left only once horizontal speed passes the threshold`() {
        val slow = blobAt(500f, 500f, vx = -Physics.FACING_THRESHOLD / 2f).tick(noGravity, world, emptyList(), frame)
        val fast = blobAt(500f, 500f, vx = -Physics.FACING_THRESHOLD * 2f).tick(noGravity, world, emptyList(), frame)

        assertTrue(slow.facingRight)
        assertFalse(fast.facingRight)
    }

    @Test
    fun `spawnFree returns the preferred spot when it is clear`() {
        val preferred = Vec2(300f, 400f)

        val spawned = GameState.spawnFree(world, emptyList(), preferred)

        assertEquals(preferred, spawned.position)
    }

    @Test
    fun `spawnFree moves off a stroke covering the preferred spot`() {
        val preferred = Vec2(300f, 400f)
        val covering = horizontalStroke(400f)

        val spawned = GameState.spawnFree(world, listOf(covering), preferred)

        assertTrue(world.contains(spawned.position))
        assertTrue(abs(spawned.cy - 400f) >= Physics.CONTACT_DISTANCE - tolerance)
    }

    @Test
    fun `spawnFree clamps a preferred spot outside the world back inside`() {
        val spawned = GameState.spawnFree(world, emptyList(), Vec2(-50f, 5000f))

        assertEquals(Vec2(world.minX, world.maxY), spawned.position)
    }

    @Test
    fun `closestPointOnSegment clamps to the endpoints`() {
        val a = Vec2(0f, 0f)
        val b = Vec2(10f, 0f)

        assertEquals(b, closestPointOnSegment(Vec2(25f, 3f), a, b))
        assertEquals(a, closestPointOnSegment(Vec2(-25f, 3f), a, b))
        assertEquals(Vec2(4f, 0f), closestPointOnSegment(Vec2(4f, 9f), a, b))
    }
}
