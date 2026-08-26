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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    private val avatarRenderer = AvatarRenderer(context.applicationContext)
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
        val normalizedPerformance = performance.normalized()
        avatarRenderer.avatar = avatar
        avatarRenderer.expression = expression
        avatarRenderer.performance = normalizedPerformance
        avatarRenderer.thoughtState = ThoughtFieldUniformState.from(expression, normalizedPerformance)
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

private class AvatarRenderer(context: Context) : GLSurfaceView.Renderer {
    @Volatile var avatar = AgentAvatar()
    @Volatile var expression = AvatarExpression.NEUTRAL
    @Volatile var performance = AvatarPerformance()
    @Volatile var thoughtState = ThoughtFieldUniformState.from(expression, performance)
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
    private val thoughtFragmentShader = context.resources.openRawResource(R.raw.thought_field_fragment)
        .bufferedReader().use { it.readText() }
    private val thoughtSurfaceVertexShader = context.resources.openRawResource(R.raw.thought_field_surface_vertex)
        .bufferedReader().use { it.readText() }
    private val thoughtPartVertexShader = context.resources.openRawResource(R.raw.thought_field_part_vertex)
        .bufferedReader().use { it.readText() }
    private val thoughtGlassFragmentShader = context.resources.openRawResource(R.raw.thought_field_glass_fragment)
        .bufferedReader().use { it.readText() }
    private lateinit var thoughtQuad: FullscreenQuad
    private lateinit var thoughtSurface: ThoughtFieldSurfaceMesh
    private var thoughtProgram = 0
    private var thoughtPositionHandle = 0
    private var thoughtResolutionHandle = 0
    private var thoughtTimeHandle = 0
    private var thoughtYawHandle = 0
    private var thoughtZoomHandle = 0
    private var thoughtMoodHandle = 0
    private var thoughtGestureHandle = 0
    private var thoughtIntensityHandle = 0
    private var thoughtGazeHandle = 0
    private var thoughtShapeHandle = 0
    private var thoughtFieldShapeHandle = 0
    private var thoughtExpressionHandle = 0
    private var thoughtSurfaceLayerHandle = 0
    private var surfaceProgram = 0
    private var surfaceParamHandle = 0
    private var surfaceMvpHandle = 0
    private var surfaceModelHandle = 0
    private var surfaceTimeHandle = 0
    private var surfaceFieldShapeHandle = 0
    private var surfaceLayerPhaseHandle = 0
    private var surfaceLayerScaleHandle = 0
    private var surfaceInverseScaleHandle = 0
    private var surfaceCameraHandle = 0
    private var surfaceBackFaceHandle = 0
    private var surfaceIntensityHandle = 0
    private var surfaceGlowHandle = 0
    private var surfacePassHandle = 0
    private var partProgram = 0
    private var partPositionHandle = 0
    private var partNormalHandle = 0
    private var partMvpHandle = 0
    private var partModelHandle = 0
    private var partTimeHandle = 0
    private var partDeformHandle = 0
    private var partInverseScaleHandle = 0
    private var partCameraHandle = 0
    private var partBackFaceHandle = 0
    private var partIntensityHandle = 0
    private var partGlowHandle = 0
    private var partSurfacePassHandle = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
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
        thoughtProgram = createProgram(THOUGHT_VERTEX_SHADER, thoughtFragmentShader)
        thoughtPositionHandle = GLES20.glGetAttribLocation(thoughtProgram, "aPosition")
        thoughtResolutionHandle = GLES20.glGetUniformLocation(thoughtProgram, "uResolution")
        thoughtTimeHandle = GLES20.glGetUniformLocation(thoughtProgram, "uTime")
        thoughtYawHandle = GLES20.glGetUniformLocation(thoughtProgram, "uYaw")
        thoughtZoomHandle = GLES20.glGetUniformLocation(thoughtProgram, "uZoom")
        thoughtMoodHandle = GLES20.glGetUniformLocation(thoughtProgram, "uMood")
        thoughtGestureHandle = GLES20.glGetUniformLocation(thoughtProgram, "uGesture")
        thoughtIntensityHandle = GLES20.glGetUniformLocation(thoughtProgram, "uIntensity")
        thoughtGazeHandle = GLES20.glGetUniformLocation(thoughtProgram, "uGaze")
        thoughtShapeHandle = GLES20.glGetUniformLocation(thoughtProgram, "uShape")
        thoughtFieldShapeHandle = GLES20.glGetUniformLocation(thoughtProgram, "uFieldShape")
        thoughtExpressionHandle = GLES20.glGetUniformLocation(thoughtProgram, "uExpression")
        thoughtSurfaceLayerHandle = GLES20.glGetUniformLocation(thoughtProgram, "uSurfaceLayer")
        surfaceProgram = createProgram(thoughtSurfaceVertexShader, thoughtGlassFragmentShader)
        surfaceParamHandle = GLES20.glGetAttribLocation(surfaceProgram, "aParam")
        surfaceMvpHandle = GLES20.glGetUniformLocation(surfaceProgram, "uMvp")
        surfaceModelHandle = GLES20.glGetUniformLocation(surfaceProgram, "uModel")
        surfaceTimeHandle = GLES20.glGetUniformLocation(surfaceProgram, "uTime")
        surfaceFieldShapeHandle = GLES20.glGetUniformLocation(surfaceProgram, "uFieldShape")
        surfaceLayerPhaseHandle = GLES20.glGetUniformLocation(surfaceProgram, "uLayerPhase")
        surfaceLayerScaleHandle = GLES20.glGetUniformLocation(surfaceProgram, "uLayerScale")
        surfaceInverseScaleHandle = GLES20.glGetUniformLocation(surfaceProgram, "uInverseScale")
        surfaceCameraHandle = GLES20.glGetUniformLocation(surfaceProgram, "uCameraPosition")
        surfaceBackFaceHandle = GLES20.glGetUniformLocation(surfaceProgram, "uBackFace")
        surfaceIntensityHandle = GLES20.glGetUniformLocation(surfaceProgram, "uIntensity")
        surfaceGlowHandle = GLES20.glGetUniformLocation(surfaceProgram, "uGlow")
        surfacePassHandle = GLES20.glGetUniformLocation(surfaceProgram, "uSurfacePass")
        partProgram = createProgram(thoughtPartVertexShader, thoughtGlassFragmentShader)
        partPositionHandle = GLES20.glGetAttribLocation(partProgram, "aPosition")
        partNormalHandle = GLES20.glGetAttribLocation(partProgram, "aNormal")
        partMvpHandle = GLES20.glGetUniformLocation(partProgram, "uMvp")
        partModelHandle = GLES20.glGetUniformLocation(partProgram, "uModel")
        partTimeHandle = GLES20.glGetUniformLocation(partProgram, "uTime")
        partDeformHandle = GLES20.glGetUniformLocation(partProgram, "uDeform")
        partInverseScaleHandle = GLES20.glGetUniformLocation(partProgram, "uInverseScale")
        partCameraHandle = GLES20.glGetUniformLocation(partProgram, "uCameraPosition")
        partBackFaceHandle = GLES20.glGetUniformLocation(partProgram, "uBackFace")
        partIntensityHandle = GLES20.glGetUniformLocation(partProgram, "uIntensity")
        partGlowHandle = GLES20.glGetUniformLocation(partProgram, "uGlow")
        partSurfacePassHandle = GLES20.glGetUniformLocation(partProgram, "uSurfacePass")
        mesh = SphereMesh(24, 16)
        thoughtQuad = FullscreenQuad()
        thoughtSurface = ThoughtFieldSurfaceMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
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
        if (value.styleFamily == AvatarStyleFamily.SYSTEM) {
            drawThoughtFieldAvatar(value, seconds, direction)
            return
        }
        val strength = direction.intensity
        val breath = sin(seconds * 2.1f) * 0.018f
        val idleSway = sin(seconds * 0.72f) * 0.018f
        val gestureWave = if (direction.gesture == AvatarGesture.WAVE) sin(seconds * 8f) * 18f * strength else 0f
        val gestureNod = if (direction.gesture == AvatarGesture.NOD) sin(seconds * 5.5f) * 0.06f * strength else 0f
        val talkBeat = if (direction.gesture == AvatarGesture.TALK) sin(seconds * 3.8f) * 8f * strength else 0f
        val bodyX = idleSway + if (direction.gesture == AvatarGesture.CELEBRATE) sin(seconds * 3f) * 0.06f else 0f
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

