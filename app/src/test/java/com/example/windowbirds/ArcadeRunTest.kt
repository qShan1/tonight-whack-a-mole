package com.example.windowbirds.content

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeRunTest {
    @Test
    fun fiveRoundsCanBeClearedAndTheBossNeedsThreeHits() {
        val run = ArcadeRun(Random(26))
        run.start()
        for (round in arcadeRounds.indices) {
            assertEquals(round, run.round)
            repeat(520) {
                if (run.phase == ArcadePhase.PLAYING) {
                    run.advance(30)
                    assertTrue(run.targets.count { it != null } <= 3)
                    for (hole in run.targets.indices) {
                        val target = run.targets[hole] ?: continue
                        if (target.cue.kind == PopKind.DECOY) continue
                        val strike = run.strike(hole).first()
                        if (strike.cracked) run.strike(hole)
                    }
                }
            }
            if (round < arcadeRounds.lastIndex) {
                assertEquals(ArcadePhase.DRAW, run.phase)
                val offers = run.snapshot().offers
                assertEquals(3, offers.toSet().size)
                run.choose(offers.first())
            }
        }
        assertEquals(ArcadePhase.WON, run.phase)
        assertEquals(3, run.snapshot().bossHits)
        assertEquals(5, run.snapshot().health)
    }

    @Test
    fun firstMoleWaitsAndRedrawCostsTwoTicketsAtMostOncePerRound() {
        val run = ArcadeRun(Random(42))
        run.start()
        repeat(200) { run.advance(30) }
        assertTrue(run.targets[1] != null)
        assertEquals(5, run.snapshot().health)
        repeat(500) {
            run.advance(30)
            run.targets.indices.filter { run.targets[it] != null }.forEach(run::strike)
        }
        assertEquals(ArcadePhase.DRAW, run.phase)
        run.choose(run.snapshot().offers.first())
        repeat(500) {
            run.advance(30)
            run.targets.indices.filter { run.targets[it] != null }.forEach(run::strike)
        }
        val tickets = run.snapshot().tickets
        assertTrue(run.snapshot().canRedraw)
        run.redraw()
        run.redraw()
        assertEquals(tickets - 2, run.snapshot().tickets)
        assertEquals(3, run.snapshot().offers.toSet().size)
    }
}
