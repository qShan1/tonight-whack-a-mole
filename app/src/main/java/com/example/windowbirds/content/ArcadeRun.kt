package com.example.windowbirds.content

import kotlin.random.Random

internal enum class PopKind { MOUSE, GOLD, LID, DECOY, FEINT, BOSS }
internal enum class ArcadePhase { READY, PLAYING, DRAW, WON, LOST }

internal enum class ArcadePerk(val label: String, val detail: String) {
    LONG_HANDLE("连敲", "每轮第一槌也敲中同排最近的地鼠"),
    LID_BREAKER("拆盖手", "锅盖鼠一槌击退"),
    SLOW("慢半拍", "地鼠多露头 0.35 秒"),
    DOUBLE_GOLD("金票加倍", "金鼠额外给 2 张票"),
    PATCH("补两格", "恢复 2 点耐久"),
    CUSHION("软垫", "每轮抵消第一次漏鼠扣血"),
    STREAK("熟手", "连击每满 3 只给 1 张票"),
    BONUS("送两张", "立刻领 2 张奖券"),
    WOOD_GUARD("护腕", "敲中木偶只断连击"),
}

internal data class PopCue(val time: Int, val hole: Int, val kind: PopKind, val duration: Int = 1450)
internal data class ArcadeRound(val name: String, val cues: List<PopCue>)

internal val arcadeRounds = listOf(
    ArcadeRound("热身", listOf(
        PopCue(0, 1, PopKind.MOUSE), PopCue(2200, 3, PopKind.MOUSE),
        PopCue(4400, 5, PopKind.MOUSE), PopCue(6500, 0, PopKind.MOUSE),
        PopCue(8600, 4, PopKind.MOUSE), PopCue(10700, 2, PopKind.MOUSE),
    )),
    ArcadeRound("抢金", listOf(
        PopCue(0, 2, PopKind.MOUSE), PopCue(1450, 4, PopKind.GOLD, 1150),
        PopCue(1700, 0, PopKind.MOUSE), PopCue(3300, 5, PopKind.MOUSE),
        PopCue(3550, 3, PopKind.GOLD, 1150), PopCue(5100, 1, PopKind.MOUSE),
        PopCue(6950, 3, PopKind.MOUSE), PopCue(7200, 4, PopKind.GOLD, 1150),
        PopCue(8800, 2, PopKind.MOUSE),
    )),
    ArcadeRound("敲锅盖", listOf(
        PopCue(0, 3, PopKind.LID, 1800), PopCue(650, 0, PopKind.GOLD, 850),
        PopCue(2550, 1, PopKind.MOUSE), PopCue(2800, 5, PopKind.LID, 1700),
        PopCue(4900, 4, PopKind.LID, 1750), PopCue(5520, 1, PopKind.GOLD, 850),
        PopCue(7200, 2, PopKind.MOUSE), PopCue(8850, 5, PopKind.LID, 1650),
        PopCue(9380, 2, PopKind.GOLD, 850),
    )),
    ArcadeRound("别敲木偶", listOf(
        PopCue(0, 1, PopKind.DECOY), PopCue(250, 3, PopKind.MOUSE),
        PopCue(1750, 5, PopKind.LID, 1800), PopCue(1950, 2, PopKind.DECOY),
        PopCue(3950, 0, PopKind.MOUSE), PopCue(4100, 1, PopKind.GOLD, 1150),
        PopCue(4250, 4, PopKind.DECOY), PopCue(5900, 3, PopKind.LID, 1800),
        PopCue(6150, 4, PopKind.MOUSE), PopCue(8150, 2, PopKind.DECOY),
        PopCue(8250, 5, PopKind.MOUSE), PopCue(8500, 0, PopKind.GOLD, 1150),
        PopCue(10350, 1, PopKind.LID, 1800),
    )),
    ArcadeRound("鼠王登场", listOf(
        PopCue(0, 1, PopKind.FEINT), PopCue(650, 3, PopKind.FEINT),
        PopCue(1850, 5, PopKind.BOSS, 1350), PopCue(3240, 1, PopKind.FEINT),
        PopCue(3900, 0, PopKind.BOSS, 1260), PopCue(5430, 4, PopKind.FEINT),
        PopCue(6150, 2, PopKind.BOSS, 1180), PopCue(7670, 0, PopKind.FEINT),
        PopCue(8250, 3, PopKind.BOSS, 1160), PopCue(10350, 4, PopKind.BOSS, 1160),
    )),
)

internal data class ActivePop(val cue: PopCue, var hits: Int = 0)
internal data class Strike(val hole: Int, val kind: PopKind?, val cracked: Boolean = false)
internal data class RunSummary(val bestStreak: Int, val goldHits: Int, val picks: List<ArcadePerk>)
internal data class ArcadeHud(
    val phase: ArcadePhase, val round: Int, val health: Int, val streak: Int,
    val tickets: Int, val score: Int, val bossHits: Int, val remaining: Int,
    val offers: List<ArcadePerk>, val canRedraw: Boolean, val secondsToStart: Int,
)

internal class ArcadeRun(private val random: Random = Random.Default) {
    var phase = ArcadePhase.READY
        private set
    var round = 0
        private set
    var elapsed = 0
        private set
    val targets = arrayOfNulls<ActivePop>(6)
    private var cues = emptyList<PopCue>()
    private var cursor = 0
    private var health = 5
    private var streak = 0
    private var bestStreak = 0
    private var goldHits = 0
    private var picks = emptyList<ArcadePerk>()
    private var tickets = 0
    private var score = 0
    private var bossHits = 0
    private var offers = emptyList<ArcadePerk>()
    private val perks = mutableSetOf<ArcadePerk>()
    private var redrawn = false
    private var shielded = false
    private var usedChain = false

