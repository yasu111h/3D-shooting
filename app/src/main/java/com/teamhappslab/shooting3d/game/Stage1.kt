package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath

/** 道中スポーンイベント */
class SpawnEvent(
    val time: Float,
    val type: Int,
    val x: Float, val z: Float,
    val pathId: Int,
    val p0: Float, val p1: Float, val p2: Float, val p3: Float,
    val defA: EmitterDef?, val defB: EmitterDef? = null
)

/**
 * ステージ1データ（道中スクリプト＋中ボス＋ボスフェーズ）。
 * すべて起動時に構築（ゲーム中アロケーションゼロ）。
 *
 * 敵弾色は暖色のみ:
 * 小玉=#FF4080 米弾=#FF2020 大玉=#FF00FF クナイ=#FFA000 特殊=#80FF40
 */
object Stage1 {
    const val MIDBOSS_TIME = 52f
    const val BOSS_TIME = 95f

    // ---- 道中ザコ用弾幕 ----
    private val aim1 = EmitterDef(
        interval = 1.6f, burstCount = 1, repeat = 4,
        angleBase = AIM, speed = 7.5f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, scale = 0.8f, radius = 0.22f
    )
    private val aim3way = EmitterDef(
        interval = 1.8f, burstCount = 3, repeat = 3,
        angleBase = AIM, angleRange = 0.5f, speed = 7f,
        sprite = BulletPool.SPR_RICE, color = 0xFF2020, scale = 0.9f, radius = 0.18f
    )
    private val scatter = EmitterDef(
        interval = 0.9f, burstCount = 4, repeat = 5,
        angleBase = AIM, angleJitter = 0.7f, speed = 5.5f, speedRange = 3f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, scale = 0.7f, radius = 0.2f
    )
    private val ring16 = EmitterDef(
        interval = 2.4f, burstCount = 16, repeat = 2,
        angleBase = 0f, angleRange = FastMath.TWO_PI, speed = 5f,
        sprite = BulletPool.SPR_BALL, color = 0xFF00FF, scale = 0.85f, radius = 0.24f
    )
    private val kunai5 = EmitterDef(
        interval = 1.4f, burstCount = 5, repeat = 4,
        angleBase = AIM, angleRange = 0.9f, speed = 8.5f,
        sprite = BulletPool.SPR_KUNAI, color = 0xFFA000, scale = 0.95f, radius = 0.18f
    )

    // ---- 中ボス弾幕 ----
    private val midSpiral = EmitterDef(
        interval = 0.10f, burstCount = 4,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.22f,
        speed = 5.5f,
        sprite = BulletPool.SPR_RICE, color = 0xFF2020, colorB = 0xFFA000,
        scale = 0.85f, radius = 0.18f
    )
    private val midAim = EmitterDef(
        interval = 1.5f, burstCount = 5,
        angleBase = AIM, angleRange = 0.7f, speed = 8f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, scale = 0.8f, radius = 0.22f
    )

    /** 道中スクリプト（時刻順） */
    val events: Array<SpawnEvent> = buildEvents()