    /** The background volume, real 3D glass surface, and temporary rig are all generated at runtime. */
    private fun drawThoughtFieldAvatar(
        value: AgentAvatar,
        seconds: Float,
        direction: AvatarPerformance,
    ) {
        val state = thoughtState
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(thoughtProgram)
        GLES20.glUniform2f(thoughtResolutionHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES20.glUniform1f(thoughtTimeHandle, seconds)
        GLES20.glUniform1f(thoughtYawHandle, yaw)
        GLES20.glUniform1f(thoughtZoomHandle, zoom)
        GLES20.glUniform1f(thoughtMoodHandle, state.mood)
        GLES20.glUniform1f(thoughtGestureHandle, state.gesture)
        GLES20.glUniform1f(thoughtIntensityHandle, state.intensity)
        GLES20.glUniform2f(thoughtGazeHandle, state.gazeX, state.gazeY)
        GLES20.glUniform4f(thoughtShapeHandle,
            value.faceWidth, value.headScale, value.eyeSpacing, value.eyeSize)
        GLES20.glUniform2f(thoughtFieldShapeHandle, value.shoulderWidth, value.bodyHeight)
        GLES20.glUniform4f(thoughtExpressionHandle,
            state.speaking, value.mouthWidth, state.facePresence, value.glow)
        GLES20.glUniform1f(thoughtSurfaceLayerHandle, 1f)
        thoughtQuad.draw(thoughtPositionHandle)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        drawThoughtSurface(value, seconds, state)
        drawThoughtGesture(direction, seconds, value.glow)
    }

    private fun drawThoughtSurface(
        value: AgentAvatar,
        seconds: Float,
        state: ThoughtFieldUniformState,
    ) {
        GLES20.glUseProgram(surfaceProgram)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, 0.88f, 0.78f, 0.88f)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUniformMatrix4fv(surfaceMvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(surfaceModelHandle, 1, false, model, 0)
        GLES20.glUniform1f(surfaceTimeHandle, seconds)
        GLES20.glUniform2f(surfaceFieldShapeHandle, value.shoulderWidth, value.bodyHeight)
        GLES20.glUniform3f(surfaceInverseScaleHandle, 1f / 0.88f, 1f / 0.78f, 1f / 0.88f)
        GLES20.glUniform3f(surfaceCameraHandle, 0f, 0.15f, zoom)
        GLES20.glUniform1f(surfaceIntensityHandle, state.intensity)
        GLES20.glUniform1f(surfaceGlowHandle, value.glow)
        GLES20.glUniform1f(surfacePassHandle, 1f)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        for (layer in SURFACE_LAYERS.indices) {
            GLES20.glUniform1f(surfaceLayerPhaseHandle, SURFACE_LAYERS[layer])
            GLES20.glUniform1f(surfaceLayerScaleHandle, SURFACE_SCALES[layer])
            GLES20.glCullFace(GLES20.GL_FRONT)
            GLES20.glUniform1f(surfaceBackFaceHandle, 1f)
            thoughtSurface.draw(surfaceParamHandle)
            GLES20.glCullFace(GLES20.GL_BACK)
            GLES20.glUniform1f(surfaceBackFaceHandle, 0f)
            thoughtSurface.draw(surfaceParamHandle)
        }
        GLES20.glColorMask(false, false, false, false)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glUniform1f(surfaceLayerPhaseHandle, SURFACE_LAYERS[0])
        GLES20.glUniform1f(surfaceLayerScaleHandle, SURFACE_SCALES[0])
        GLES20.glUniform1f(surfaceBackFaceHandle, 0f)
        thoughtSurface.draw(surfaceParamHandle)
        GLES20.glColorMask(true, true, true, true)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawThoughtGesture(direction: AvatarPerformance, seconds: Float, glow: Float) {
        val gestureActive = when (direction.gesture) {
            AvatarGesture.TALK, AvatarGesture.WAVE, AvatarGesture.POINT,
            AvatarGesture.CELEBRATE, AvatarGesture.COMFORT, AvatarGesture.EXPLAIN -> true
            else -> false
        }
        if (!gestureActive) return
        val strength = direction.intensity.coerceIn(0f, 1f)
        val side = if (direction.gesture == AvatarGesture.POINT) 1f else -1f
        val motion = if (direction.gesture == AvatarGesture.WAVE) sin(seconds * 6.2f) * 0.055f * strength else 0f
        val shoulderX = side * 0.25f
        val shoulderY = 0.31f
        val elbowX = side * 0.35f
        val elbowY = 0.39f
        val forearmX = side * 0.41f
        val forearmY = 0.50f
        val wristX = if (direction.gesture == AvatarGesture.POINT) side * 0.62f else side * 0.45f + motion
        val wristY = if (direction.gesture == AvatarGesture.POINT) 0.38f else 0.66f

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        drawThoughtBone(shoulderX, shoulderY, elbowX, elbowY, 0.24f, 0.057f, seconds, strength, glow)
        drawThoughtBone(elbowX, elbowY, forearmX, forearmY, 0.26f, 0.052f, seconds, strength, glow)
        drawThoughtBone(forearmX, forearmY, wristX, wristY, 0.28f, 0.047f, seconds, strength, glow)
        drawThoughtPart(wristX + side * 0.030f, wristY + 0.035f, 0.31f,
            0.12f, 0.090f, 0.062f, -side * 22f, seconds, strength, glow, 0.018f)

        val palmX = wristX + side * 0.030f
        val palmY = wristY + 0.055f
        val fingers = if (direction.gesture == AvatarGesture.POINT) POINT_FINGERS else OPEN_HAND_FINGERS
        fingers.forEachIndexed { index, finger ->
            val mirror = -side
            val rootX = palmX + finger[0] * mirror
            val rootY = palmY + finger[1]
            val jointX = palmX + finger[2] * mirror
            val jointY = palmY + finger[3]
            val tipX = palmX + finger[4] * mirror
            val tipY = palmY + finger[5]
            val fingerZ = 0.32f + (index - 2) * 0.008f
            drawThoughtBone(rootX, rootY, jointX, jointY, fingerZ, 0.022f,
                seconds, strength, glow)
            drawThoughtBone(jointX, jointY, tipX, tipY, fingerZ, 0.017f,
                seconds, strength, glow)
        }
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawThoughtBone(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        z: Float,
        radius: Float,
        seconds: Float,
        intensity: Float,
        glow: Float,
    ) {
        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy)
        val rotation = (atan2(-dx, dy) * 180f / PI.toFloat())
        drawThoughtPart((startX + endX) * 0.5f, (startY + endY) * 0.5f, z,
            radius, length * 0.62f, radius, rotation, seconds, intensity, glow, 0.012f)
    }

    private fun drawThoughtPart(
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        rotationZ: Float,
        seconds: Float,
        intensity: Float,
        glow: Float,
        deform: Float,
    ) {
        GLES20.glUseProgram(partProgram)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, rotationZ, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUniformMatrix4fv(partMvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(partModelHandle, 1, false, model, 0)
        GLES20.glUniform1f(partTimeHandle, seconds)
        GLES20.glUniform1f(partDeformHandle, deform)
        GLES20.glUniform3f(partInverseScaleHandle, 1f / sx, 1f / sy, 1f / sz)
        GLES20.glUniform3f(partCameraHandle, 0f, 0.15f, zoom)
        GLES20.glUniform1f(partBackFaceHandle, 0f)
        GLES20.glUniform1f(partIntensityHandle, intensity)
        GLES20.glUniform1f(partGlowHandle, glow)
        GLES20.glUniform1f(partSurfacePassHandle, 0f)
        mesh.draw(partPositionHandle, partNormalHandle)
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
        // ponytail: Two fixed shells are the measured mobile ceiling until representative GPU profiles justify LOD tiers.
        val SURFACE_LAYERS = floatArrayOf(0f, 2.05f)
        val SURFACE_SCALES = floatArrayOf(1f, 0.84f)
        val OPEN_HAND_FINGERS = arrayOf(
            floatArrayOf(-0.08f, -0.01f, -0.16f, 0.02f, -0.22f, 0.07f),
            floatArrayOf(-0.06f, 0.06f, -0.15f, 0.14f, -0.20f, 0.22f),
            floatArrayOf(-0.01f, 0.08f, -0.08f, 0.19f, -0.10f, 0.28f),
            floatArrayOf(0.04f, 0.07f, 0.02f, 0.18f, 0.04f, 0.26f),
            floatArrayOf(0.08f, 0.04f, 0.11f, 0.13f, 0.15f, 0.20f),
        )
        val POINT_FINGERS = arrayOf(
            floatArrayOf(-0.02f, 0.07f, -0.01f, 0.20f, 0.00f, 0.34f),
            floatArrayOf(0.03f, 0.05f, 0.09f, 0.10f, 0.06f, 0.16f),
            floatArrayOf(0.06f, 0.02f, 0.13f, 0.04f, 0.10f, 0.10f),
            floatArrayOf(0.08f, -0.01f, 0.14f, 0.00f, 0.12f, 0.06f),
            floatArrayOf(-0.08f, 0.00f, -0.14f, 0.04f, -0.16f, 0.09f),
        )
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
        const val THOUGHT_VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """
    }
}

private class ThoughtFieldSurfaceMesh(
    data: ThoughtFieldSurfaceData = ThoughtFieldSurfaceGeometry.create(),
) {
    private val parameters = ByteBuffer.allocateDirect(data.parameters.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data.parameters)
            position(0)
        }
    private val indices = ByteBuffer.allocateDirect(data.indices.size * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(data.indices)
            position(0)
        }
    private val indexCount = data.indices.size

    fun draw(paramHandle: Int) {
        parameters.position(0)
        GLES20.glVertexAttribPointer(paramHandle, 3, GLES20.GL_FLOAT, false, 12, parameters)
        GLES20.glEnableVertexAttribArray(paramHandle)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
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

private class FullscreenQuad {
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun draw(positionHandle: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertices)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}

private fun Long.rgb() = floatArrayOf(
    ((this shr 16) and 0xFF).toFloat() / 255f,
    ((this shr 8) and 0xFF).toFloat() / 255f,
    (this and 0xFF).toFloat() / 255f,
)
