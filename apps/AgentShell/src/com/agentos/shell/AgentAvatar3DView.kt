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
) {
    AndroidView(
        factory = { AvatarSurfaceView(it) },
        modifier = modifier.semantics {
            contentDescription = "${avatar.name}，可旋转的 3D 角色，${expression.label}表情"
        },
        update = { it.updateAvatar(avatar, expression) },
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

    init {
        setEGLContextClientVersion(2)
        setRenderer(avatarRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        preserveEGLContextOnPause = true
    }

    fun updateAvatar(avatar: AgentAvatar, expression: AvatarExpression) {
        avatarRenderer.avatar = avatar
        avatarRenderer.expression = expression
        renderMode = if (expression == AvatarExpression.SPEAKING) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
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
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }
}

private class AvatarRenderer : GLSurfaceView.Renderer {
    @Volatile var avatar = AgentAvatar()
    @Volatile var expression = AvatarExpression.NEUTRAL
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
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.024f, 0.063f, 0.078f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        glossHandle = GLES20.glGetUniformLocation(program, "uGloss")
        glowHandle = GLES20.glGetUniformLocation(program, "uGlow")
        mesh = SphereMesh(24, 16)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 36f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 0.15f, zoom, 0f, 0.05f, 0f, 0f, 1f, 0f)
        drawAvatar(avatar, expression)
    }

    private fun drawAvatar(value: AgentAvatar, mood: AvatarExpression) {
        val skin = value.skinTone.argb.rgb()
        val hair = value.hairColor.argb.rgb()
        val outfit = value.outfitColor.argb.rgb()
        val ink = 0xFF172126.rgb()
        val accent = when (value.styleFamily) {
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
        val bodyY = -0.72f - value.bodyHeight * 0.12f
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
        drawPart(0f, bodyY, 0f, shoulder, 0.92f + value.bodyHeight * 0.2f, 0.42f, outfit, gloss, glow)
        drawPart(-shoulder * 0.82f, bodyY - 0.02f, 0f, 0.27f, 0.72f, 0.27f, outfit, gloss, glow)
        drawPart(shoulder * 0.82f, bodyY - 0.02f, 0f, 0.27f, 0.72f, 0.27f, outfit, gloss, glow)
        drawPart(0f, -0.02f, 0f, 0.24f, 0.33f, 0.24f, skin, 0.18f, 0f)
        if (value.outfitStyle == AvatarOutfitStyle.ARMOR) {
            drawPart(0f, bodyY + 0.16f, 0.38f, shoulder * 0.88f, 0.38f, 0.12f, accent, 0.85f, glow)
        } else if (value.outfitStyle == AvatarOutfitStyle.ROBE) {
            drawPart(0f, bodyY - 0.35f, 0f, shoulder * 0.94f, 0.86f, 0.38f, outfit, gloss, glow)
        } else if (value.outfitStyle == AvatarOutfitStyle.SUIT) {
            drawPart(-0.16f, bodyY + 0.18f, 0.39f, 0.08f, 0.52f, 0.06f, accent, gloss, glow)
            drawPart(0.16f, bodyY + 0.18f, 0.39f, 0.08f, 0.52f, 0.06f, accent, gloss, glow)
        }

        drawPart(0f, 0.62f, 0f, head * faceX, head * faceY, head * 0.9f, skin, 0.22f, 0f)
        drawPart(0f, 0.51f, head * 0.83f, 0.09f, 0.13f, 0.12f, skin, 0.2f, 0f)

        val eyeY = 0.72f
        val eyeGap = 0.24f + value.eyeSpacing * 0.09f
        val eyeSize = 0.065f + value.eyeSize * 0.045f
        val eyeScaleY = when {
            mood == AvatarExpression.HAPPY || mood == AvatarExpression.SLEEPY -> 0.2f
            value.eyeStyle == AvatarEyeStyle.CALM || value.eyeStyle == AvatarEyeStyle.SHARP -> 0.55f
            else -> 1f
        }
        val eyeZ = head * 0.84f
        drawPart(-eyeGap, eyeY, eyeZ, eyeSize, eyeSize * eyeScaleY, 0.055f, ink, 0.1f, 0f)
        drawPart(eyeGap, eyeY, eyeZ, eyeSize, eyeSize * eyeScaleY, 0.055f, ink, 0.1f, 0f)
        if (value.eyeStyle == AvatarEyeStyle.BRIGHT && eyeScaleY > 0.5f) {
            drawPart(-eyeGap - eyeSize * 0.2f, eyeY + eyeSize * 0.2f, eyeZ + 0.054f,
                eyeSize * 0.22f, eyeSize * 0.22f, 0.018f, floatArrayOf(1f, 1f, 1f), 0.8f, glow)
            drawPart(eyeGap - eyeSize * 0.2f, eyeY + eyeSize * 0.2f, eyeZ + 0.054f,
                eyeSize * 0.22f, eyeSize * 0.22f, 0.018f, floatArrayOf(1f, 1f, 1f), 0.8f, glow)
        }

        val mouthWidth = 0.17f + value.mouthWidth * 0.13f
        val mouthHeight = when (mood) {
            AvatarExpression.SPEAKING -> 0.06f + abs(sin(SystemClock.uptimeMillis() / 95f)) * 0.1f
            AvatarExpression.SURPRISED -> 0.12f
            AvatarExpression.HAPPY, AvatarExpression.LISTENING -> 0.065f
            else -> 0.035f
        }
        drawPart(0f, 0.38f, eyeZ + 0.02f, mouthWidth, mouthHeight, 0.035f,
            if (mood == AvatarExpression.SPEAKING) floatArrayOf(0.48f, 0.12f, 0.16f) else ink, 0.12f, 0f)

        drawHair(value, hair, head, gloss, glow)
        drawAccessory(value.accessory, accent, head, gloss, glow)
    }

    private fun drawHair(value: AgentAvatar, color: FloatArray, head: Float, gloss: Float, glow: Float) {
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
            drawPart(x, y, 0.08f, head * 0.34f, head * 0.34f, head * 0.54f, color, gloss, glow)
        }
        if (value.hairStyle == AvatarHairStyle.PONYTAIL) {
            drawPart(head * 0.8f, 0.56f, -0.2f, 0.25f, 0.46f, 0.25f, color, gloss, glow)
        }
    }

    private fun drawAccessory(type: AvatarAccessory, color: FloatArray, head: Float, gloss: Float, glow: Float) {
        when (type) {
            AvatarAccessory.NONE -> Unit
            AvatarAccessory.VISOR -> drawPart(0f, 0.72f, head * 0.92f, 0.52f, 0.13f, 0.045f, color, gloss, 0.3f + glow)
            AvatarAccessory.HEADSET -> {
                drawPart(-head * 0.86f, 0.65f, 0f, 0.12f, 0.28f, 0.16f, color, gloss, glow)
                drawPart(head * 0.86f, 0.65f, 0f, 0.12f, 0.28f, 0.16f, color, gloss, glow)
            }
            AvatarAccessory.HALO -> {
                repeat(12) { index ->
                    val angle = index * PI.toFloat() * 2f / 12f
                    drawPart(cos(angle) * 0.52f, 1.58f, sin(angle) * 0.24f,
                        0.07f, 0.07f, 0.07f, color, gloss, 0.5f + glow)
                }
            }
            AvatarAccessory.HORNS -> {
                drawPart(-0.38f, 1.37f, 0f, 0.11f, 0.34f, 0.11f, color, gloss, glow)
                drawPart(0.38f, 1.37f, 0f, 0.11f, 0.34f, 0.11f, color, gloss, glow)
            }
        }
    }

    private fun drawPart(
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
        color: FloatArray, gloss: Float, glow: Float,
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform3fv(colorHandle, 1, color, 0)
        GLES20.glUniform1f(glossHandle, gloss)
        GLES20.glUniform1f(glowHandle, glow.coerceIn(0f, 1f))
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
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uColor;
            uniform float uGloss;
            uniform float uGlow;
            varying vec3 vNormal;
            void main() {
                vec3 light = normalize(vec3(-0.35, 0.65, 0.8));
                float diffuse = max(dot(normalize(vNormal), light), 0.0);
                float rim = pow(1.0 - max(vNormal.z, 0.0), 2.0);
                float shine = pow(max(dot(normalize(vNormal), light), 0.0), 10.0) * uGloss;
                vec3 color = uColor * (0.32 + diffuse * 0.7) + vec3(shine) + uColor * rim * uGlow;
                gl_FragColor = vec4(color, 1.0);
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

private fun Long.rgb() = floatArrayOf(
    ((this shr 16) and 0xFF).toFloat() / 255f,
    ((this shr 8) and 0xFF).toFloat() / 255f,
    (this and 0xFF).toFloat() / 255f,
)
