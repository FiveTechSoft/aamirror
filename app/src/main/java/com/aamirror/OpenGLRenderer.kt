package com.aamirror

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * Renders RGBA frame buffers to an Android Auto Surface via OpenGL ES 2.0.
 * Runs a dedicated render thread at 30fps.
 */
class OpenGLRenderer {

    companion object {
        private const val TAG = "OpenGLRenderer"
        private const val TARGET_FPS = 30
        private const val FRAME_TIMEOUT_MS = 33L
    }

    // Queue: capacity 1, drops oldest frame on overflow
    val frameQueue = LinkedBlockingQueue<ByteArray>(1)

    @Volatile
    var isRunning = false

    var displayWidth = 0
        private set
    var displayHeight = 0
        private set

    private var renderThread: Thread? = null

    // EGL state
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // GL state
    private var program = 0
    private var textureId = 0
    private var vertexBuffer: FloatBuffer? = null

    // Fullscreen quad vertices (position.x, position.y)
    private val quadVertices = floatArrayOf(
        -1f,  1f,   // top-left
        -1f, -1f,   // bottom-left
         1f,  1f,   // top-right
         1f, -1f    // bottom-right
    )

    private val vertexShaderSource = """
        attribute vec2 aPosition;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aPosition * 0.5 + 0.5;
            vTexCoord.y = 1.0 - vTexCoord.y;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    fun start(surface: Surface, width: Int, height: Int) {
        if (isRunning) return
        displayWidth = width
        displayHeight = height
        isRunning = true
        renderThread = Thread(RenderLoop(surface), "GLRender").apply { isDaemon = true; start() }
        Log.d(TAG, "Renderer started: ${width}x${height}")
    }

    fun stop() {
        isRunning = false
        renderThread?.interrupt()
        renderThread?.join(1000)
        renderThread = null
        frameQueue.clear()
        Log.d(TAG, "Renderer stopped")
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        displayWidth = width
        displayHeight = height
    }

    private inner class RenderLoop(private val surface: Surface) : Runnable {
        override fun run() {
            if (!initEGL(surface)) return
            if (!initGL()) {
                cleanupGL()
                cleanupEGL()
                return
            }

            while (isRunning) {
                val frame = try {
                    frameQueue.poll(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    null
                } ?: continue

                renderFrame(frame)
            }

            cleanupGL()
            cleanupEGL()
        }
    }

    private fun initEGL(surface: Surface): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return false
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = null
            return false
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
            Log.e(TAG, "eglChooseConfig failed")
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = null
            return false
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed")
            cleanupEGL()
            return false
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed")
            cleanupEGL()
            return false
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.e(TAG, "eglMakeCurrent failed: ${EGL14.eglGetError()}")
            cleanupEGL()
            return false
        }

        GLES20.glViewport(0, 0, displayWidth, displayHeight)
        Log.d(TAG, "EGL initialized: ${displayWidth}x${displayHeight}")
        return true
    }

    private fun initGL(): Boolean {
        program = createProgram(vertexShaderSource, fragmentShaderSource)
        if (program == 0) return false

        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vertexBuffer!!.position(0)

        // Create texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        clearFrame()
        return true
    }

    private fun renderFrame(rgba: ByteArray) {
        if (eglDisplay == null || eglSurface == null) return

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            displayWidth, displayHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.wrap(rgba)
        )

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        vertexBuffer!!.position(0)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)

        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun clearFrame() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (eglDisplay != null && eglSurface != null) {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource) ?: return 0
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource) ?: return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glDetachShader(program, vertexShader)
        GLES20.glDetachShader(program, fragmentShader)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    private fun cleanupGL() {
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        GLES20.glDeleteProgram(program)
    }

    private fun cleanupEGL() {
        if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        eglSurface = null
        eglContext = null
        eglDisplay = null
    }
}
