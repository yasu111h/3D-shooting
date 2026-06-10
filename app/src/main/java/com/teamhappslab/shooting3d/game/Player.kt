package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath

/** 自機ショットプール（SoA・swap-remove） */
class PlayerShots(val max: Int) {
    var count = 0
        private set
    val posX = FloatArray(max)
    val posZ = FloatArray(max)
    val prevX = FloatArray(max)
    val prevZ = FloatArray(max)
    val velX = FloatArray(max)
    val velZ = FloatArray(max)

    fun spawn(x: Float, z: Float, vx: Float, vz: Float) {
        if (count >= max) return
        val i = count
        count++
        posX[i] = x; posZ[i] = z
        prevX[i] = x; prevZ[i] = z
        velX[i] = vx; velZ[i] = vz
    }

    fun kill(i: Int) {
        val last = count - 1
        if (i != last) {
            posX[i] = posX[last]; posZ[i] = posZ[last]
            prevX[i] = prevX[last]; prevZ[i] = prevZ[last]
            velX[i] = velX[last]; velZ[i] = velZ[last]
        }
        count = last
    }

    fun clear() {
        count = 0
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            prevX[i] = posX[i]; prevZ[i] = posZ[i]
            posX[i] += velX[i] * dt
            posZ[i] += velZ[i] * dt
            if (posZ[i] < World.BOUND_Z_FAR || posX[i] < -World.BOUND_X || posX[i] > World.BOUND_X) {
                kill(i)
            } else {
                i++
            }
        }
    }
}

/**
 * 自機。相対タッチ移動・オートショット・低速モード。
 * 自機色: シアン #40F0FF（コア白）
 */
class Player {
    var x = 0f
    var z = 5.5f
    var prevX = 0f
    var prevZ = 5.5f
    var alive = true
    var invulnTimer = 0f
    var slowMode = false
    var shotTimer = 0f
    var shotCounter = 0   // SE間引き用

    val hitRadius = 0.13f
    val grazeRadius = 0.62f

    fun reset() {
        x = 0f; z = 5.5f
        prevX = x; prevZ = z
        alive = true
        invulnTimer = 0f
        slowMode = false
        shotTimer = 0f
        shotCounter = 0
    }

    fun respawn() {
        x = 0f; z = 6.5f
        prevX = x; prevZ = z
        alive = true
        invulnTimer = 3f   // 無敵3秒点滅
    }

    /** 移動デルタ適用（world単位） */
    fun move(dx: Float, dz: Float) {
        if (!alive) return
        val k = if (slowMode) 0.42f else 1f
        x = FastMath.clamp(x + dx * k, -World.FIELD_X, World.FIELD_X)
        z = FastMath.clamp(z + dz * k, World.PLAYER_Z_MIN, World.PLAYER_Z_MAX)
    }

    /** @return ショット音を鳴らすべきならtrue */
    fun update(dt: Float, shots: PlayerShots): Boolean {
        prevX = x; prevZ = z
        if (invulnTimer > 0f) invulnTimer -= dt
        if (!alive) return false
        var playSfx = false
        shotTimer -= dt
        while (shotTimer <= 0f) {
            shotTimer += 0.055f
            val speed = 42f
            if (slowMode) {
                // 集中ショット（細く前方4本）
                shots.spawn(x - 0.35f, z - 0.4f, 0f, -speed)
                shots.spawn(x + 0.35f, z - 0.4f, 0f, -speed)
                shots.spawn(x - 0.12f, z - 0.55f, 0f, -speed)
                shots.spawn(x + 0.12f, z - 0.55f, 0f, -speed)
            } else {
                // 通常ショット（広め3way）
                shots.spawn(x - 0.4f, z - 0.4f, -2.5f, -speed)
                shots.spawn(x, z - 0.6f, 0f, -speed)
                shots.spawn(x + 0.4f, z - 0.4f, 2.5f, -speed)
            }
            shotCounter++
            if (shotCounter % 3 == 0) playSfx = true  // 3フレームに1回まで
        }
        return playSfx
    }
}
