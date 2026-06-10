package com.teamhappslab.shooting3d.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import com.teamhappslab.shooting3d.game.Labels
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * HUDレンダラー。
 * 起動時に全ラベル文字列・数字グリフ・白テクセルを1枚のアトラスへCanvasで焼き込み、
 * フレーム中は頂点バッチに積んで1 draw callで描画する（プレイ中アロケーションゼロ）。
 */
class HudRenderer {
    companion object {
        private const val ATLAS_W = 1024
        private const val ATLAS_H = 2048
        private const val MAX_QUADS = 1024
        private const val VFLOATS = 8  // x,y,u,v,r,g,b,a

        private const val VS = """#version 300 es
layout(location=0) in vec2 aPos;
layout(location=1) in vec2 aUv;
layout(location=2) in vec4 aCol;
uniform vec2 uScreen;
out vec2 vUv;
out vec4 vCol;
void main() {
    gl_Position = vec4(aPos.x * 2.0 / uScreen.x - 1.0, 1.0 - aPos.y * 2.0 / uScreen.y, 0.0, 1.0);
    vUv = aUv;
    vCol = aCol;
}
"""
        private const val FS = """#version 300 es
precision mediump float;
in vec2 vUv;
in vec4 vCol;
uniform sampler2D uTex;
out vec4 fragColor;
void main() {
    fragColor = texture(uTex, vUv) * vCol;
}
"""
        private const val VIG_VS = """#version 300 es
layout(location=0) in vec2 aPos;
out vec2 vUv;
void main() { vUv = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }
"""
        private const val VIG_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform float uRed;
uniform float uWhite;
out vec4 fragColor;
void main() {
    float d = length(vUv);
    // ビネット常時α0.25 ＋ 被弾時赤
    float vig = smoothstep(0.55, 1.45, d) * 0.25 + smoothstep(0.5, 1.4, d) * uRed * 0.55;
    vec3 col = mix(vec3(0.0), vec3(0.5, 0.0, 0.05), uRed > 0.01 ? 1.0 : 0.0);
    // 白フラッシュ（上限α0.8）
    vec3 outc = col * vig + vec3(1.0) * uWhite;
    float a = clamp(vig + uWhite, 0.0, 0.8);
    fragColor = vec4(mix(col, vec3(1.0), uWhite > 0.01 ? uWhite / (vig + uWhite + 0.0001) : 0.0), a);
}
"""
    }

    // ラベルUVと縦横比
    private val n = Labels.strings.size
    private val lu0 = FloatArray(n); private val lv0 = FloatArray(n)
    private val lu1 = FloatArray(n); private val lv1 = FloatArray(n)
    private val lAspect = FloatArray(n)
    // 数字グリフ
    private val du0 = FloatArray(10); private val dv0 = FloatArray(10)
    private val du1 = FloatArray(10); private val dv1 = FloatArray(10)
    private val dAspect = FloatArray(10)
    // 白テクセル
    private var wu = 0f; private var wv = 0f

    private var program = 0
    private var uScreen = 0
    private var texture = 0
    private var vbo = 0
    private var vao = 0
    private lateinit var vtxBuf: FloatBuffer
    private val vtx = FloatArray(MAX_QUADS * 6 * VFLOATS)
    private var quadCount = 0
    private val digitTmp = IntArray(20)

    private var vigProg = 0
    private var vigRed = 0
    private var vigWhite = 0
    private var vigVao = 0
    private var vigVbo = 0

    fun init() {
        buildAtlas()
        program = GfxUtil.buildProgram(VS, FS)
        uScreen = GLES30.glGetUniformLocation(program, "uScreen")
        vtxBuf = ByteBuffer.allocateDirect(vtx.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        var ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vtx.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, VFLOATS * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, VFLOATS * 4, 8)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, VFLOATS * 4, 16)
        GLES30.glBindVertexArray(0)

        // ビネット
        vigProg = GfxUtil.buildProgram(VIG_VS, VIG_FS)
        vigRed = GLES30.glGetUniformLocation(vigProg, "uRed")
        vigWhite = GLES30.glGetUniformLocation(vigProg, "uWhite")
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val qb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        qb.put(quad).position(0)
        ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vigVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vigVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, qb, GLES30.GL_STATIC_DRAW)
        ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vigVao = ids[0]
        GLES30.glBindVertexArray(vigVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vigVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun buildAtlas() {
        val bmp = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textSize = 64f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        // 白テクセル(0,0)-(8,8)
        canvas.drawRect(0f, 0f, 8f, 8f, paint)
        wu = 4f / ATLAS_W
        wv = 4f / ATLAS_H

        var cx = 16f
        var cy = 8f
        val rowH = 84f
        val pad = 10f
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent

        // ラベル
        var i = 0
        while (i < n) {
            val s = Labels.strings[i]
            val w = paint.measureText(s)
            if (cx + w + pad > ATLAS_W) {
                cx = 8f
                cy += rowH
            }
            canvas.drawText(s, cx, cy - fm.ascent, paint)
            lu0[i] = cx / ATLAS_W
            lv0[i] = cy / ATLAS_H
            lu1[i] = (cx + w) / ATLAS_W
            lv1[i] = (cy + textH) / ATLAS_H
            lAspect[i] = w / textH
            cx += w + pad
            i++
        }

        // 数字グリフ（大きめ）
        paint.textSize = 80f
        val fm2 = paint.fontMetrics
        val dh = fm2.descent - fm2.ascent
        cx = 8f
        cy += rowH + 16f
        i = 0
        while (i < 10) {
            val s = ('0' + i).toString()
            val w = paint.measureText(s)
            if (cx + w + pad > ATLAS_W) {
                cx = 8f
                cy += dh + 8f
            }
            canvas.drawText(s, cx, cy - fm2.ascent, paint)
            du0[i] = cx / ATLAS_W
            dv0[i] = cy / ATLAS_H
            du1[i] = (cx + w) / ATLAS_W
            dv1[i] = (cy + dh) / ATLAS_H
            dAspect[i] = w / dh
            cx += w + pad
            i++
        }

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        texture = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        bmp.recycle()
    }

    fun begin() {
        quadCount = 0
    }

    private fun pushQuad(x0: Float, y0: Float, x1: Float, y1: Float,
                         u0: Float, v0: Float, u1: Float, v1: Float,
                         r: Float, g: Float, b: Float, a: Float) {
        if (quadCount >= MAX_QUADS) return
        var o = quadCount * 6 * VFLOATS
        // tri1
        o = putV(o, x0, y0, u0, v0, r, g, b, a)
        o = putV(o, x1, y0, u1, v0, r, g, b, a)
        o = putV(o, x0, y1, u0, v1, r, g, b, a)
        // tri2
        o = putV(o, x1, y0, u1, v0, r, g, b, a)
        o = putV(o, x1, y1, u1, v1, r, g, b, a)
        putV(o, x0, y1, u0, v1, r, g, b, a)
        quadCount++
    }

    private fun putV(o: Int, x: Float, y: Float, u: Float, v: Float,
                     r: Float, g: Float, b: Float, a: Float): Int {
        vtx[o] = x; vtx[o + 1] = y; vtx[o + 2] = u; vtx[o + 3] = v
        vtx[o + 4] = r; vtx[o + 5] = g; vtx[o + 6] = b; vtx[o + 7] = a
        return o + VFLOATS
    }

    fun labelWidth(id: Int, h: Float): Float = lAspect[id] * h

    /** align: 0=左 1=中央 2=右（x基準） */
    fun label(id: Int, x: Float, yTop: Float, h: Float, align: Int,
              r: Float, g: Float, b: Float, a: Float) {
        val w = lAspect[id] * h
        val x0 = when (align) {
            1 -> x - w * 0.5f
            2 -> x - w
            else -> x
        }
        pushQuad(x0, yTop, x0 + w, yTop + h, lu0[id], lv0[id], lu1[id], lv1[id], r, g, b, a)
    }

    /** 数値を右揃えで描画（String生成なし） */
    fun number(value: Long, xRight: Float, yTop: Float, h: Float,
               r: Float, g: Float, b: Float, a: Float) {
        var v = if (value < 0) 0L else value
        var nd = 0
        if (v == 0L) {
            digitTmp[0] = 0
            nd = 1
        } else {
            while (v > 0 && nd < 19) {
                digitTmp[nd] = (v % 10).toInt()
                v /= 10
                nd++
            }
        }
        var x = xRight
        var i = 0
        while (i < nd) {
            val d = digitTmp[i]
            val w = dAspect[d] * h
            x -= w
            pushQuad(x, yTop, x + w, yTop + h, du0[d], dv0[d], du1[d], dv1[d], r, g, b, a)
            i++
        }
    }

    /** 整数を左揃えで描画 */
    fun numberLeft(value: Long, xLeft: Float, yTop: Float, h: Float,
                   r: Float, g: Float, b: Float, a: Float) {
        var v = if (value < 0) 0L else value
        var nd = 0
        if (v == 0L) { digitTmp[0] = 0; nd = 1 } else {
            while (v > 0 && nd < 19) { digitTmp[nd] = (v % 10).toInt(); v /= 10; nd++ }
        }
        // 総幅
        var tw = 0f
        var i = 0
        while (i < nd) { tw += dAspect[digitTmp[i]] * h; i++ }
        number(value, xLeft + tw, yTop, h, r, g, b, a)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float,
             r: Float, g: Float, b: Float, a: Float) {
        pushQuad(x, y, x + w, y + h, wu, wv, wu, wv, r, g, b, a)
    }

    fun end(screenW: Float, screenH: Float) {
        if (quadCount == 0) return
        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uScreen, screenW, screenH)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        vtxBuf.position(0)
        vtxBuf.put(vtx, 0, quadCount * 6 * VFLOATS)
        vtxBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vtx.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, quadCount * 6 * VFLOATS * 4, vtxBuf)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, quadCount * 6)
        GLES30.glBindVertexArray(0)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
    }

    /** ビネット＋被弾赤＋白フラッシュ（1 draw call） */
    fun drawVignette(red: Float, white: Float) {
        GLES30.glUseProgram(vigProg)
        GLES30.glUniform1f(vigRed, red)
        GLES30.glUniform1f(vigWhite, white)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glBindVertexArray(vigVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
    }
}