    fun snapshot() = ArcadeHud(phase, round, health, streak, tickets, score, bossHits,
        cues.size - cursor + targets.count { it != null }, offers, tickets >= 2 && !redrawn,
        (-elapsed + 999).coerceAtLeast(0) / 1000)

    fun summary() = RunSummary(bestStreak, goldHits, picks)

    fun start() {
        if (phase == ArcadePhase.READY) startRound()
    }

    private fun startRound() {
        val mirror = round != 0 && random.nextBoolean()
        cues = arcadeRounds[round].cues.map { cue ->
            if (!mirror) cue else cue.copy(hole = cue.hole / 3 * 3 + 2 - cue.hole % 3)
        }
        elapsed = -2000
        cursor = 0
        shielded = ArcadePerk.CUSHION in perks
        usedChain = false
        phase = ArcadePhase.PLAYING
    }

    fun duration(cue: PopCue): Int =
        cue.duration + if (ArcadePerk.SLOW in perks && cue.kind != PopKind.DECOY) 350 else 0

    fun warning(hole: Int): PopCue? {
        for (index in cursor until cues.size) {
            val cue = cues[index]
            if (cue.time > elapsed + 650) break
            val lead = if (cue.kind == PopKind.BOSS) 650 else 300
            if (cue.hole == hole && cue.time <= elapsed + lead) return cue
        }
        return null
    }

    fun advance(delta: Int) {
        if (phase != ArcadePhase.PLAYING) return
        if (round == 0 && cursor == 1 && score == 0 && elapsed >= 145) return
        elapsed += delta
        targets.forEachIndexed { hole, target ->
            if (target != null && elapsed >= target.cue.time + duration(target.cue)) {
                targets[hole] = null
                when (target.cue.kind) {
                    PopKind.MOUSE, PopKind.LID, PopKind.BOSS -> miss()
                    PopKind.GOLD, PopKind.DECOY, PopKind.FEINT -> Unit
                }
            }
        }
        if (health == 0) {
            phase = ArcadePhase.LOST
            return
        }
        while (cursor < cues.size && cues[cursor].time <= elapsed) {
            val cue = cues[cursor++]
            if (cue.kind != PopKind.FEINT) targets[cue.hole] = ActivePop(cue)
        }
        if (cursor == cues.size && targets.all { it == null }) {
            if (round == arcadeRounds.lastIndex) {
                phase = ArcadePhase.LOST
            } else {
                redrawn = false
                offers = draw(emptyList())
                phase = ArcadePhase.DRAW
            }
        }
    }

    private fun miss() {
        streak = 0
        if (shielded) shielded = false else damage()
    }

    private fun damage() {
        health = (health - 1).coerceAtLeast(0)
    }

    fun strike(hole: Int): List<Strike> {
        if (phase != ArcadePhase.PLAYING || elapsed < 0 || hole !in targets.indices) return emptyList()
        val target = targets[hole]
        val strikes = mutableListOf(resolve(hole))
        if (target != null && target.cue.kind != PopKind.DECOY &&
            !usedChain && ArcadePerk.LONG_HANDLE in perks && phase == ArcadePhase.PLAYING
        ) {
            usedChain = true
            val neighbor = targets.indices.filter { other ->
                other != hole && other / 3 == hole / 3 &&
                    targets[other]?.cue?.kind?.let { it != PopKind.DECOY } == true
            }.minByOrNull { kotlin.math.abs(it - hole) }
            if (neighbor != null) strikes += resolve(neighbor)
        }
        return strikes
    }

    private fun resolve(hole: Int): Strike {
        val target = targets[hole]
        if (target == null) {
            streak = 0
            return Strike(hole, null)
        }
        val kind = target.cue.kind
        if (kind == PopKind.LID && target.hits == 0 && ArcadePerk.LID_BREAKER !in perks) {
            target.hits++
            return Strike(hole, kind, cracked = true)
        }
        targets[hole] = null
        if (round == 0 && score == 0) elapsed = maxOf(0, elapsed)
        if (kind == PopKind.DECOY) {
            streak = 0
            if (ArcadePerk.WOOD_GUARD !in perks) damage()
            if (health == 0) phase = ArcadePhase.LOST
        } else {
            streak++
            bestStreak = maxOf(bestStreak, streak)
            score += if (kind == PopKind.GOLD) 2 else 1
            val threshold = if (ArcadePerk.STREAK in perks) 3 else 4
            if (streak % threshold == 0) tickets++
            if (kind == PopKind.GOLD) {
                goldHits++
                tickets += if (ArcadePerk.DOUBLE_GOLD in perks) 2 else 1
            }
            if (kind == PopKind.BOSS && ++bossHits == 3) phase = ArcadePhase.WON
        }
        return Strike(hole, kind)
    }

    private fun draw(previous: List<ArcadePerk>): List<ArcadePerk> {
        val pool = ArcadePerk.entries.filter {
            it !in perks && (it != ArcadePerk.PATCH || health < 5)
        }
        return (pool.filterNot { it in previous }.shuffled(random) +
            pool.filter { it in previous }.shuffled(random)).take(3)
    }

    fun redraw() {
        if (phase != ArcadePhase.DRAW || redrawn || tickets < 2) return
        tickets -= 2
        redrawn = true
        offers = draw(offers)
    }

    fun choose(perk: ArcadePerk) {
        if (phase != ArcadePhase.DRAW || perk !in offers) return
        when (perk) {
            ArcadePerk.PATCH -> health = (health + 2).coerceAtMost(5)
            ArcadePerk.BONUS -> tickets += 2
            else -> perks += perk
        }
        picks = picks + perk
        offers = emptyList()
        round++
        startRound()
    }
}
