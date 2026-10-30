package com.example.stickman.game

import kotlin.random.Random

object StarRules {
    const val STAR_RADIUS = 40f
    const val CATCH_DISTANCE = Physics.HALF_EXTENT + STAR_RADIUS
    const val MIN_SPAWN_DISTANCE = CATCH_DISTANCE * 2f
    const val MAX_PLACEMENT_ATTEMPTS = 32
}

data class StarHunt(val star: Vec2, val collected: Int) {
    companion object {
        fun begin(world: World, blob: Vec2, random: Random): StarHunt =
            StarHunt(star = world.randomSpotAwayFrom(blob, random), collected = 0)
    }
}

fun StarHunt.collectIfReached(blob: Vec2, world: World, random: Random): StarHunt {
    if (!blob.isWithin(StarRules.CATCH_DISTANCE, of = star)) return this
    return StarHunt(star = world.randomSpotAwayFrom(blob, random), collected = collected + 1)
}

private fun World.randomSpotAwayFrom(blob: Vec2, random: Random): Vec2 =
    generateSequence { randomSpot(random) }
        .take(StarRules.MAX_PLACEMENT_ATTEMPTS)
        .firstOrNull { !it.isWithin(StarRules.MIN_SPAWN_DISTANCE, of = blob) }
        ?: randomSpot(random)

private fun World.randomSpot(random: Random): Vec2 =
    Vec2(random.nextFloat(from = minX, until = maxX), random.nextFloat(from = minY, until = maxY))

private fun Random.nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)

private fun Vec2.isWithin(distance: Float, of: Vec2): Boolean =
    squaredDistance(this, of) <= distance * distance
