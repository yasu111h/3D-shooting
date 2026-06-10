package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath

/**
 * 弾プール（SoA FloatArray構造・swap-remove密配列）。
 * ゲームループ内アロケーションゼロ。外部から弾インデックスを保持してはならない。
 */
class BulletPool(val max: Int) {
    var count = 0
        private set

    val posX = FloatArray(max)
    val posY = FloatArray(max)   // 高度（通常0）
    val posZ = FloatArray(max)
    val prevX = FloatArray(max)
    val prevY = FloatArray(max)
    val prevZ = FloatArray(max)
    val velX = FloatArray(max)
    val velY = FloatArray(max)
    val velZ = FloatArray(max)
    val accX = FloatArray(max)
    val accY = FloatArray(max)
    val accZ = FloatArray(max)
    val angularVel = FloatArray(max)  // 速度ベクトル回転 rad/s
    val speedCap = FloatArray(max)    // 0=制限なし
    val scale = FloatArray(max)       // 描画クアッドサイズ
    val colR = FloatArray(max)
    val colG = FloatArray(max)
    val colB = FloatArray(max)
    val spriteId = FloatArray(max)    // 0小玉 1米弾 2大玉 3クナイ
    val rotation = FloatArray(max)
    val rotSpeed = FloatArray(max)
    val radius = FloatArray(max)      // 当たり判定半径
    val age = FloatArray(max)
    val flags = IntArray(max)         // bit0=アイテム化 bit1=グレイズ済み

    companion object {
        const val FLAG_ITEM = 1
        const val FLAG_GRAZED = 2
        const val SPR_BALL = 0f
        const val SPR_RICE = 1f
        const val SPR_ORB = 2f
        const val SPR_KUNAI = 3f
    }

    /** 弾を発射。満杯時は-1 */
    fun spawn(
        x: Float, z: Float, angle: Float, speed: Float,
        accel: Float, angVel: Float, cap: Float,
        sprite: Float, color: Int, scl: Float, rad: Float,
        y: Float = 0f, ySpd: Float = 0f
    ): Int {
        if (count >= max) return -1
        val i = count
        count++
        val s = FastMath.sin(angle)
        val c = FastMath.cos(angle)
        posX[i] = x; posY[i] = y; posZ[i] = z
        prevX[i] = x; prevY[i] = y; prevZ[i] = z
        velX[i] = s * speed; velY[i] = ySpd; velZ[i] = c * speed
        accX[i] = s * accel; accY[i] = 0f; accZ[i] = c * accel
        angularVel[i] = angVel
        speedCap[i] = cap
        scale[i] = scl
        colR[i] = ((color shr 16) and 0xFF) / 255f
        colG[i] = ((color shr 8) and 0xFF) / 255f
        colB[i] = (color and 0xFF) / 255f
        spriteId[i] = sprite
        rotation[i] = angle
        rotSpeed[i] = if (sprite == SPR_ORB) 2.5f else 0f
        radius[i] = rad
        age[i] = 0f
        flags[i] = 0
        return i
    }

    /** swap-remove。iの位置に末尾要素をコピーしてcountを減らす */
    fun kill(i: Int) {
        val last = count - 1
        if (i != last) {
            posX[i] = posX[last]; posY[i] = posY[last]; posZ[i] = posZ[last]
            prevX[i] = prevX[last]; prevY[i] = prevY[last]; prevZ[i] = prevZ[last]
            velX[i] = velX[last]; velY[i] = velY[last]; velZ[i] = velZ[last]
            accX[i] = accX[last]; accY[i] = accY[last]; accZ[i] = accZ[last]
            angularVel[i] = angularVel[last]
            speedCap[i] = speedCap[last]
            scale[i] = scale[last]
            colR[i] = colR[last]; colG[i] = colG[last]; colB[i] = colB[last]
            spriteId[i] = spriteId[last]
            rotation[i] = rotation[last]
            rotSpeed[i] = rotSpeed[last]
            radius[i] = radius[last]
            age[i] = age[last]
            flags[i] = flags[last]
        }
        count = last
    }

    fun clear() {
        count = 0
    }

    /**
     * 全弾更新。境界外の弾はswap-removeで除去。
     * アイテム化弾は自機へ吸引（px,pz=自機位置）。
     */
    fun update(dt: Float, px: Float, pz: Float) {
        var i = 0
        while (i < count) {
            prevX[i] = posX[i]; prevY[i] = posY[i]; prevZ[i] = posZ[i]

            if ((flags[i] and FLAG_ITEM) != 0) {
                // アイテム吸引: 自機へ加速ホーミング
                val dx = px - posX[i]
                val dz = pz - posZ[i]
                val d2 = dx * dx + dz * dz
                val inv = 1f / kotlin.math.sqrt(d2 + 0.0001f)
                val spd = 24f + age[i] * 30f
                velX[i] = dx * inv * spd
                velZ[i] = dz * inv * spd
            } else {
                // 加速度
                velX[i] += accX[i] * dt
                velZ[i] += accZ[i] * dt
                velY[i] += accY[i] * dt
                // 角速度（速度ベクトル回転）
                val av = angularVel[i]
                if (av != 0f) {
                    val a = av * dt
                    val s = FastMath.sin(a)
                    val c = FastMath.cos(a)
                    val vx = velX[i]
                    val vz = velZ[i]
                    velX[i] = vx * c + vz * s
                    velZ[i] = -vx * s + vz * c
                    rotation[i] += a
                }
                // 速度上限
                val cap = speedCap[i]
                if (cap > 0f) {
                    val sp2 = velX[i] * velX[i] + velZ[i] * velZ[i]
                    if (sp2 > cap * cap) {
                        val k = cap / kotlin.math.sqrt(sp2)
                        velX[i] *= k
                        velZ[i] *= k
                    }
                }
            }

            posX[i] += velX[i] * dt
            posY[i] += velY[i] * dt
            posZ[i] += velZ[i] * dt
            rotation[i] += rotSpeed[i] * dt
            age[i] += dt

            // 境界外除去
            val x = posX[i]
            val z = posZ[i]
            if (x < -World.BOUND_X || x > World.BOUND_X || z < World.BOUND_Z_FAR || z > World.BOUND_Z_NEAR) {
                kill(i)
                // killでiに新要素が来るので再処理（iは進めない）
            } else {
                i++
            }
        }
    }

    /** 全弾をアイテム化（スペカ撃破時） */
    fun convertAllToItems() {
        var i = 0
        while (i < count) {
            flags[i] = flags[i] or FLAG_ITEM
            age[i] = 0f
            // スコアアイテム色（金）
            colR[i] = 1f; colG[i] = 0.85f; colB[i] = 0.3f
            spriteId[i] = SPR_BALL
            scale[i] = 0.55f
            i++
        }
    }
}

/** ワールド境界定数 */
object World {
    const val FIELD_X = 9.5f       // 自機移動可能半幅
    const val BOUND_X = 13f        // 弾消去境界
    const val BOUND_Z_FAR = -26f
    const val BOUND_Z_NEAR = 11f
    const val PLAYER_Z_MIN = -5f
    const val PLAYER_Z_MAX = 7.5f
}
