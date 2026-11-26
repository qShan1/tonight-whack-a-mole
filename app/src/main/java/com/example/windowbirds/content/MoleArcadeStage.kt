package com.example.windowbirds.content

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.windowbirds.R
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.cos
import kotlin.math.sin

private const val ArcadePanel = "arcade-panel"
private const val ArcadeMarquee = "arcade-marquee"
private val ArcadeCorners = RoundedCornerShape(30.dp)
private val DisplayBackground = Color(0xFF102B2A)
private val DisplayText = Color(0xFFFFEDD1)
private val DisplayAccent = Color(0xFFFFC75B)

private data class ArcadeOptions(val sound: Boolean, val reducedMotion: Boolean, val passthrough: Boolean)

@Composable
internal fun MoleArcadeStage() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("arcade-options", Context.MODE_PRIVATE) }
    var options by remember(preferences) {
        mutableStateOf(ArcadeOptions(
            sound = preferences.getBoolean("sound", true),
            reducedMotion = preferences.getBoolean("reduced-motion", false),
            passthrough = preferences.getBoolean("passthrough", false),
        ))
    }
    val audio = remember(context) { ArcadeAudio(context) }
    var bestScore by remember(preferences) { mutableStateOf(preferences.getInt("best-score", 0)) }
    SideEffect { audio.enabled = options.sound }
    DisposableEffect(audio) { onDispose { audio.close() } }
    var attempt by remember { mutableStateOf(0) }
    key(attempt) {
        MoleArcade(
            audio = audio,
            options = options,
            bestScore = bestScore,
            onScore = { score ->
                if (score > bestScore) {
                    bestScore = score
                    preferences.edit().putInt("best-score", score).apply()
                }
            },
            onOptionsChange = { updated ->
                audio.enabled = updated.sound
                options = updated
                preferences.edit().putBoolean("sound", updated.sound)
                    .putBoolean("reduced-motion", updated.reducedMotion)
                    .putBoolean("passthrough", updated.passthrough).apply()
            },
            onRestart = { attempt++ },
        )
    }
}

