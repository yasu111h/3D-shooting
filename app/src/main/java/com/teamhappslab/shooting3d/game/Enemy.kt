package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath
import com.teamhappslab.shooting3d.engine.XorShift

/**
 * 敵（ザコ・中型・中ボス）。固定配列プールで事前確保、ゲーム中newゼロ。
 */
class Enemy {
    var active = false
    var type = 0            // 0=ザコ紫 1=中型橙 2=中ボス
    var x = 0f; var z = 0f
    var prevX = 0f; var prevZ = 0f
    var hp = 0f; var maxHp = 0f
    var t = 0f              // 生存時間
    var pathId = 0
    var p0 = 0f; var p1 = 0f; var p2 = 0f; var p3 = 0f  // パスパラメータ
    var scale = 1f
    var radius = 0.6f
    var colR = 0.7f; var colG = 0.25f; var colB = 1f
    var sprite = 4f
    var score = 100
    var flashT = 0f         // 被弾フラッシュ
    val emitterA = Emitter()
    val emitterB = Emitter()

    companion object {
        // 移動パターン
        const val PATH_DESCEND_HOLD_LEAVE = 0  // 上から降下→停止射撃→上に退避
        const val PATH_SWEEP = 1               // 横切り（sin揺れ）
        const val PATH_DIVE = 2                // 自機方向へ突進
        const val PATH_MIDBOSS = 3             // 中央停止
    }

    fun spawn(type_: Int, x_: Float, z_: Float, pathId_: Int,
              p0_: Float, p1_: Float, p2_: Float, p3_: Float,
              defA: EmitterDef?, defB: EmitterDef?) {
        active = true
        type = type_
        x = x_; z = z_; prevX = x_; prevZ = z_
        t = 0f
        pathId = pathId_
        p0 = p0_; p1 = p1_; p2 = p2_; p3 = p3_
        flashT = 0f
        when (type_) {
            0 -> { // ザコ #B040FF
                hp = 4f; scale = 1.2f; radius = 0.55f; score = 100
                colR = 0xB0 / 255f; colG = 0x40 / 255f; colB = 1f
                sprite = 4f
            }
            1 -> { // 中型 #FF8020
                hp = 24f; scale = 2.0f; radius = 0.95f; score = 500
                colR = 1f; colG = 0x80 / 255f; colB = 0x20 / 255f
                sprite = 4f
            }
            else -> { // 中ボス #FF8020強
                hp = 220f; scale = 3.2f; radius = 1.4f; score = 5000
                colR = 1f; colG = 0x60 / 255f; colB = 0x20 / 255f
                sprite = 5f
            }
        }
        maxHp = hp
        emitterA.set(defA)
        emitterB.set(defB)
    }

    /** @return falseなら退場（除去） */
    fun update(dt: Float, px: Float, pz: Float, bullets: BulletPool,
               rng: XorShift, rank: Float): Boolean {
        prevX = x; prevZ = z
        t += dt
        if (flashT > 0f) flashT -= dt
        when (pathId) {
            PATH_DESCEND_HOLD_LEAVE -> {
                // p0=目標z p1=滞在秒 p2=横揺れ振幅
                if (t < 1.2f) {
                    z += (p0 - z) * 3f * dt
                } else if (t < 1.2f + p1) {
                    x += FastMath.sin(t * 2f) * p2 * dt
                } else {
                    z -= 9f * dt
                    if (z < World.BOUND_Z_FAR + 1f) return false
                }
            }
            PATH_SWEEP -> {
                // p0=横速度(符号で向き) p1=sin振幅 p2=sin周波数
                x += p0 * dt
                z += FastMath.sin(t * p2) * p1 * dt
                if (x < -World.BOUND_X || x > World.BOUND_X) return false
            }
            PATH_DIVE -> {
                // p0=突進速度。1秒待ってから自機方向へ加速
                if (t < 0.8f) {
                    z += 4f * dt
                } else {
                    if (p3 == 0f) {
                        val a = FastMath.atan2(px - x, pz - z)
                        p1 = FastMath.sin(a); p2 = FastMath.cos(a); p3 = 1f
                    }
                    x += p1 * p0 * dt
                    z += p2 * p0 * dt
                    if (z > World.BOUND_Z_NEAR - 1f) return false
                }
            }
            PATH_MIDBOSS -> {
                // 中央付近で左右移動 p0=目標z
                if (t < 2f) {
                    z += (p0 - z) * 2.5f * dt
                } else {
                    x += FastMath.sin(t * 0.8f) * 2.2f * dt
                    x = FastMath.clamp(x, -6f, 6f)
                }
            }
        }
        // 射撃（登場直後は撃たない）
        if (t > 1.0f) {
            emitterA.update(dt, x, z, px, pz, bullets, rng, rank)
            emitterB.update(dt, x, z, px, pz, bullets, rng, rank)
        }
        return true
    }
}
