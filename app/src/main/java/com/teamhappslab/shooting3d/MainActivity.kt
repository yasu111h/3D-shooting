package com.teamhappslab.shooting3d

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import com.teamhappslab.shooting3d.audio.Sfx
import com.teamhappslab.shooting3d.engine.GameThread
import com.teamhappslab.shooting3d.engine.HudLayout
import com.teamhappslab.shooting3d.engine.TouchInput
import com.teamhappslab.shooting3d.game.GameWorld
import com.teamhappslab.shooting3d.game.ScorePersist

class MainActivity : Activity() {
    private lateinit var gameView: GameView
    private var sfx: Sfx? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = GameView(this)
        setContentView(gameView)
        sfx = Sfx(this)
        gameView.world.sfx = sfx
        gameView.world.persist = object : ScorePersist {
            override fun loadHiScore(): Long =
                getSharedPreferences("score", Context.MODE_PRIVATE).getLong("hiscore", 0L)
            override fun saveHiScore(v: Long) {
                getSharedPreferences("score", Context.MODE_PRIVATE).edit().putLong("hiscore", v).apply()
            }
        }
        hideSystemUi()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        gameView.input.backPresses.incrementAndGet()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx?.release()
    }
}

/**
 * SurfaceView。UIスレッドは入力受付のみ（lock-freeでGameThreadへ渡す）。
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    val input = TouchInput()
    val world = GameWorld()
    private var thread: GameThread? = null

    // ポインタ追跡（最大10本）
    private val ptrIds = IntArray(10) { -1 }
    private val ptrLastX = FloatArray(10)
    private val ptrLastY = FloatArray(10)
    private val ptrKind = IntArray(10)  // 0=移動 1=BOMB 2=SLOW 3=PAUSE領域

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        thread?.quit()
        thread?.join(1000)
        thread = GameThread(holder.surface, w, h, world, input).also { it.start() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.quit()
        thread?.join(1500)
        thread = null
    }

    private fun classify(x: Float, y: Float): Int {
        val w = width.toFloat()
        val h = height.toFloat()
        // BOMB円
        run {
            val dx = x - w * HudLayout.BOMB_CX
            val dy = y - h * HudLayout.BOMB_CY
            val r = w * (HudLayout.BOMB_R + 0.03f)
            if (dx * dx + dy * dy < r * r) return 1
        }
        // SLOW円
        run {
            val dx = x - w * HudLayout.SLOW_CX
            val dy = y - h * HudLayout.SLOW_CY
            val r = w * (HudLayout.SLOW_R + 0.03f)
            if (dx * dx + dy * dy < r * r) return 2
        }
        return 0
    }

    private fun slotOf(pointerId: Int): Int {
        var i = 0
        while (i < 10) {
            if (ptrIds[i] == pointerId) return i
            i++
        }
        return -1
    }

    private fun freeSlot(): Int {
        var i = 0
        while (i < 10) {
            if (ptrIds[i] == -1) return i
            i++
        }
        return -1
    }

    private fun recountMovePointers() {
        var n = 0
        var slows = false
        var i = 0
        while (i < 10) {
            if (ptrIds[i] != -1) {
                if (ptrKind[i] == 0) n++
                if (ptrKind[i] == 2) slows = true
            }
            i++
        }
        input.movePointerCount.set(n)
        input.slowHeld.set(slows)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                val slot = freeSlot()
                if (slot >= 0) {
                    ptrIds[slot] = pid
                    ptrLastX[slot] = x
                    ptrLastY[slot] = y
                    val kind = classify(x, y)
                    ptrKind[slot] = kind
                    if (kind == 1) input.bombPresses.incrementAndGet()
                    recountMovePointers()
                }
                // タップイベント（メニュー・ポーズ用）
                input.pushTap(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                var i = 0
                val pc = event.pointerCount
                while (i < pc) {
                    val pid = event.getPointerId(i)
                    val slot = slotOf(pid)
                    if (slot >= 0) {
                        val x = event.getX(i)
                        val y = event.getY(i)
                        if (ptrKind[slot] == 0) {
                            input.addMove(x - ptrLastX[slot], y - ptrLastY[slot])
                        }
                        ptrLastX[slot] = x
                        ptrLastY[slot] = y
                    }
                    i++
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val slot = slotOf(pid)
                if (slot >= 0) {
                    ptrIds[slot] = -1
                    recountMovePointers()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                var i = 0
                while (i < 10) {
                    ptrIds[i] = -1
                    i++
                }
                recountMovePointers()
            }
        }
        return true
    }
}
