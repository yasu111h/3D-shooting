package com.teamhappslab.shooting3d.engine

/**
 * 高速数学ユーティリティ。
 * sin/cos は1024分割テーブル参照。ゲームループ内アロケーションゼロ。
 */
object FastMath {
    const val PI = 3.1415927f
    const val TWO_PI = 6.2831855f
    const val HALF_PI = 1.5707964f

    private const val SIZE = 1024
    private const val MASK = SIZE - 1
    private const val TO_INDEX = SIZE / TWO_PI
    private val sinTable = FloatArray(SIZE)

    init {
        var i = 0
        while (i < SIZE) {
            sinTable[i] = kotlin.math.sin(i * TWO_PI.toDouble() / SIZE).toFloat()
            i++
        }
    }

    fun sin(a: Float): Float {
        val x = a * TO_INDEX
        var idx = x.toInt()
        if (x < 0f) idx--
        return sinTable[idx and MASK]
    }

    fun cos(a: Float): Float = sin(a + HALF_PI)

    /** 高速atan2近似（弾の向き計算用、誤差0.01rad以下） */
    fun atan2(y: Float, x: Float): Float {
        if (x == 0f && y == 0f) return 0f
        val ax = if (x >= 0f) x else -x
        val ay = if (y >= 0f) y else -y
        val a = (if (ax > ay) ay / ax else ax / ay)
        val s = a * a
        var r = ((-0.0464964749f * s + 0.15931422f) * s - 0.327622764f) * s * a + a
        if (ay > ax) r = HALF_PI - r
        if (x < 0f) r = PI - r
        if (y < 0f) r = -r
        return r
    }

    fun clamp(v: Float, min: Float, max: Float): Float =
        if (v < min) min else if (v > max) max else v

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/** シード付きXorShift乱数（再現性保証） */
class XorShift(seed: Long = 88172645463325252L) {
    private var s: Long = if (seed == 0L) 88172645463325252L else seed

    fun reseed(seed: Long) {
        s = if (seed == 0L) 88172645463325252L else seed
    }

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    /** [0,1) */
    fun nextFloat(): Float = ((nextLong() ushr 40).toInt()) * (1f / 16777216f)

    /** [min,max) */
    fun range(min: Float, max: Float): Float = min + nextFloat() * (max - min)

    /** [0,n) */
    fun nextInt(n: Int): Int = ((nextLong() ushr 33) % n).toInt()
}
