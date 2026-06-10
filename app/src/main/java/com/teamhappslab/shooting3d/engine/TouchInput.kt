package com.teamhappslab.shooting3d.engine

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * UIスレッド→ゲームスレッドへのlock-free入力受け渡し。
 * 移動デルタはAtomicLongに固定小数点(×64)で2値パック。
 */
class TouchInput {
    /** 上位32bit=dx*64, 下位32bit=dy*64 (px) */
    private val moveDelta = AtomicLong(0L)

    /** 移動用ポインタの本数（2本以上で低速モード） */
    val movePointerCount = AtomicInteger(0)

    /** SLOWボタンが押されているか */
    val slowHeld = AtomicBoolean(false)

    /** BOMBボタン押下回数（エッジ） */
    val bombPresses = AtomicInteger(0)

    /** バックキー押下回数 */
    val backPresses = AtomicInteger(0)

    /** タップイベント（画面座標、メニュー操作用） */
    val tapSeq = AtomicInteger(0)
    private val tapPos = AtomicLong(0L)

    fun addMove(dxPx: Float, dyPx: Float) {
        val adx = (dxPx * 64f).toInt()
        val ady = (dyPx * 64f).toInt()
        while (true) {
            val cur = moveDelta.get()
            val cx = (cur shr 32).toInt()
            val cy = cur.toInt()
            val nv = ((cx + adx).toLong() shl 32) or ((cy + ady).toLong() and 0xFFFFFFFFL)
            if (moveDelta.compareAndSet(cur, nv)) return
        }
    }

    /** 消費して(dx,dy)pxを返す。結果はoutに格納（アロケーション回避） */
    fun consumeMove(out: FloatArray) {
        val v = moveDelta.getAndSet(0L)
        out[0] = (v shr 32).toInt() / 64f
        out[1] = v.toInt() / 64f
    }

    fun pushTap(xPx: Float, yPx: Float) {
        val v = (xPx.toInt().toLong() shl 32) or (yPx.toInt().toLong() and 0xFFFFFFFFL)
        tapPos.set(v)
        tapSeq.incrementAndGet()
    }

    /** out[0]=x, out[1]=y。新タップがあればtrue */
    fun consumeTap(lastSeq: Int, out: FloatArray): Boolean {
        val seq = tapSeq.get()
        if (seq == lastSeq) return false
        val v = tapPos.get()
        out[0] = ((v shr 32).toInt()).toFloat()
        out[1] = (v.toInt()).toFloat()
        return true
    }
}

/**
 * HUDボタン配置（UIスレッドの当たり判定とGL描画で共有する定数）。
 * すべて画面幅/高さに対する比率。
 */
object HudLayout {
    // BOMBボタン（左下・円）
    const val BOMB_CX = 0.13f
    const val BOMB_CY = 0.90f   // 高さ比
    const val BOMB_R = 0.105f   // 幅比

    // SLOWボタン（右下・円）
    const val SLOW_CX = 0.87f
    const val SLOW_CY = 0.90f
    const val SLOW_R = 0.105f

    // ポーズボタン（右上・正方形領域）
    const val PAUSE_X = 0.88f
    const val PAUSE_Y = 0.025f
    const val PAUSE_S = 0.10f
}
