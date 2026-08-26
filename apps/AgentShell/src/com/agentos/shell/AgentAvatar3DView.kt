package com.agentos.shell

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AgentAvatarView(
    avatar: AgentAvatar,
    expression: AvatarExpression,
    modifier: Modifier = Modifier,
    performance: AvatarPerformance = AvatarPerformance(),
) {
    AndroidView(
        factory = { AvatarSurfaceView(it) },
        modifier = modifier.semantics {
            contentDescription = "${avatar.name}，可旋转的 3D 角色，${expression.label}表情"
        },
        update = { it.updateAvatar(avatar, expression, performance) },
    )
}

private class AvatarSurfaceView(context: Context) : GLSurfaceView(context) {
    private val avatarRenderer = AvatarRenderer()
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            avatarRenderer.zoom = (avatarRenderer.zoom / detector.scaleFactor).coerceIn(3.8f, 7.2f)
            requestRender()
            return true
        }
    })
    private var lastX = 0f
    private val animationTick = object : Runnable {
        override fun run() {
            requestRender()
            postDelayed(this, FRAME_DELAY_MS)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(avatarRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        preserveEGLContextOnPause = true
    }

    fun updateAvatar(avatar: AgentAvatar, expression: AvatarExpression, performance: AvatarPerformance) {
        avatarRenderer.avatar = avatar
        avatarRenderer.expression = expression
        avatarRenderer.performance = performance.normalized()
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastX = event.x
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress) {
                avatarRenderer.yaw += (event.x - lastX) * 0.35f
                lastX = event.x
                requestRender()
            }
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
        removeCallbacks(animationTick)
        post(animationTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animationTick)
        onPause()
        super.onDetachedFromWindow()
    }

    private companion object { const val FRAME_DELAY_MS = 33L }
}

private class AvatarRenderer : GLSurfaceView.Renderer {
    @Volatile var avatar = AgentAvatar()
    @Volatile var expression = AvatarExpression.NEUTRAL
    @Volatile var performance = AvatarPerformance()
    @Volatile var yaw = -12f
    @Volatile var zoom = 5.3f

