package com.teamhappslab.shooting3d.game

/**
 * パーティクルプール（SoA・swap-remove）。
 * フラッシュ／衝撃波リング／破片／グレイズスパークを統合管理。
 */
class ParticlePool(val max: Int) {
    var count = 0
        private set

    val posX = FloatArray(max)
    val posY = FloatArray(max)
    val posZ = FloatArray(max)
    val velX = FloatArray(max)
    val velY = FloatArray(max)
    val velZ = FloatArray(max)
    val life = FloatArray(max)
    val maxLife = FloatArray(max)
    val scale0 = FloatArray(max)   // 生成時サイズ
    val scale1 = FloatArray(max)   // 消滅時サイズ
    val colR = FloatArray(max)
    val colG = FloatArray(max)
    val colB = FloatArray(max)
    val spriteId = FloatArray(max) // 0=グロー円 8=リング
    val drag = FloatArray(max)

    companion object {
        const val SPR_GLOW = 0f
        const val SPR_RING = 8f
    }

    fun spawn(
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        lifeSec: Float, s0: Float, s1: Float,
        r: Float, g: Float, b: Float,
        sprite: Float, dragK: Float
    ) {
        if (count >= max) return
        val i = count
        count++
        posX[i] = x; posY[i] = y; posZ[i] = z
        velX[i] = vx; velY[i] = vy; velZ[i] = vz
        life[i] = lifeSec; maxLife[i] = lifeSec
        scale0[i] = s0; scale1[i] = s1
        colR[i] = r; colG[i] = g; colB[i] = b
        spriteId[i] = sprite
        drag[i] = dragK
    }

    fun kill(i: Int) {
        val last = count - 1
        if (i != last) {
            posX[i] = posX[last]; posY[i] = posY[last]; posZ[i] = posZ[last]
            velX[i] = velX[last]; velY[i] = velY[last]; velZ[i] = velZ[last]
            life[i] = life[last]; maxLife[i] = maxLife[last]
            scale0[i] = scale0[last]; scale1[i] = scale1[last]
            colR[i] = colR[last]; colG[i] = colG[last]; colB[i] = colB[last]
            spriteId[i] = spriteId[last]
            drag[i] = drag[last]
        }
        count = last
    }

    fun clear() {
        count = 0
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                kill(i)
                continue
            }
            val d = 1f - drag[i] * dt
            velX[i] *= d; velY[i] *= d; velZ[i] *= d
            posX[i] += velX[i] * dt
            posY[i] += velY[i] * dt
            posZ[i] += velZ[i] * dt
            i++
        }
    }
}