    private fun buildEvents(): Array<SpawnEvent> {
        val list = ArrayList<SpawnEvent>()
        // wave1: 左右からザコ降下（自機狙い単発）
        var t = 3f
        var i = 0
        while (i < 5) {
            list.add(SpawnEvent(t + i * 0.5f, 0, -6f + i * 1.2f, -24f,
                Enemy.PATH_DESCEND_HOLD_LEAVE, -14f, 4f, 2f, 0f, aim1))
            i++
        }
        t = 8f
        i = 0
        while (i < 5) {
            list.add(SpawnEvent(t + i * 0.5f, 0, 6f - i * 1.2f, -24f,
                Enemy.PATH_DESCEND_HOLD_LEAVE, -13f, 4f, -2f, 0f, aim1))
            i++
        }
        // wave2: 横切りスイープ（米弾3way）
        t = 14f
        i = 0
        while (i < 6) {
            val fromLeft = (i and 1) == 0
            list.add(SpawnEvent(t + i * 0.9f, 0,
                if (fromLeft) -12.5f else 12.5f, -16f - (i % 3),
                Enemy.PATH_SWEEP, if (fromLeft) 5.5f else -5.5f, 2.5f, 2f, 0f, aim3way))
            i++
        }
        // wave3: 中型機2機（リング）＋ザコ突進
        t = 22f
        list.add(SpawnEvent(t, 1, -4.5f, -24f, Enemy.PATH_DESCEND_HOLD_LEAVE, -15f, 8f, 1f, 0f, ring16, scatter))
        list.add(SpawnEvent(t + 1.5f, 1, 4.5f, -24f, Enemy.PATH_DESCEND_HOLD_LEAVE, -15f, 8f, -1f, 0f, ring16, scatter))
        i = 0
        while (i < 6) {
            list.add(SpawnEvent(t + 3f + i * 0.7f, 0, -8f + i * 3.2f, -25f,
                Enemy.PATH_DIVE, 11f, 0f, 0f, 0f, null))
            i++
        }
        // wave4: クナイ部隊
        t = 34f
        i = 0
        while (i < 6) {
            list.add(SpawnEvent(t + i * 0.6f, 0, -7f + i * 2.8f, -24f,
                Enemy.PATH_DESCEND_HOLD_LEAVE, -12f - (i % 2) * 3f, 5f, 1.5f, 0f, kunai5))
            i++
        }
        // wave5: 横切り大量＋中型
        t = 42f
        i = 0
        while (i < 8) {
            val fromLeft = i < 4
            list.add(SpawnEvent(t + i * 0.55f, 0,
                if (fromLeft) -12.5f else 12.5f, -18f + (i % 4),
                Enemy.PATH_SWEEP, if (fromLeft) 6.5f else -6.5f, 2f, 3f, 0f, aim1))
            i++
        }
        list.add(SpawnEvent(t + 4f, 1, 0f, -24f, Enemy.PATH_DESCEND_HOLD_LEAVE, -14f, 7f, 2f, 0f, ring16, kunai5))
        // 中ボス（52秒）
        list.add(SpawnEvent(MIDBOSS_TIME, 2, 0f, -24f, Enemy.PATH_MIDBOSS, -13f, 0f, 0f, 0f, midSpiral, midAim))
        // wave6: 中ボス後の波状（70秒〜）
        t = 72f
        i = 0
        while (i < 8) {
            list.add(SpawnEvent(t + i * 0.7f, 0, -8f + (i % 4) * 5.3f, -24f,
                Enemy.PATH_DESCEND_HOLD_LEAVE, -13f - (i % 3) * 2f, 4f, 2f, 0f, scatter))
            i++
        }
        t = 82f
        i = 0
        while (i < 6) {
            val fromLeft = (i and 1) == 0
            list.add(SpawnEvent(t + i * 0.8f, 0,
                if (fromLeft) -12.5f else 12.5f, -17f,
                Enemy.PATH_SWEEP, if (fromLeft) 6f else -6f, 2.5f, 2.5f, 0f, aim3way))
            i++
        }
        list.sortBy { it.time }
        return list.toTypedArray()
    }

    // ================= ボス「緋焔のヴェスパー」 =================

    /** 通常1: 自機狙い5wayの連射＋ばらまき */
    private val n1a = EmitterDef(
        interval = 0.65f, burstCount = 5,
        angleBase = AIM, angleRange = 0.85f, speed = 7.5f,
        sprite = BulletPool.SPR_RICE, color = 0xFF2020, scale = 0.9f, radius = 0.18f
    )
    private val n1b = EmitterDef(
        interval = 0.22f, burstCount = 2,
        angleBase = AIM, angleJitter = 1.1f, speed = 5f, speedRange = 3.5f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, scale = 0.72f, radius = 0.2f
    )

