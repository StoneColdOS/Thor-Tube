package io.github.stonecoldos.thortube.crt

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import io.github.stonecoldos.thortube.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp

/**
 * Draws video frames through the CRT shader.
 *
 * ExoPlayer decodes into [Surface] (backed by an OES texture). Every new frame, and every
 * frame while static is fading, is redrawn with the shader.
 */
class CrtRenderer(
    private val view: GLSurfaceView,
    /** Called on the main thread whenever a new Surface for the player is ready. */
    private val onSurfaceReady: (Surface) -> Unit,
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    @Volatile var crtEnabled = true
    @Volatile var pictureScale = 1f
    @Volatile var fill = false
    @Volatile var curve = 1f
    @Volatile var scan = 1f
    @Volatile var soft = 0f
    @Volatile var grain = 0f
    @Volatile var targetStatic = 1f   // starts snowy until the first picture arrives
    @Volatile private var videoAspect = 0f

    private var staticLevel = 1f
    private var program = 0
    private var texId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val stMatrix = FloatArray(16)
    private var width = 1
    private var height = 1
    private var lastFrameMs = SystemClock.uptimeMillis()
    private val startMs = SystemClock.uptimeMillis()

    private var aPos = 0
    private var uTex = 0
    private var uST = 0
    private var uRes = 0
    private var uVideoAspect = 0
    private var uTime = 0
    private var uStatic = 0
    private var uCrt = 0
    private var uScale = 0
    private var uFill = 0
    private var uCurve = 0
    private var uScan = 0
    private var uSoft = 0
    private var uGrain = 0

    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    fun setVideoSize(width: Int, height: Int, pixelRatio: Float) {
        if (width > 0 && height > 0) {
            videoAspect = width * pixelRatio / height
            view.requestRender()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(CrtShaders.VERTEX, CrtShaders.FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uST = GLES20.glGetUniformLocation(program, "uST")
        uRes = GLES20.glGetUniformLocation(program, "uRes")
        uVideoAspect = GLES20.glGetUniformLocation(program, "uVideoAspect")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uStatic = GLES20.glGetUniformLocation(program, "uStatic")
        uCrt = GLES20.glGetUniformLocation(program, "uCrt")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uFill = GLES20.glGetUniformLocation(program, "uFill")
        uCurve = GLES20.glGetUniformLocation(program, "uCurve")
        uScan = GLES20.glGetUniformLocation(program, "uScan")
        uSoft = GLES20.glGetUniformLocation(program, "uSoft")
        uGrain = GLES20.glGetUniformLocation(program, "uGrain")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture?.release()
        surface?.release()
        val st = SurfaceTexture(texId).also { it.setOnFrameAvailableListener(this) }
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        android.opengl.Matrix.setIdentityM(stMatrix, 0)
        view.post { onSurfaceReady(s) }
        AppLog.i("ThorTube", "CRT renderer ready")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
        AppLog.d("ThorTube", "Glass is ${w}x$h px")
    }

    override fun onDrawFrame(gl: GL10?) {
        surfaceTexture?.let {
            it.updateTexImage()
            it.getTransformMatrix(stMatrix)
        }

        // Ease static toward its target, frame-rate independent.
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameMs).coerceIn(0, 100) / 1000f
        lastFrameMs = now
        val target = targetStatic
        val speed = if (target > staticLevel) 25f else 7f   // snap in, fade out
        staticLevel += (target - staticLevel) * (1f - exp(-speed * dt))
        if (kotlin.math.abs(target - staticLevel) < 0.002f) staticLevel = target

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uST, 1, false, stMatrix, 0)
        GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uVideoAspect, videoAspect)
        GLES20.glUniform1f(uTime, ((now - startMs) % 100_000L) / 1000f)
        GLES20.glUniform1f(uStatic, staticLevel)
        GLES20.glUniform1f(uCrt, if (crtEnabled) 1f else 0f)
        GLES20.glUniform1f(uScale, pictureScale)
        GLES20.glUniform1f(uFill, if (fill) 1f else 0f)
        GLES20.glUniform1f(uCurve, curve)
        GLES20.glUniform1f(uScan, scan)
        GLES20.glUniform1f(uSoft, soft)
        GLES20.glUniform1f(uGrain, grain)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)

        // Keep drawing while there's static to animate; otherwise wait for the next video frame.
        if (staticLevel > 0f) view.requestRender()
    }

    override fun onFrameAvailable(st: SurfaceTexture?) = view.requestRender()

    /** The surface the player draws into, once the GL side has made it. */
    val playerSurface: Surface? get() = surface

    /** The two looks: a curved 90s tube, or a flat late-2000s HD tube with faint lines. */
    fun applyLook(flat: Boolean) {
        crtEnabled = true
        curve = if (flat) 0f else 1f
        scan = if (flat) 0.35f else 1f
        soft = 0f
        grain = 0f
        view.requestRender()
    }

    fun release() {
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) AppLog.e("ThorTube", "Shader link failed: ${GLES20.glGetProgramInfoLog(p)}")
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) AppLog.e("ThorTube", "Shader compile failed: ${GLES20.glGetShaderInfoLog(s)}")
        return s
    }
}
