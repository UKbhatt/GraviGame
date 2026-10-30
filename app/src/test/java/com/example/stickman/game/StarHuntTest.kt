package com.example.stickman.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class StarHuntTest {

    private val world = World(width = 1000f, height = 2000f)
    private val blob = Vec2(500f, 1000f)

    private fun distance(a: Vec2, b: Vec2) = sqrt(squaredDistance(a, b))

    @Test
    fun `begin places the star inside the world and out of the blob's reach`() {
        val hunt = StarHunt.begin(world, blob, Random(seed = 1))

        assertEquals(0, hunt.collected)
        assertTrue(world.contains(hunt.star))
        assertTrue(distance(blob, hunt.star) > StarRules.CATCH_DISTANCE)
    }

    @Test
    fun `blob far from the star leaves the hunt unchanged`() {
        val hunt = StarHunt(star = Vec2(200f, 200f), collected = 3)

        assertEquals(hunt, hunt.collectIfReached(blob, world, Random(seed = 1)))
    }

    @Test
    fun `blob reaching the star scores it and a new star appears elsewhere`() {
        val star = Vec2(blob.x + StarRules.CATCH_DISTANCE - 1f, blob.y)
        val hunt = StarHunt(star = star, collected = 3)

        val next = hunt.collectIfReached(blob, world, Random(seed = 1))

        assertEquals(4, next.collected)
        assertNotEquals(star, next.star)
        assertTrue(world.contains(next.star))
        assertTrue(distance(blob, next.star) > StarRules.CATCH_DISTANCE)
    }

    @Test
    fun `a new star is never placed within reach of the blob`() {
        val random = Random(seed = 42)
        var hunt = StarHunt.begin(world, blob, random)

        repeat(500) {
            val blobOnStar = hunt.star
            hunt = hunt.collectIfReached(blobOnStar, world, random)
            assertTrue(distance(blobOnStar, hunt.star) > StarRules.CATCH_DISTANCE)
        }
        assertEquals(500, hunt.collected)
    }
}
