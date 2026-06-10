package com.teamhappslab.shooting3d.gfx

import android.opengl.Matrix
import com.teamhappslab.shooting3d.engine.FastMath

/**
 * 俯瞰チルトカメラ（約55度）。プレイ平面はy=0のXZ平面。
 * 画面シェイク（ノイズ駆動・指数減衰）とステージ開始フライスルーを担当。
 */
class Camera {
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val right = FloatArray(3)
    val up = FloatArray(3)

    private var aspect = 9f / 16f

    fun setViewport(w: Int, h: Int) {
        aspect = w.toFloat() / h.toFloat()
        Matrix.perspectiveM(proj, 0, 46f, aspect, 1f, 150f)
    }

    /**
     * @param trauma 0..1 シェイク強度
     * @param intro 0..1 ステージ開始フライスルー進行（1=開始直後）
     */
    fun update(time: Float, trauma: Float, intro: Float) {
        // シェイク: 擬似Perlin（複数sin合成）× trauma^2
        val amp = trauma * trauma * 0.65f
        val sx = (FastMath.sin(time * 41f) * 0.6f + FastMath.sin(time * 67f + 1.3f) * 0.4f) * amp
        val sy = (FastMath.sin(time * 53f + 2.1f) * 0.6f + FastMath.sin(time * 79f + 0.7f) * 0.4f) * amp

        // 基本: eye(0,31,15) → lookAt(0,0,-6.5) 俯瞰約55度
        var ey = 31f
        var ez = 15f
        var cy = 0f
        var cz = -6.5f
        if (intro > 0f) {
            // フライスルー: 低空後方から引き上げる
            val t = 1f - intro  // 0→1
            val e = t * t * (3f - 2f * t)
            ey = FastMath.lerp(10f, 31f, e)
            ez = FastMath.lerp(26f, 15f, e)
            cz = FastMath.lerp(-18f, -6.5f, e)
        }
        Matrix.setLookAtM(view, 0,
            sx, ey + sy, ez,
            sx * 0.4f, cy + sy * 0.4f, cz,
            0f, 1f, 0f)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        right[0] = view[0]; right[1] = view[4]; right[2] = view[8]
        up[0] = view[1]; up[1] = view[5]; up[2] = view[9]
    }
}
