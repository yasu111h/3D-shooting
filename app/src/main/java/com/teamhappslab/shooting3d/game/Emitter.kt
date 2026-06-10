package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath
import com.teamhappslab.shooting3d.engine.XorShift

/** angleBase にこれを指定すると自機狙い（AIM） */
const val AIM = 999f

/**
 * 弾幕パターン定義（データ駆動DSL）。
 * リング・渦巻き・nWAY自機狙い・ばらまき・多重リング・花型を表現できる。
 * 角度は rad。0 = 自機方向(+Z)。
 */
class EmitterDef(
    val interval: Float = 0.5f,       // 発射間隔（秒）
    val burstCount: Int = 1,          // 1回で同時に撃つ弾数（way数/リング弾数）
    val repeat: Int = -1,             // 発射回数（-1=無限）
    val startDelay: Float = 0f,
    val angleBase: Float = AIM,       // 基準角（AIM=自機狙い）
    val angleRange: Float = 0f,       // 全way拡がり角（TWO_PIでリング）
    val angleRate: Float = 0f,        // バーストごとの基準角回転（渦巻きの肝）
    val angleRateAccel: Float = 0f,   // angleRateの加速
    val angleJitter: Float = 0f,      // ばらまきランダム角
    val speed: Float = 6f,
    val speedRange: Float = 0f,       // 速度ランダム幅 / 多重リング時は速度差
    val speedLayers: Int = 1,         // 多重リング層数（speedRangeを層で分割）
    val accel: Float = 0f,
    val angularVel: Float = 0f,       // 弾の進行方向回転
    val speedCap: Float = 0f,
    val sprite: Float = BulletPool.SPR_BALL,
    val color: Int = 0xFF4080,        // 小玉ピンク
    val colorB: Int = 0,              // 0以外なら交互2色
    val scale: Float = 0.8f,
    val radius: Float = 0.22f
)

/**
 * エミッタ実行状態。敵/ボスごとに事前確保しゲーム中はsetで再構成（アロケーションゼロ）。
 */
class Emitter {
    var def: EmitterDef? = null
    private var timer = 0f
    private var bursts = 0
    private var curAngle = 0f
    private var curRate = 0f

    fun set(d: EmitterDef?) {
        def = d
        timer = d?.startDelay ?: 0f
        bursts = 0
        curAngle = 0f
        curRate = d?.angleRate ?: 0f
    }

    /**
     * @param ex,ez エミッタ位置 / px,pz 自機位置
     */
    fun update(dt: Float, ex: Float, ez: Float, px: Float, pz: Float,
               bullets: BulletPool, rng: XorShift, rank: Float) {
        val d = def ?: return
        if (d.repeat >= 0 && bursts >= d.repeat) return
        timer -= dt
        while (timer <= 0f) {
            fire(d, ex, ez, px, pz, bullets, rng, rank)
            bursts++
            if (d.repeat >= 0 && bursts >= d.repeat) return
            timer += d.interval
        }
    }

    private fun fire(d: EmitterDef, ex: Float, ez: Float, px: Float, pz: Float,
                     bullets: BulletPool, rng: XorShift, rank: Float) {
        var base = if (d.angleBase == AIM) FastMath.atan2(px - ex, pz - ez) else d.angleBase
        base += curAngle
        curRate += d.angleRateAccel
        curAngle += curRate

        val n = d.burstCount
        val layers = if (d.speedLayers < 1) 1 else d.speedLayers
        var layer = 0
        while (layer < layers) {
            val spd0 = if (layers > 1)
                d.speed + d.speedRange * layer / (layers - 1).toFloat()
            else d.speed
            var k = 0
            while (k < n) {
                var a = base
                if (n > 1) {
                    a += if (d.angleRange >= FastMath.TWO_PI - 0.001f) {
                        // 完全リング: 均等配置（端の重複なし）
                        d.angleRange * k / n
                    } else {
                        -d.angleRange * 0.5f + d.angleRange * k / (n - 1).toFloat()
                    }
                }
                if (d.angleJitter > 0f) a += rng.range(-d.angleJitter, d.angleJitter)
                var spd = spd0 * rank
                if (layers == 1 && d.speedRange > 0f) spd += rng.range(0f, d.speedRange)
                val col = if (d.colorB != 0 && (k and 1) == 1) d.colorB else d.color
                bullets.spawn(
                    ex, ez, a, spd, d.accel, d.angularVel, d.speedCap,
                    d.sprite, col, d.scale, d.radius
                )
                k++
            }
            layer++
        }
    }
}