    private lateinit var mesh: SphereMesh
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var glossHandle = 0
    private var glowHandle = 0
    private var alphaHandle = 0
    private var timeHandle = 0
    private var deformHandle = 0
    private var fieldProgram = 0
    private var fieldPositionHandle = 0
    private var fieldMvpHandle = 0
    private var fieldColorHandle = 0
    private var fieldPointSizeHandle = 0
    private var fieldRoundHandle = 0
    private lateinit var thoughtField: ThoughtFieldMesh
    private var frameSeconds = 0f
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.024f, 0.063f, 0.078f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        glossHandle = GLES20.glGetUniformLocation(program, "uGloss")
        glowHandle = GLES20.glGetUniformLocation(program, "uGlow")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        deformHandle = GLES20.glGetUniformLocation(program, "uDeform")
        fieldProgram = createProgram(FIELD_VERTEX_SHADER, FIELD_FRAGMENT_SHADER)
        fieldPositionHandle = GLES20.glGetAttribLocation(fieldProgram, "aPosition")
        fieldMvpHandle = GLES20.glGetUniformLocation(fieldProgram, "uMvp")
        fieldColorHandle = GLES20.glGetUniformLocation(fieldProgram, "uColor")
        fieldPointSizeHandle = GLES20.glGetUniformLocation(fieldProgram, "uPointSize")
        fieldRoundHandle = GLES20.glGetUniformLocation(fieldProgram, "uRound")
        mesh = SphereMesh(24, 16)
        thoughtField = ThoughtFieldMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 36f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 0.15f, zoom, 0f, 0.05f, 0f, 0f, 1f, 0f)
        drawAvatar(avatar, expression, performance)
    }

    private fun drawAvatar(value: AgentAvatar, mood: AvatarExpression, direction: AvatarPerformance) {
        val seconds = SystemClock.uptimeMillis() / 1_000f * direction.tempo
        frameSeconds = seconds
        val strength = direction.intensity
        val breath = sin(seconds * 2.1f) * 0.018f
        val idleSway = sin(seconds * 0.72f) * 0.018f
        val gestureWave = if (direction.gesture == AvatarGesture.WAVE) sin(seconds * 8f) * 18f * strength else 0f
        val gestureNod = if (direction.gesture == AvatarGesture.NOD) sin(seconds * 5.5f) * 0.06f * strength else 0f
        val talkBeat = if (direction.gesture == AvatarGesture.TALK) sin(seconds * 3.8f) * 8f * strength else 0f
        val bodyX = idleSway + if (direction.gesture == AvatarGesture.CELEBRATE) sin(seconds * 3f) * 0.06f else 0f
        if (value.styleFamily == AvatarStyleFamily.SYSTEM) {
            drawThoughtFieldAvatar(value, mood, direction, seconds, breath, bodyX)
            return
        }
        val skin = value.skinTone.argb.rgb()
        val hair = value.hairColor.argb.rgb()
        val outfit = value.outfitColor.argb.rgb()
        val ink = 0xFF172126.rgb()
        val accent = when (value.styleFamily) {
            AvatarStyleFamily.SYSTEM -> 0xFF68F5CE.rgb()
            AvatarStyleFamily.CYBER -> 0xFF68F5CE.rgb()
            AvatarStyleFamily.FANTASY -> 0xFFC79BFF.rgb()
            AvatarStyleFamily.ANIME -> 0xFF84AFFF.rgb()
            AvatarStyleFamily.REALISTIC -> 0xFFFFC66D.rgb()
            AvatarStyleFamily.SOFT -> 0xFFA8F4DF.rgb()
        }
        val gloss = when (value.material) {
            AvatarMaterial.MATTE -> 0.12f
            AvatarMaterial.GLOSS -> 0.55f
            AvatarMaterial.METAL -> 0.82f
            AvatarMaterial.HOLOGRAM -> 0.68f
        }
        val glow = if (value.material == AvatarMaterial.HOLOGRAM) 0.22f + value.glow * 0.5f else value.glow * 0.18f
        val bodyY = -0.72f - value.bodyHeight * 0.12f + breath
        val shoulder = 0.72f + value.shoulderWidth * 0.32f
        val head = 0.73f + value.headScale * 0.18f
        val faceX = when (value.faceShape) {
            AvatarFaceShape.ROUND -> 1.02f
            AvatarFaceShape.OVAL -> 0.94f
            AvatarFaceShape.HEART -> 0.98f
            AvatarFaceShape.SQUARE -> 1.07f
        } * (0.92f + value.faceWidth * 0.15f)
        val faceY = when (value.faceShape) {
            AvatarFaceShape.ROUND -> 1f
            AvatarFaceShape.OVAL -> 1.12f
            AvatarFaceShape.HEART -> 1.08f
            AvatarFaceShape.SQUARE -> 1.02f
        }

        // Torso, neck, shoulders and arms use one batched sphere mesh with different transforms.
        val leftArm = when (direction.gesture) {
            AvatarGesture.CELEBRATE -> 128f
            AvatarGesture.EXPLAIN -> -22f - talkBeat
            AvatarGesture.COMFORT -> -38f
            else -> -4f + talkBeat * 0.35f
        }
        val rightArm = when (direction.gesture) {
            AvatarGesture.WAVE -> -132f + gestureWave
            AvatarGesture.CELEBRATE -> -128f
            AvatarGesture.POINT -> 72f
            AvatarGesture.EXPLAIN -> 24f + talkBeat
            AvatarGesture.COMFORT -> 38f
            else -> 4f - talkBeat * 0.35f
        }
        drawPart(bodyX, bodyY, 0f, shoulder, 0.92f + value.bodyHeight * 0.2f, 0.42f, outfit, gloss, glow)
        drawPart(bodyX - shoulder * 0.82f, bodyY - 0.02f, 0f, 0.27f, 0.72f, 0.27f,
            outfit, gloss, glow, leftArm)
        drawPart(bodyX + shoulder * 0.82f, bodyY - 0.02f, 0f, 0.27f, 0.72f, 0.27f,
            outfit, gloss, glow, rightArm)
        drawPart(bodyX, -0.02f + breath, 0f, 0.24f, 0.33f, 0.24f, skin, 0.18f, 0f)
        if (value.outfitStyle == AvatarOutfitStyle.ARMOR) {
            drawPart(0f, bodyY + 0.16f, 0.38f, shoulder * 0.88f, 0.38f, 0.12f, accent, 0.85f, glow)
        } else if (value.outfitStyle == AvatarOutfitStyle.ROBE) {
            drawPart(0f, bodyY - 0.35f, 0f, shoulder * 0.94f, 0.86f, 0.38f, outfit, gloss, glow)
        } else if (value.outfitStyle == AvatarOutfitStyle.SUIT) {
            drawPart(-0.16f, bodyY + 0.18f, 0.39f, 0.08f, 0.52f, 0.06f, accent, gloss, glow)
            drawPart(0.16f, bodyY + 0.18f, 0.39f, 0.08f, 0.52f, 0.06f, accent, gloss, glow)
        }

        val headY = 0.62f + breath * 0.7f + gestureNod
        drawPart(bodyX, headY, 0f, head * faceX, head * faceY, head * 0.9f, skin, 0.22f, 0f)
        drawPart(bodyX, headY - 0.11f, head * 0.83f, 0.09f, 0.13f, 0.12f, skin, 0.2f, 0f)

        val eyeY = headY + 0.1f + direction.gazeY * 0.025f
        val eyeGap = 0.24f + value.eyeSpacing * 0.09f
        val eyeSize = 0.065f + value.eyeSize * 0.045f
        val blink = (SystemClock.uptimeMillis() % 4_600L) in 0L..115L
        val eyeScaleY = when {
            blink -> 0.08f
            mood == AvatarExpression.HAPPY || mood == AvatarExpression.SLEEPY -> 0.2f
            value.eyeStyle == AvatarEyeStyle.CALM || value.eyeStyle == AvatarEyeStyle.SHARP -> 0.55f
            else -> 1f
        }
        val eyeZ = head * 0.84f
        val gazeX = direction.gazeX * 0.035f
        drawPart(bodyX - eyeGap + gazeX, eyeY, eyeZ, eyeSize, eyeSize * eyeScaleY, 0.055f, ink, 0.1f, 0f)
        drawPart(bodyX + eyeGap + gazeX, eyeY, eyeZ, eyeSize, eyeSize * eyeScaleY, 0.055f, ink, 0.1f, 0f)
        if (value.eyeStyle == AvatarEyeStyle.BRIGHT && eyeScaleY > 0.5f) {
            drawPart(bodyX - eyeGap - eyeSize * 0.2f + gazeX, eyeY + eyeSize * 0.2f, eyeZ + 0.054f,
                eyeSize * 0.22f, eyeSize * 0.22f, 0.018f, floatArrayOf(1f, 1f, 1f), 0.8f, glow)
            drawPart(bodyX + eyeGap - eyeSize * 0.2f + gazeX, eyeY + eyeSize * 0.2f, eyeZ + 0.054f,
                eyeSize * 0.22f, eyeSize * 0.22f, 0.018f, floatArrayOf(1f, 1f, 1f), 0.8f, glow)
        }

        val mouthWidth = 0.17f + value.mouthWidth * 0.13f
        val mouthHeight = when (mood) {
            AvatarExpression.SPEAKING -> 0.06f + abs(sin(SystemClock.uptimeMillis() / 95f)) * 0.1f
            AvatarExpression.SURPRISED -> 0.12f
            AvatarExpression.HAPPY, AvatarExpression.LISTENING -> 0.065f
            else -> 0.035f
        }
        drawPart(bodyX, headY - 0.24f, eyeZ + 0.02f, mouthWidth, mouthHeight, 0.035f,
            if (mood == AvatarExpression.SPEAKING) floatArrayOf(0.48f, 0.12f, 0.16f) else ink, 0.12f, 0f)

        drawHair(value, hair, head, gloss, glow, bodyX, headY - 0.62f)
        drawAccessory(value.accessory, accent, head, gloss, glow, bodyX, headY - 0.62f)
    }

    /**
     * AgentOS' native identity is a deforming thought field, never a fixed mascot body.
     * Facial marks and gesture limbs condense only when the current interaction needs them.
     */
    private fun drawThoughtFieldAvatar(
        value: AgentAvatar,
        mood: AvatarExpression,
        direction: AvatarPerformance,
        seconds: Float,
        breath: Float,
        bodyX: Float,
    ) {
        val glass = 0xFF091217.rgb()
        val glassLight = 0xFF1B2A30.rgb()
        val amber = 0xFFFFB45B.rgb()
        val warmWhite = 0xFFFFF1D7.rgb()
        val memory = when (mood) {
            AvatarExpression.LISTENING -> 0xFF78F4D3.rgb()
            AvatarExpression.THINKING -> 0xFF8FB6FF.rgb()
            AvatarExpression.CONCERNED -> 0xFFFF8B82.rgb()
            AvatarExpression.HAPPY -> 0xFFFFD37C.rgb()
            else -> amber
        }
        val glow = 0.48f + value.glow * 0.48f
        val strength = direction.intensity
        val pulse = 1f + sin(seconds * 2.2f) * (0.028f + strength * 0.018f)
        val focus = if (direction.gesture == AvatarGesture.THINK) 0.08f else 0f
        val knotWidth = 0.9f + value.faceWidth * 0.2f
        val coreScale = 0.85f + value.headScale * 0.3f

        drawThoughtField(bodyX, seconds, strength, value.shoulderWidth, value.bodyHeight, memory)

        // Black-glass lobes overlap into one asymmetric, breathing consciousness knot.
        drawPart(bodyX - 0.24f, 0.31f + breath, -0.03f,
            0.56f * pulse * knotWidth, 0.86f, 0.4f, glass, 0.94f, glow, -17f, 0.78f, 0.075f + focus)
        drawPart(bodyX + 0.27f, 0.22f - breath * 0.4f, -0.05f,
            0.53f * knotWidth, 0.79f * pulse, 0.42f, glassLight, 0.92f, glow, 21f, 0.7f, 0.062f + focus)
        drawPart(bodyX - 0.03f, -0.48f + breath, -0.08f,
            0.59f * knotWidth, 0.72f * pulse, 0.43f, glass, 0.96f, glow, 4f, 0.76f, 0.09f + focus)

        // The amber core is the only stable landmark; all surrounding anatomy stays transient.
        drawPart(bodyX, -0.08f + breath, 0.36f,
            0.24f * pulse * coreScale, 0.34f * pulse * coreScale, 0.18f, amber, 0.9f, 0.96f, deform = 0.035f)
        drawPart(bodyX, -0.08f + breath, 0.5f,
            0.09f * pulse * coreScale, 0.14f * pulse * coreScale, 0.05f, warmWhite, 0.84f, 1f)

        val communicating = mood == AvatarExpression.SPEAKING || mood == AvatarExpression.LISTENING ||
            mood == AvatarExpression.HAPPY || mood == AvatarExpression.SURPRISED ||
            direction.gesture == AvatarGesture.TALK
        val facePresence = when {
            communicating -> 0.96f
            mood == AvatarExpression.SLEEPY -> 0.08f
            mood == AvatarExpression.THINKING -> 0.22f
            else -> 0.38f
        }
        val blink = (SystemClock.uptimeMillis() % 4_600L) in 0L..115L
        val eyeHeight = when {
            blink -> 0.01f
            mood == AvatarExpression.HAPPY -> 0.027f
            mood == AvatarExpression.SURPRISED -> 0.095f
            else -> 0.052f
        }
        val gazeX = direction.gazeX * 0.04f
        val gazeY = direction.gazeY * 0.025f
        val eyeGap = 0.1f + value.eyeSpacing * 0.1f
        val eyeWidth = 0.035f + value.eyeSize * 0.03f
        drawPart(bodyX - eyeGap + gazeX, 0.48f + gazeY + breath, 0.48f,
            eyeWidth, eyeHeight, 0.024f, warmWhite, 0.74f, 1f, alpha = facePresence)
        drawPart(bodyX + eyeGap + gazeX, 0.48f + gazeY + breath, 0.48f,
            eyeWidth, eyeHeight, 0.024f, warmWhite, 0.74f, 1f, alpha = facePresence)
        val mouthOpen = when (mood) {
            AvatarExpression.SPEAKING -> 0.018f + abs(sin(seconds * 9f)) * 0.068f
            AvatarExpression.SURPRISED -> 0.085f
            AvatarExpression.HAPPY -> 0.024f
            else -> 0.012f
        }
        drawPart(bodyX, 0.31f + breath, 0.5f,
            0.09f + value.mouthWidth * 0.08f, mouthOpen, 0.022f,
            memory, 0.72f, 1f, alpha = facePresence)

        drawThoughtGesture(direction.gesture, bodyX, breath, seconds, strength, glassLight, memory, glow)
    }

    private fun drawThoughtField(
        bodyX: Float,
        seconds: Float,
        intensity: Float,
        width: Float,
        height: Float,
        color: FloatArray,
    ) {
        thoughtField.update(seconds, intensity)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.translateM(model, 0, bodyX, 0f, 0f)
        Matrix.scaleM(model, 0, 0.86f + width * 0.28f, 0.9f + height * 0.18f, 1f)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUseProgram(fieldProgram)
        GLES20.glUniformMatrix4fv(fieldMvpHandle, 1, false, mvp, 0)
        GLES20.glDepthMask(false)
        GLES20.glUniform4f(fieldColorHandle, color[0], color[1], color[2], 0.18f + intensity * 0.12f)
        GLES20.glUniform1f(fieldPointSizeHandle, 1.5f)
        GLES20.glUniform1f(fieldRoundHandle, 0f)
        thoughtField.drawLines(fieldPositionHandle)
        GLES20.glUniform4f(fieldColorHandle, color[0], color[1], color[2], 0.58f + intensity * 0.25f)
        GLES20.glUniform1f(fieldPointSizeHandle, 3.5f + intensity * 2.2f)
        GLES20.glUniform1f(fieldRoundHandle, 1f)
        thoughtField.drawPoints(fieldPositionHandle)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(program)
    }

    private fun drawThoughtGesture(
        gesture: AvatarGesture,
        bodyX: Float,
        breath: Float,
        seconds: Float,
        intensity: Float,
        glass: FloatArray,
        light: FloatArray,
        glow: Float,
    ) {
        val active = when (gesture) {
            AvatarGesture.WAVE, AvatarGesture.POINT, AvatarGesture.CELEBRATE,
            AvatarGesture.COMFORT, AvatarGesture.EXPLAIN, AvatarGesture.TALK -> true
            else -> false
        }
        if (!active) return
        val side = if (gesture == AvatarGesture.COMFORT) -1f else 1f
        val lift = when (gesture) {
            AvatarGesture.WAVE, AvatarGesture.CELEBRATE -> 0.86f
            AvatarGesture.POINT -> 0.28f
            AvatarGesture.COMFORT -> -0.2f
            else -> 0.12f + sin(seconds * 3.2f) * 0.12f
        }
        val wave = if (gesture == AvatarGesture.WAVE) sin(seconds * 7.5f) * 0.16f else 0f
        repeat(6) { index ->
            val progress = (index + 1) / 6f
            val curl = sin(progress * PI.toFloat()) * (0.12f + wave)
            drawPart(
                bodyX + side * (0.31f + progress * 0.72f) + curl,
                -0.02f + breath + lift * progress,
                0.04f + progress * 0.12f,
                0.16f - progress * 0.055f,
                0.24f - progress * 0.085f,
                0.14f - progress * 0.035f,
                if (index == 5) light else glass,
                0.86f,
                glow,
                rotationZ = -side * (34f + lift * 18f),
                alpha = (0.78f - progress * 0.18f) * intensity.coerceAtLeast(0.35f),
                deform = 0.05f,
            )
        }
    }

    private fun drawHair(
        value: AgentAvatar, color: FloatArray, head: Float, gloss: Float, glow: Float,
        offsetX: Float, offsetY: Float,
    ) {
        if (value.hairStyle == AvatarHairStyle.BALD) return
        val count = when (value.hairStyle) {
            AvatarHairStyle.BUZZ -> 5
            AvatarHairStyle.SHORT -> 7
            AvatarHairStyle.BOB -> 9
            AvatarHairStyle.WAVY -> 11
            AvatarHairStyle.PONYTAIL -> 8
            AvatarHairStyle.BALD -> 0
        }
        repeat(count) { index ->
            val angle = PI.toFloat() * (0.12f + index.toFloat() / (count - 1).coerceAtLeast(1) * 0.76f)
            val x = cos(angle) * head * 0.72f
            val y = 0.67f + sin(angle) * head * 0.76f
            drawPart(x + offsetX, y + offsetY, 0.08f,
                head * 0.34f, head * 0.34f, head * 0.54f, color, gloss, glow)
        }
        if (value.hairStyle == AvatarHairStyle.PONYTAIL) {
            drawPart(head * 0.8f + offsetX, 0.56f + offsetY, -0.2f,
                0.25f, 0.46f, 0.25f, color, gloss, glow)
        }
    }

    private fun drawAccessory(
        type: AvatarAccessory, color: FloatArray, head: Float, gloss: Float, glow: Float,
        offsetX: Float, offsetY: Float,
    ) {
        when (type) {
            AvatarAccessory.NONE -> Unit
            AvatarAccessory.VISOR -> drawPart(offsetX, 0.72f + offsetY, head * 0.92f,
                0.52f, 0.13f, 0.045f, color, gloss, 0.3f + glow)
            AvatarAccessory.HEADSET -> {
                drawPart(-head * 0.86f + offsetX, 0.65f + offsetY, 0f,
                    0.12f, 0.28f, 0.16f, color, gloss, glow)
                drawPart(head * 0.86f + offsetX, 0.65f + offsetY, 0f,
                    0.12f, 0.28f, 0.16f, color, gloss, glow)
            }
            AvatarAccessory.HALO -> {
                repeat(12) { index ->
                    val angle = index * PI.toFloat() * 2f / 12f
                    drawPart(cos(angle) * 0.52f + offsetX, 1.58f + offsetY, sin(angle) * 0.24f,
                        0.07f, 0.07f, 0.07f, color, gloss, 0.5f + glow)
                }
            }
            AvatarAccessory.HORNS -> {
                drawPart(-0.38f + offsetX, 1.37f + offsetY, 0f, 0.11f, 0.34f, 0.11f, color, gloss, glow)
                drawPart(0.38f + offsetX, 1.37f + offsetY, 0f, 0.11f, 0.34f, 0.11f, color, gloss, glow)
            }
        }
    }

    private fun drawPart(
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
        color: FloatArray, gloss: Float, glow: Float,
        rotationZ: Float = 0f,
        alpha: Float = 1f,
        deform: Float = 0f,
    ) {
        GLES20.glUseProgram(program)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, rotationZ, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform3fv(colorHandle, 1, color, 0)
        GLES20.glUniform1f(glossHandle, gloss)
        GLES20.glUniform1f(glowHandle, glow.coerceIn(0f, 1f))
        GLES20.glUniform1f(alphaHandle, alpha.coerceIn(0f, 1f))
        GLES20.glUniform1f(timeHandle, frameSeconds)
        GLES20.glUniform1f(deformHandle, deform.coerceIn(0f, 0.18f))
        mesh.draw(positionHandle, normalHandle)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { value ->
            GLES20.glAttachShader(value, vertex)
            GLES20.glAttachShader(value, fragment)
            GLES20.glLinkProgram(value)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(value, GLES20.GL_LINK_STATUS, linked, 0)
            require(linked[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(value) }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        require(compiled[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform float uTime;
            uniform float uDeform;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                float ripple = sin(aPosition.y * 5.8 + uTime * 1.7)
                    + sin(aPosition.x * 7.1 - uTime * 1.15);
                vec3 displaced = aPosition + aNormal * ripple * uDeform;
                vNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMvp * vec4(displaced, 1.0);
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uColor;
            uniform float uGloss;
            uniform float uGlow;
            uniform float uAlpha;
            varying vec3 vNormal;
            void main() {
                vec3 light = normalize(vec3(-0.35, 0.65, 0.8));
                float diffuse = max(dot(normalize(vNormal), light), 0.0);
                float rim = pow(1.0 - max(vNormal.z, 0.0), 2.0);
                float shine = pow(max(dot(normalize(vNormal), light), 0.0), 10.0) * uGloss;
                vec3 color = uColor * (0.32 + diffuse * 0.7) + vec3(shine) + uColor * rim * uGlow;
                gl_FragColor = vec4(color, uAlpha);
            }
        """
        const val FIELD_VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform float uPointSize;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """
        const val FIELD_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform float uRound;
            void main() {
                if (uRound > 0.5 && distance(gl_PointCoord, vec2(0.5)) > 0.5) discard;
                gl_FragColor = uColor;
            }
        """
    }
}

private class SphereMesh(segments: Int, rings: Int) {
    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    init {
        val rawVertices = ArrayList<Float>((segments + 1) * (rings + 1) * 6)
        val rawIndices = ArrayList<Short>(segments * rings * 6)
        for (ring in 0..rings) {
            val v = ring.toFloat() / rings
            val phi = PI.toFloat() * v
            for (segment in 0..segments) {
                val u = segment.toFloat() / segments
                val theta = 2f * PI.toFloat() * u
                val x = sin(phi) * cos(theta)
                val y = cos(phi)
                val z = sin(phi) * sin(theta)
                rawVertices.add(x); rawVertices.add(y); rawVertices.add(z)
                rawVertices.add(x); rawVertices.add(y); rawVertices.add(z)
            }
        }
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val first = (ring * (segments + 1) + segment).toShort()
                val second = (first + segments + 1).toShort()
                rawIndices.add(first); rawIndices.add(second); rawIndices.add((first + 1).toShort())
                rawIndices.add(second); rawIndices.add((second + 1).toShort()); rawIndices.add((first + 1).toShort())
            }
        }
        vertices = ByteBuffer.allocateDirect(rawVertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { rawVertices.forEach { put(it) }; position(0) }
        indices = ByteBuffer.allocateDirect(rawIndices.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { rawIndices.forEach { put(it) }; position(0) }
        indexCount = rawIndices.size
    }

    fun draw(positionHandle: Int, normalHandle: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, vertices)
        GLES20.glEnableVertexAttribArray(positionHandle)
        vertices.position(3)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, vertices)
        GLES20.glEnableVertexAttribArray(normalHandle)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
    }
}

private class ThoughtFieldMesh {
    private val pointData = FloatArray(ThoughtFieldGeometry.POINT_COUNT * 3)
    private val lineData = FloatArray(LINK_COUNT * 6)
    private val points = directFloatBuffer(pointData.size)
    private val lines = directFloatBuffer(lineData.size)

    fun update(seconds: Float, intensity: Float) {
        repeat(ThoughtFieldGeometry.POINT_COUNT) { index ->
            ThoughtFieldGeometry.writePosition(index, seconds, intensity, pointData, index * 3)
        }
        repeat(LINK_COUNT) { link ->
            copyPoint(link * 4, link * 6)
            copyPoint((link * 4 + 37) % ThoughtFieldGeometry.POINT_COUNT, link * 6 + 3)
        }
        points.position(0); points.put(pointData); points.position(0)
        lines.position(0); lines.put(lineData); lines.position(0)
    }

    fun drawPoints(positionHandle: Int) = draw(positionHandle, points, GLES20.GL_POINTS, ThoughtFieldGeometry.POINT_COUNT)

    fun drawLines(positionHandle: Int) = draw(positionHandle, lines, GLES20.GL_LINES, LINK_COUNT * 2)

    private fun copyPoint(point: Int, targetOffset: Int) {
        val sourceOffset = point * 3
        lineData[targetOffset] = pointData[sourceOffset]
        lineData[targetOffset + 1] = pointData[sourceOffset + 1]
        lineData[targetOffset + 2] = pointData[sourceOffset + 2]
    }

    private fun draw(positionHandle: Int, buffer: FloatBuffer, mode: Int, count: Int) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glDrawArrays(mode, 0, count)
    }

    private companion object {
        // ponytail: a fixed 36-edge constellation keeps the mobile render budget predictable;
        // promote this to a bounded graph buffer only when live memory-node topology is wired in.
        const val LINK_COUNT = 36

        fun directFloatBuffer(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}

private fun Long.rgb() = floatArrayOf(
    ((this shr 16) and 0xFF).toFloat() / 255f,
    ((this shr 8) and 0xFF).toFloat() / 255f,
    (this and 0xFF).toFloat() / 255f,
)