@Composable
private fun MoleArcade(
    audio: ArcadeAudio,
    options: ArcadeOptions,
    bestScore: Int,
    onScore: (Int) -> Unit,
    onOptionsChange: (ArcadeOptions) -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    val run = remember { ArcadeRun() }
    val scene = remember { MoleCabinet() }
    var hud by remember { mutableStateOf(run.snapshot()) }
    var settingsOpen by remember { mutableStateOf(false) }
    val currentOptions by rememberUpdatedState(options)

    DisposableEffect(scene) { onDispose { scene.close() } }
    SideEffect {
        scene.setPassthrough(options.passthrough)
        scene.setInputEnabled(!settingsOpen && hud.phase == ArcadePhase.PLAYING && hud.secondsToStart == 0)
    }
    LaunchedEffect(hud.phase) {
        if (hud.phase == ArcadePhase.WON || hud.phase == ArcadePhase.LOST) onScore(hud.score)
    }
    LaunchedEffect(scene) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = now - previous
            if (elapsed < 30_000_000L) continue
            previous = now
            if (settingsOpen) continue
            val delta = (elapsed / 1_000_000L).toInt().coerceAtMost(90)
            val phase = run.phase
            if (phase == ArcadePhase.PLAYING) {
                run.advance(delta)
                val updated = run.snapshot()
                if (updated != hud) hud = updated
                if (run.phase == ArcadePhase.DRAW) audio.reward()
                if (run.phase == ArcadePhase.LOST) audio.warning()
            }
            scene.update(run, hud, delta, currentOptions.reducedMotion)
        }
    }

    SpatialView(
        modifier = Modifier.pointerInput(scene, context) {
            detectSpatialTapGesture(context, TargetEntity.any { scene.indexOf(it) >= 0 }) { tap ->
                val index = tap.targetEntity?.let(scene::indexOf) ?: -1
                if (!settingsOpen && run.phase == ArcadePhase.PLAYING && index >= 0) {
                    val hits = run.strike(index)
                    if (hits.isNotEmpty()) {
                        scene.strike(hits)
                        hud = run.snapshot()
                        scene.update(run, hud, 0, currentOptions.reducedMotion)
                        audio.strike(hits.first())
                        if (run.phase == ArcadePhase.WON) audio.reward()
                    }
                }
            }
        },
        attachments = {
            AttachmentPanel(ArcadeMarquee) {
                Text(
                    "今晚打地鼠",
                    modifier = Modifier.width(385.dp).padding(vertical = 12.dp),
                    fontSize = 46.sp,
                    color = DisplayAccent,
                    textAlign = TextAlign.Center,
                )
            }
            AttachmentPanel(ArcadePanel) {
                Column(
                    Modifier.width(410.dp).clip(ArcadeCorners)
                        .background(DisplayBackground, ArcadeCorners).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (settingsOpen) {
                        Text("设置", fontSize = 34.sp, color = DisplayAccent)
                        if (hud.phase == ArcadePhase.PLAYING) {
                            Text("游戏已暂停", fontSize = 20.sp, color = DisplayText)
                        }
                        Button(onClick = { onOptionsChange(options.copy(sound = !options.sound)) }) {
                            Text(if (options.sound) "音效  开" else "音效  关")
                        }
                        Button(onClick = {
                            onOptionsChange(options.copy(reducedMotion = !options.reducedMotion))
                        }) {
                            Text(if (options.reducedMotion) "减少动效  开" else "减少动效  关")
                        }
                        Button(onClick = {
                            onOptionsChange(options.copy(passthrough = !options.passthrough))
                        }) {
                            Text(if (options.passthrough) "透视  开" else "透视  关")
                        }
                        Button(onClick = { settingsOpen = false }) {
                            Text(if (hud.phase == ArcadePhase.PLAYING) "继续游戏" else "返回")
                        }
                    } else {
                        when (hud.phase) {
                            ArcadePhase.READY -> {
                                Text("准备开台", fontSize = 34.sp, color = DisplayAccent)
                                    Text("瞄准洞口捏合。洞灯先亮，地鼠随后露头。", fontSize = 21.sp,
                                    color = DisplayText)
                                    if (bestScore > 0) Text("本机最高分  $bestScore", color = DisplayText)
                                Button(onClick = {
                                    run.start()
                                    audio.reward()
                                    hud = run.snapshot()
                                }) { Text("开一局") }
                            }
                            ArcadePhase.PLAYING -> {
                                Text("${hud.round + 1}/5  ${arcadeRounds[hud.round].name}", fontSize = 28.sp,
                                    color = DisplayAccent)
                                Text("耐久 ${hud.health}/5    连击 ${hud.streak}    奖券 ${hud.tickets}",
                                    fontSize = 20.sp, color = DisplayText)
                                    if (hud.secondsToStart > 0) {
                                        Text("${hud.secondsToStart} 秒后开轮", color = DisplayAccent)
                                    } else if (hud.round == arcadeRounds.lastIndex) {
                                        Text("鼠王  ${hud.bossHits} / 3  ·  等金灯，红灯是虚晃", color = DisplayText)
                                } else if (hud.round == 0 && hud.score == 0) {
                                    Text("第一只会等你敲中。", color = DisplayText)
                                } else {
                                    Text("本轮剩余 ${hud.remaining} 个目标", color = DisplayText)
                                }
                            }
                            ArcadePhase.DRAW -> {
                                Text("抽一张带走", fontSize = 30.sp, color = DisplayAccent)
                                Text("耐久 ${hud.health}/5  ·  剩 ${hud.tickets} 张奖券", fontSize = 20.sp,
                                    color = DisplayText)
                                val reveal = remember(hud.offers, options.reducedMotion) {
                                    mutableStateOf(if (options.reducedMotion) 1f else 0f)
                                }
                                LaunchedEffect(hud.offers, options.reducedMotion) {
                                    val start = withFrameNanos { it }
                                    while (reveal.value < 1f) {
                                        reveal.value = withFrameNanos {
                                            ((it - start) / 310_000_000f).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                                hud.offers.forEachIndexed { index, perk ->
                                    Button(modifier = Modifier.fillMaxWidth().graphicsLayer {
                                        val progress = ((reveal.value - index * 0.16f) / 0.68f).coerceIn(0f, 1f)
                                        alpha = progress
                                        rotationX = -30f * (1f - progress)
                                        translationY = 12.dp.toPx() * (1f - progress)
                                    }, onClick = {
                                        run.choose(perk)
                                        audio.reward()
                                        hud = run.snapshot()
                                    }) { Text("${perk.label}  ·  ${perk.detail}") }
                                }
                                if (hud.canRedraw) {
                                    Button(onClick = {
                                        run.redraw()
                                        audio.reward()
                                        hud = run.snapshot()
                                    }) { Text("花 2 张奖券重抽") }
                                }
                            }
                            ArcadePhase.WON, ArcadePhase.LOST -> {
                                val summary = run.summary()
                                Text(if (hud.phase == ArcadePhase.WON) "鼠王回洞了" else "这局结束了",
                                    fontSize = 32.sp, color = DisplayAccent)
                                Text("得分 ${hud.score}   ·   第 ${hud.round + 1} 轮   ·   耐久 ${hud.health}/5",
                                    fontSize = 20.sp, color = DisplayText)
                                    Text("本机最高分  ${maxOf(hud.score, bestScore)}", color = DisplayText)
                                Text("最高连击  ${summary.bestStreak}   ·   金鼠  ${summary.goldHits} 只",
                                    color = DisplayText)
                                if (summary.picks.isNotEmpty()) {
                                    Text("本局强化  ${summary.picks.joinToString(" / ") { it.label }}", color = DisplayText)
                                }
                                Button(onClick = onRestart) { Text("再来一局") }
                            }
                        }
                        Button(onClick = { settingsOpen = true }) { Text("设置") }
                    }
                }
            }
        },
        initial = { content, attachments ->
            content.addEntity(scene.root)
            attachments.entity(ArcadeMarquee)?.let {
                it.arcadeTransform.position = Vector3(-0.19f, 2.03f, -2.77f)
                it.arcadeTransform.scaleBy(2.1f)
                scene.root.addChild(it)
            }
            attachments.entity(ArcadePanel)?.let {
                it.arcadeTransform.position = Vector3(0.70f, 1.64f, -2.43f)
                it.arcadeTransform.eulerAngles = EulerAngles(pitch = -16f)
                it.arcadeTransform.scaleBy(1.66f)
                scene.root.addChild(it)
            }
        },
    )
}

private val Entity.arcadeTransform: TransformComponent
    get() = requireNotNull(components.get<TransformComponent>())

private class MoleCabinet {
    val root = Entity().apply { setName("mole-cabinet") }
    private val room = Entity().apply {
        setName("arcade-room")
        root.addChild(this)
    }

    private val dark = Color4(0.04f, 0.06f, 0.07f, 1f)
    private val navy = Color4(0.07f, 0.17f, 0.19f, 1f)
    private val teal = Color4(0.10f, 0.36f, 0.32f, 1f)
    private val orange = Color4(0.91f, 0.32f, 0.18f, 1f)
    private val brass = Color4(0.98f, 0.68f, 0.22f, 1f)
    private val cream = Color4(0.96f, 0.86f, 0.64f, 1f)
    private val fur = Color4(0.66f, 0.35f, 0.20f, 1f)
    private val lidColor = Color4(0.42f, 0.62f, 0.65f, 1f)
    private val materials = mutableMapOf<Color4, UnlitMaterial>()
    private val spheres = mutableMapOf<Float, MeshResource>()
    private val boxes = mutableMapOf<Triple<Float, Float, Float>, MeshResource>()
    private val holes: List<Hole>
    private val hammer: Entity
    private val flyingLid: Entity
    private val sparks: List<Entity>
    private val healthLights: List<Entity>
    private val roundLights: List<Entity>
    private val streakLights: List<Entity>
    private val roundTrims: List<List<Entity>>
    private val impactBars: List<Entity>
    private var displayedHud: ArcadeHud? = null
    private var lightMask = -1
    private var roundLightAge = 500
    private var streakLightAge = 500
    private var effectAge = 420
    private var effect: Strike? = null
    private var inputEnabled = false

    init {
        buildBooth()
        val roundColors = listOf(teal, brass, lidColor, Color4(0.70f, 0.43f, 0.76f, 1f), orange)
        roundTrims = roundColors.map { color ->
            listOf(-1f, 1f).map { side ->
                val x = -0.19f + side * 0.82f
                box(0.028f, 1.08f, 0.024f, color, Vector3(x, 1.37f, -2.832f))
                    .apply { enabled = false }
            }
        }
        box(1.43f, 0.25f, 0.92f, navy, Vector3(-0.19f, 0.93f, -2.47f))
        box(1.46f, 0.055f, 0.94f, cream, Vector3(-0.19f, 1.062f, -2.47f))
        box(1.38f, 0.044f, 0.86f, teal, Vector3(-0.19f, 1.100f, -2.47f))
        box(1.17f, 0.042f, 0.018f, orange, Vector3(-0.19f, 0.99f, -2.001f))
        box(0.41f, 0.072f, 0.021f, dark, Vector3(-0.19f, 0.905f, -1.993f))
        healthLights = List(5) { index ->
            val x = -0.19f + (index - 2) * 0.071f
            box(0.048f, 0.026f, 0.011f, teal, Vector3(x, 0.906f, -1.977f))
            box(0.048f, 0.026f, 0.011f, cream, Vector3(x, 0.906f, -1.968f))
        }
        for (side in listOf(-1f, 1f)) {
            box(0.17f, 0.79f, 0.70f, navy, Vector3(-0.19f + side * 0.49f, 0.423f, -2.48f))
            box(0.22f, 0.07f, 0.77f, dark, Vector3(-0.19f + side * 0.49f, 0.073f, -2.48f))
            for (index in 0..2) {
                box(0.067f, 0.018f, 0.014f, brass,
                    Vector3(-0.19f + side * (0.46f + index * 0.07f), 0.91f, -1.995f))
            }
        }
        holes = (0 until 6).map { index ->
            val x = -0.19f + (index % 3 - 1) * 0.405f + if (index < 3) -0.036f else 0.036f
            val z = if (index < 3) -2.27f else -2.66f
            hole(x, z, index)
        }
        hammer = Entity().also {
            root.addChild(it)
            box(0.25f, 0.042f, 0.043f, fur, Vector3(0.13f, 0f, 0f), it)
            box(0.12f, 0.12f, 0.115f, orange, Vector3(0f, 0f, 0f), it)
            box(0.022f, 0.112f, 0.105f, cream, Vector3(0.057f, 0f, 0f), it)
            it.enabled = false
        }
        flyingLid = box(0.205f, 0.040f, 0.16f, lidColor, Vector3(0f, 0f, 0f)).apply { enabled = false }
        sparks = List(5) {
            box(0.035f, 0.035f, 0.028f, if (it % 2 == 0) brass else cream,
                Vector3(0f, 0f, 0f)).apply { enabled = false }
        }
        roundLights = List(5) { index ->
            val x = -0.19f + (index - 2) * 0.145f
            box(0.100f, 0.027f, 0.020f, teal, Vector3(x, 1.818f, -2.793f))
            box(0.100f, 0.027f, 0.020f, brass, Vector3(x, 1.818f, -2.779f))
                .apply { enabled = false }
        }
        streakLights = listOf(-1f, 1f).map { side ->
            box(0.23f, 0.025f, 0.019f, brass, Vector3(-0.19f + side * 0.66f, 1.126f, -2.47f))
                .apply {
                    arcadeTransform.eulerAngles = EulerAngles(yaw = 90f)
                    enabled = false
                }
        }
        impactBars = List(4) { index ->
            val horizontal = index < 2
            box(if (horizontal) 0.14f else 0.018f, 0.013f,
                if (horizontal) 0.018f else 0.14f, brass, Vector3(0f, 0f, 0f))
                .apply { enabled = false }
        }
    }

    private fun buildBooth() {
        val wall = Color4(0.17f, 0.15f, 0.23f, 1f)
        val stripe = Color4(0.22f, 0.19f, 0.30f, 1f)
        val oxblood = Color4(0.39f, 0.14f, 0.21f, 1f)
        val rubber = Color4(0.12f, 0.13f, 0.17f, 1f)
        box(8.1f, 0.10f, 7.6f, rubber, Vector3(0f, -0.055f, -0.20f), room)
        box(8.1f, 0.12f, 7.6f, navy, Vector3(0f, 3.42f, -0.20f), room)
        box(8.1f, 3.4f, 0.10f, wall, Vector3(0f, 1.68f, -3.85f), room)
        box(8.1f, 3.4f, 0.10f, wall, Vector3(0f, 1.68f, 3.47f), room)
        box(8.1f, 0.54f, 0.045f, oxblood, Vector3(0f, 0.29f, -3.77f), room)
        box(8.1f, 0.034f, 0.044f, brass, Vector3(0f, 0.57f, -3.74f), room)
        box(8.1f, 0.040f, 0.052f, teal, Vector3(0f, 2.80f, 3.408f), room)
        for (side in listOf(-1f, 1f)) {
            box(0.10f, 3.4f, 7.6f, wall, Vector3(side * 3.97f, 1.68f, -0.20f), room)
            box(0.048f, 0.54f, 7.6f, oxblood, Vector3(side * 3.89f, 0.29f, -0.20f), room)
            box(0.044f, 0.033f, 7.6f, brass, Vector3(side * 3.86f, 0.57f, -0.20f), room)
            box(0.058f, 0.048f, 7.1f, teal, Vector3(side * 3.88f, 2.80f, -0.18f), room)
            for (index in -3..3) {
                box(0.03f, 2.18f, 0.057f, stripe, Vector3(side * 3.90f, 1.66f, index * 0.92f), room)
            }
        }
        for (index in -4..4) {
            box(0.065f, 2.7f, 0.018f, stripe, Vector3(index * 0.68f, 1.93f, -3.78f), room)
        }
        box(2.30f, 0.024f, 2.25f, oxblood, Vector3(-0.19f, 0.012f, -2.35f), room)
        box(2.21f, 0.020f, 2.16f, dark, Vector3(-0.19f, 0.024f, -2.35f), room)
        box(1.64f, 0.72f, 0.105f, navy, Vector3(-0.19f, 1.98f, -2.91f))
        box(1.51f, 0.48f, 0.053f, dark, Vector3(-0.19f, 2.017f, -2.82f))
        box(1.52f, 0.022f, 0.025f, brass, Vector3(-0.19f, 2.25f, -2.782f))
        box(1.52f, 0.017f, 0.023f, teal, Vector3(-0.19f, 1.776f, -2.792f))
        for (side in listOf(-1f, 1f)) {
            val columnX = -0.19f + side * 0.82f
            box(0.106f, 1.71f, 0.16f, navy, Vector3(columnX, 0.94f, -2.96f))
            box(0.023f, 1.08f, 0.030f, teal, Vector3(columnX, 1.37f, -2.854f))
            for (index in 0..2) {
                sphere(0.025f, if (index == 1) cream else brass,
                    Vector3(columnX, 1.96f + index * 0.095f, -2.798f), root)
            }
        }
        // Two fixed shelves give the booth a silhouette without adding animated scenery.
        for (index in 0..1) {
            val y = 1.42f + index * 0.36f
            box(0.53f, 0.047f, 0.27f, navy, Vector3(-1.49f, y, -3.27f), room)
            box(0.52f, 0.014f, 0.016f, teal, Vector3(-1.49f, y, -3.128f), room)
            for (prize in -1..1) {
                val x = -1.49f + prize * 0.15f
                sphere(0.057f, if (prize == 0) orange else cream, Vector3(x, y + 0.081f, -3.25f), room)
            }
        }
    }

    fun setPassthrough(enabled: Boolean) {
        // The Mixed stage keeps the cabinet visible when the opaque room is hidden.
        if (room.enabled == enabled) room.enabled = !enabled
    }

    private fun hole(x: Float, z: Float, index: Int): Hole {
        box(0.255f, 0.021f, 0.228f, dark, Vector3(x, 1.135f, z))
        val warning = box(0.152f, 0.021f, 0.030f, orange, Vector3(x, 1.139f, z + 0.122f))
        warning.enabled = false
        val bossWarning = box(0.152f, 0.021f, 0.030f, cream, Vector3(x, 1.139f, z + 0.122f))
            .apply { enabled = false }
        val cover = box(0.206f, 0.033f, 0.168f, navy, Vector3(x, 1.156f, z)).apply { enabled = false }
        val collider = Entity().apply {
            setName("hole-${index + 1}")
            arcadeTransform.position = Vector3(x, 1.188f, z)
            root.addChild(this)
            val shape = ShapeResource.createBox(Vector3(0.31f, 0.27f, 0.28f))
            val material = PhysicsMaterialResource()
            components.set(CollisionComponent(collisionShape = listOf(shape), physicsMaterial = material))
            shape.close()
            material.close()
        }
        box(0.294f, 0.018f, 0.264f, brass, Vector3(0f, -0.060f, 0f), collider)
        val target = Entity().also { root.addChild(it); it.enabled = false }
        val ordinary = sphere(0.096f, fur, Vector3(0f, 0f, 0f), target)
        val gold = sphere(0.096f, brass, Vector3(0f, 0f, 0f), target)
        val decoy = box(0.15f, 0.16f, 0.15f, cream, Vector3(0f, 0f, 0f), target)
        for (side in listOf(-1f, 1f)) {
            sphere(0.035f, fur, Vector3(side * 0.075f, 0.055f, 0f), target)
            sphere(0.014f, dark, Vector3(side * 0.032f, 0.024f, 0.087f), target)
        }
        sphere(0.025f, orange, Vector3(0f, -0.005f, 0.092f), target)
        val lid = box(0.213f, 0.035f, 0.155f, lidColor, Vector3(0f, 0.084f, 0f), target)
        val crown = Entity().also { target.addChild(it) }
        box(0.143f, 0.046f, 0.10f, brass, Vector3(0f, 0.099f, 0f), crown)
        for (tooth in -1..1) {
            box(0.029f, 0.040f, 0.035f, brass, Vector3(tooth * 0.047f, 0.132f, 0.027f), crown)
        }
        return Hole(x, z, collider, cover, warning, bossWarning, target, ordinary, gold, decoy, lid, crown)
    }

    fun indexOf(entity: Entity) = holes.indexOfFirst { it.collider == entity }

    fun setInputEnabled(enabled: Boolean) {
        if (inputEnabled == enabled) return
        inputEnabled = enabled
        for (hole in holes) {
            if (enabled) {
                hole.collider.components.set(InteractableComponent())
                hole.collider.components.set(HoverEffectComponent())
            } else {
                hole.collider.components.remove(HoverEffectComponent::class.java)
                hole.collider.components.remove(InteractableComponent::class.java)
            }
        }
    }

    fun strike(hits: List<Strike>) {
        effect = hits.first()
        effectAge = 0
        for (hit in hits) {
            if (hit.kind != null && !hit.cracked) holes[hit.hole].retreatMs = 120
        }
    }

    private fun updateCabinetLights(hud: ArcadeHud, delta: Int, reducedMotion: Boolean) {
        if (hud !== displayedHud) {
            val previous = displayedHud
            if (hud.phase == ArcadePhase.PLAYING &&
                (previous?.phase != ArcadePhase.PLAYING || previous.round != hud.round)
            ) roundLightAge = 0
            if (hud.streak > 0 && hud.streak % 4 == 0 && hud.streak != previous?.streak) streakLightAge = 0
            displayedHud = hud
            healthLights.forEachIndexed { index, light -> light.enabled = index < hud.health }
            if (previous == null || previous.round != hud.round || previous.phase != hud.phase) {
                roundTrims.forEachIndexed { index, lights ->
                    lights.forEach { it.enabled = index == hud.round && hud.phase == ArcadePhase.PLAYING }
                }
            }
        }
        roundLightAge = (roundLightAge + delta).coerceAtMost(500)
        streakLightAge = (streakLightAge + delta).coerceAtMost(500)
        val normalMask = if (hud.phase == ArcadePhase.READY) 0 else (1 shl (hud.round + 1)) - 1
        val sweep = hud.phase == ArcadePhase.PLAYING && roundLightAge < 400 && !reducedMotion
        val mask = if (sweep) normalMask or (1 shl (roundLightAge / 80)) else normalMask
        if (mask != lightMask) {
            lightMask = mask
            roundLights.forEachIndexed { index, light -> light.enabled = mask and (1 shl index) != 0 }
        }
        val comboVisible = hud.streak >= 4 && hud.phase == ArcadePhase.PLAYING
        val comboSweep = comboVisible && streakLightAge < 420 && !reducedMotion
        streakLights.forEachIndexed { index, light ->
            if (light.enabled != comboVisible) light.enabled = comboVisible
            if (comboSweep || comboVisible && streakLightAge - delta < 420) {
                val offset = if (comboSweep) (streakLightAge / 420f - 0.5f) * 0.55f else 0f
                light.arcadeTransform.position = Vector3(-0.85f + index * 1.32f, 1.126f, -2.47f + offset)
            }
        }
    }

    fun update(run: ArcadeRun, hud: ArcadeHud, delta: Int, reducedMotion: Boolean) {
        updateCabinetLights(hud, delta, reducedMotion)
        val activeEffect = effect
        if (activeEffect != null) effectAge += delta
        holes.forEachIndexed { index, hole ->
            val pop = if (run.phase == ArcadePhase.PLAYING) run.targets[index] else null
            val warning = if (run.phase == ArcadePhase.PLAYING && pop == null) run.warning(index) else null
            val boss = warning?.kind == PopKind.BOSS
            val warningRemaining = warning?.let { it.time - run.elapsed } ?: 0
            val showGold = boss && (reducedMotion || warningRemaining > 470 || warningRemaining in 120..330)
            val signal = when {
                warning == null -> DoorSignal.CLOSED
                showGold -> DoorSignal.GOLD
                boss -> DoorSignal.RATTLE
                else -> DoorSignal.RED
            }
            if (hole.signal != signal) {
                hole.signal = signal
                hole.warning.enabled = signal == DoorSignal.RED
                hole.bossWarning.enabled = signal == DoorSignal.GOLD
                hole.cover.enabled = signal != DoorSignal.CLOSED
            }
            if (warning != null) {
                val wobble = if (reducedMotion) 0f else sin(run.elapsed * 0.045f) * 10f
                hole.cover.arcadeTransform.eulerAngles = EulerAngles(roll = wobble)
            }
            hole.retreatMs = (hole.retreatMs - delta).coerceAtLeast(0)
            val retreating = pop == null && hole.retreatMs > 0
            val appearance = pop?.cue?.kind ?: if (retreating) hole.kind else null
            val cracked = pop?.hits == 1
            if (appearance != hole.kind || cracked != hole.cracked) {
                hole.kind = appearance
                hole.cracked = cracked
                hole.target.enabled = appearance != null
                hole.ordinary.enabled = appearance != PopKind.GOLD && appearance != PopKind.DECOY
                hole.gold.enabled = appearance == PopKind.GOLD
                hole.decoy.enabled = appearance == PopKind.DECOY
                hole.lid.enabled = appearance == PopKind.LID && !cracked
                hole.crown.enabled = appearance == PopKind.BOSS
            }
            if (pop != null) {
                val age = run.elapsed - pop.cue.time
                val visible = (age / 145f).coerceIn(0f, 1f)
                val eased = visible * (2f - visible)
                val timeLeft = pop.cue.time + run.duration(pop.cue) - run.elapsed
                val sink = (timeLeft / 125f).coerceIn(0f, 1f)
                val height = 1.07f + 0.168f * eased * sink
                hole.lastHeight = height
                hole.target.arcadeTransform.position = Vector3(hole.x, height, hole.z)
            } else if (retreating) {
                val height = 1.07f + (hole.lastHeight - 1.07f) * (hole.retreatMs / 120f)
                hole.target.arcadeTransform.position = Vector3(hole.x, height, hole.z)
            }
        }
        if (activeEffect == null) return
        if (effectAge >= 380) {
            hammer.enabled = false
            flyingLid.enabled = false
            sparks.forEach { it.enabled = false }
            impactBars.forEach { it.enabled = false }
            effect = null
            return
        }
        val hole = holes[activeEffect.hole]
        val progress = effectAge / 380f
        val rebound = sin((effectAge / 185f).coerceIn(0f, 1f) * 1.5708f)
        hammer.enabled = effectAge < 185
        if (hammer.enabled) {
            hammer.arcadeTransform.position = Vector3(hole.x, 1.24f + rebound * 0.26f, hole.z)
            hammer.arcadeTransform.eulerAngles = EulerAngles(roll = -16f + rebound * 57f)
        }
        flyingLid.enabled = activeEffect.cracked && !reducedMotion
        if (flyingLid.enabled) {
            flyingLid.arcadeTransform.position = Vector3(hole.x + 0.24f * progress,
                1.28f + 0.36f * sin(progress * 3.14159f), hole.z)
            flyingLid.arcadeTransform.eulerAngles = EulerAngles(roll = progress * 290f)
        }
        val impact = progress
        for ((index, spark) in sparks.withIndex()) {
            spark.enabled = !reducedMotion && activeEffect.kind != null && effectAge < 220
            if (spark.enabled) {
                val angle = index * 1.256637f
                spark.arcadeTransform.position = Vector3(hole.x + cos(angle) * impact * 0.37f,
                    1.24f + impact * 0.22f, hole.z + sin(angle) * impact * 0.33f)
                spark.arcadeTransform.eulerAngles = EulerAngles(roll = index * 29f + progress * 120f)
            }
        }
        val showImpact = !reducedMotion && activeEffect.kind != null && effectAge < 220
        for ((index, bar) in impactBars.withIndex()) {
            if (bar.enabled != showImpact) bar.enabled = showImpact
            if (showImpact) {
                val spread = 0.14f + effectAge * 0.00035f
                val side = if (index % 2 == 0) -1f else 1f
                bar.arcadeTransform.position = if (index < 2) Vector3(hole.x, 1.153f, hole.z + side * spread)
                    else Vector3(hole.x + side * spread, 1.153f, hole.z)
            }
        }
    }

    fun close() {
        root.destroy(true)
        materials.values.forEach { it.close() }
        boxes.values.forEach { it.close() }
        spheres.values.forEach { it.close() }
    }

    private fun sphere(radius: Float, color: Color4, position: Vector3, parent: Entity) =
        model(spheres.getOrPut(radius) { MeshResource.createSphere(radius) }, color, position, parent)

    private fun box(
        width: Float, height: Float, depth: Float, color: Color4, position: Vector3, parent: Entity = root,
    ): Entity {
        val mesh = boxes.getOrPut(Triple(width, height, depth)) {
            MeshResource.createBox(Vector3(width, height, depth), minOf(width, height, depth) * 0.17f)
        }
        return model(mesh, color, position, parent)
    }

    private fun model(mesh: MeshResource, color: Color4, position: Vector3, parent: Entity): Entity {
        val material = materials.getOrPut(color) {
            UnlitMaterial.create(BlendingMode.OPAQUE).apply { setBaseColor(color) }
        }
        return ModelEntity(mesh, material).also {
            it.arcadeTransform.position = position
            parent.addChild(it)
        }
    }
}

private enum class DoorSignal { CLOSED, RED, RATTLE, GOLD }

private class Hole(
    val x: Float, val z: Float, val collider: Entity, val cover: Entity, val warning: Entity,
    val bossWarning: Entity,
    val target: Entity, val ordinary: Entity, val gold: Entity, val decoy: Entity,
    val lid: Entity, val crown: Entity,
) {
    var kind: PopKind? = null
    var cracked = false
    var signal = DoorSignal.CLOSED
    var retreatMs = 0
    var lastHeight = 1.07f
}

private class ArcadeAudio(context: Context) {
    var enabled = true
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val hit = pool.load(context, R.raw.arcade_hit, 1)
    private val lid = pool.load(context, R.raw.arcade_lid, 1)
    private val prize = pool.load(context, R.raw.arcade_prize, 1)
    private val miss = pool.load(context, R.raw.arcade_miss, 1)

    fun strike(strike: Strike) {
        when {
            strike.cracked -> play(lid)
            strike.kind == PopKind.GOLD -> play(prize)
            strike.kind == PopKind.DECOY || strike.kind == null -> play(miss)
            else -> play(hit)
        }
    }

    fun reward() { play(prize) }
    fun warning() { play(miss) }

    private fun play(sample: Int) {
        if (enabled) pool.play(sample, 0.68f, 0.68f, 1, 0, 1f)
    }

    fun close() { pool.release() }
}
