package com.teamhappslab.shooting3d.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import com.teamhappslab.shooting3d.game.GameWorld
import com.teamhappslab.shooting3d.gfx.Renderer

/**
 * ゲームスレッド。EGLコンテキストを自前管理し、
 * ロジック120Hz固定タイムステップ＋VSync同期補間描画を行う。
 * update+renderは同一スレッド（分離しない）。
 */
class GameThread(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val world: GameWorld,
    private val input: TouchInput
) : Thread("GameThread") {

    @Volatile
    private var running = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private val moveTmp = FloatArray(2)
    private val tapTmp = FloatArray(2)
    private var lastTapSeq = 0

    fun quit() {
        running = false
    }

    override fun run() {
        try {
            initEgl()
            val renderer = Renderer()
            renderer.init(width, height)
            world.setViewport(width.toFloat(), height.toFloat())
            lastTapSeq = input.tapSeq.get()

            val dt = GameWorld.DT
            var accumulator = 0.0
            var lastNs = System.nanoTime()
            var timeSec = 0f

            while (running) {
                val now = System.nanoTime()
                var frame = (now - lastNs) / 1_000_000_000.0
                lastNs = now
                if (frame > 0.1) frame = 0.1  // 長停止時の暴走防止
                timeSec += frame.toFloat()

                // 入力消費
                consumeInput()

                // 実時間演出（ヒットストップ/スロー）→timeScale
                val timeScale = world.advanceRealTime(frame.toFloat())
                accumulator += frame * timeScale
                var steps = 0
                while (accumulator >= dt && steps < 30) {
                    world.step(dt)
                    accumulator -= dt
                    steps++
                }
                val alpha = (accumulator / dt).toFloat().coerceIn(0f, 1f)

                renderer.render(world, alpha, timeSec)

                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    // サーフェス喪失
                    break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GameThread", "fatal", e)
        } finally {
            releaseEgl()
        }
    }

    private fun consumeInput() {
        // 移動
        input.consumeMove(moveTmp)
        if (moveTmp[0] != 0f || moveTmp[1] != 0f) {
            world.onMove(moveTmp[0], moveTmp[1])
        }
        // 低速: SLOWボタン or 2本指
        world.setSlow(input.slowHeld.get() || input.movePointerCount.get() >= 2)
        // ボム
        var bombs = input.bombPresses.getAndSet(0)
        while (bombs > 0) {
            world.onBomb()
            bombs--
        }
        // バックキー
        var backs = input.backPresses.getAndSet(0)
        while (backs > 0) {
            world.onBack()
            backs--
        }
        // タップ
        if (input.consumeTap(lastTapSeq, tapTmp)) {
            lastTapSeq = input.tapSeq.get()
            world.onTap(tapTmp[0], tapTmp[1])
        }
    }

    // ================= EGL =================

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("eglGetDisplay failed")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x40, // ES3 bit
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            || numConfigs[0] == 0) {
            throw RuntimeException("eglChooseConfig failed")
        }
        val config = configs[0]!!
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("eglCreateContext failed")
        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("eglCreateWindowSurface failed")
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
        EGL14.eglSwapInterval(eglDisplay, 1)  // VSync同期
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