    /** スペカ1「紅蓮符「緋色の螺旋」」: 4本腕の渦巻き＋逆回転渦巻き */
    private val s1a = EmitterDef(
        interval = 0.075f, burstCount = 4,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.17f,
        speed = 5.2f,
        sprite = BulletPool.SPR_RICE, color = 0xFF2020, scale = 0.88f, radius = 0.18f
    )
    private val s1b = EmitterDef(
        interval = 0.075f, burstCount = 4,
        angleBase = 0.4f, angleRange = FastMath.TWO_PI, angleRate = -0.17f,
        speed = 4.2f,
        sprite = BulletPool.SPR_BALL, color = 0xFFA000, scale = 0.78f, radius = 0.2f
    )

    /** 通常2: 大玉リング＋自機狙いクナイ */
    private val n2a = EmitterDef(
        interval = 1.6f, burstCount = 12,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.26f,
        speed = 4.5f,
        sprite = BulletPool.SPR_ORB, color = 0xFF00FF, scale = 1.5f, radius = 0.42f
    )
    private val n2b = EmitterDef(
        interval = 0.5f, burstCount = 3,
        angleBase = AIM, angleRange = 0.4f, speed = 9f,
        sprite = BulletPool.SPR_KUNAI, color = 0xFFA000, scale = 0.95f, radius = 0.18f
    )

    /** スペカ2「焔符「火車輪・八重」」: 八重リング（多重速度）＋加速渦 */
    private val s2a = EmitterDef(
        interval = 1.9f, burstCount = 24, speedLayers = 3, speedRange = 2.4f,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.4f,
        speed = 3.2f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, colorB = 0xFF00FF,
        scale = 0.82f, radius = 0.22f
    )
    private val s2b = EmitterDef(
        interval = 0.12f, burstCount = 2,
        angleBase = 0f, angleRange = FastMath.PI, angleRate = 0.09f, angleRateAccel = 0.004f,
        speed = 2.5f, accel = 2.2f, speedCap = 8f,
        sprite = BulletPool.SPR_RICE, color = 0xFFA000, scale = 0.85f, radius = 0.18f
    )

    /** スペカ3「終符「真紅の大花火」」: 大玉花火（リング炸裂風の多重リング高密度）＋自機狙い隙間弾 */
    private val s3a = EmitterDef(
        interval = 2.2f, burstCount = 32, speedLayers = 4, speedRange = 3.0f,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.55f,
        speed = 2.6f,
        sprite = BulletPool.SPR_BALL, color = 0xFF2020, colorB = 0xFF4080,
        scale = 0.8f, radius = 0.21f
    )
    private val s3b = EmitterDef(
        interval = 1.1f, burstCount = 8,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = -0.31f,
        speed = 4.8f, angularVel = 0.35f,
        sprite = BulletPool.SPR_ORB, color = 0xFF00FF, scale = 1.35f, radius = 0.38f
    )
    private val s3c = EmitterDef(
        interval = 0.8f, burstCount = 3,
        angleBase = AIM, angleRange = 0.32f, speed = 8.5f,
        sprite = BulletPool.SPR_KUNAI, color = 0xFFA000, scale = 0.95f, radius = 0.18f
    )

    fun buildBossPhases(): Array<BossPhase> = arrayOf(
        BossPhase(280f, 30f, false, -1, 0, n1a, n1b),
        BossPhase(330f, 40f, true, Labels.SPELL1, 2, s1a, s1b),
        BossPhase(300f, 30f, false, -1, 1, n2a, n2b),
        BossPhase(360f, 45f, true, Labels.SPELL2, 0, s2a, s2b),
        BossPhase(420f, 50f, true, Labels.SPELL3, 2, s3a, s3b, s3c)
    )

    // タイトル画面デモ用弾幕（中央から渦巻き）
    val titleDemo = EmitterDef(
        interval = 0.06f, burstCount = 3,
        angleBase = 0f, angleRange = FastMath.TWO_PI, angleRate = 0.13f, angleRateAccel = 0.0007f,
        speed = 4f,
        sprite = BulletPool.SPR_BALL, color = 0xFF4080, colorB = 0xFFA000,
        scale = 0.8f, radius = 0.2f
    )
}
