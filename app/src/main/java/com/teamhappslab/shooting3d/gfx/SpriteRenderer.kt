package com.teamhappslab.shooting3d.gfx

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GPUインスタンシング・ビルボードSDFスプライトレンダラー。
 * 弾・敵・自機・パーティクル全てを手続きシェーダーで描画（テクスチャ不使用）。
 * 1回のdraw()が1 draw call。orphaning + glBufferSubData。
 *
 * インスタンスレイアウト(12 float): [x,y,z,scale, rot,sprite,alpha,pad, r,g,b,pad]
 */
class SpriteRenderer(private val maxInstances: Int) {
    companion object {
        const val STRIDE_FLOATS = 12
        private const val VS = """#version 300 es
layout(location=0) in vec2 aCorner;
layout(location=1) in vec4 aPosScale;
layout(location=2) in vec4 aMisc;     // rot, sprite, alpha, pad
layout(location=3) in vec4 aColor;
uniform mat4 uVP;
uniform vec3 uRight;
uniform vec3 uUp;
out vec2 vUv;
flat out int vSprite;
out vec3 vColor;
out float vAlpha;
void main() {
    float c = cos(aMisc.x);
    float s = sin(aMisc.x);
    vec2 rc = vec2(aCorner.x * c - aCorner.y * s, aCorner.x * s + aCorner.y * c);
    vec3 wp = aPosScale.xyz + (uRight * rc.x + uUp * rc.y) * aPosScale.w;
    gl_Position = uVP * vec4(wp, 1.0);
    vUv = aCorner * 2.0;
    vSprite = int(aMisc.y + 0.5);
    vColor = aColor.rgb;
    vAlpha = aMisc.z;
}
"""
        private const val FS = """#version 300 es
precision mediump float;
in vec2 vUv;
flat in int vSprite;
in vec3 vColor;
in float vAlpha;
out vec4 fragColor;

float ring(float d, float r, float w) {
    return smoothstep(w, 0.0, abs(d - r));
}

void main() {
    vec2 uv = vUv;
    vec3 col = vec3(0.0);
    if (vSprite == 0) {
        // 小玉: 白コア＋色ハロー＋縁取りリム
        float d = length(uv);
        float core = smoothstep(0.5, 0.26, d);
        float halo = exp(-d * 3.0) * 0.85;
        float rim = ring(d, 0.78, 0.12);
        col = vec3(1.0) * core + vColor * halo + vColor * rim * 1.5;
    } else if (vSprite == 1) {
        // 米弾: 楕円＋進行方向回転（回転は頂点側）
        vec2 q = vec2(uv.x * 2.6, uv.y * 1.1);
        float d = length(q);
        float core = smoothstep(0.55, 0.25, d);
        float halo = exp(-d * 2.6) * 0.9;
        float rim = ring(d, 0.85, 0.14);
        col = vec3(1.0) * core + vColor * halo + vColor * rim * 1.4;
    } else if (vSprite == 2) {
        // 大玉: 同心円模様＋回転
        float d = length(uv);
        float ang = atan(uv.y, uv.x);
        float pat = 0.55 + 0.45 * sin(ang * 6.0 + d * 5.0);
        float core = smoothstep(0.42, 0.18, d);
        float halo = exp(-d * 2.2) * 0.85;
        float inner = ring(d, 0.55, 0.08) * pat;
        float rim = ring(d, 0.85, 0.10);
        col = vec3(1.0) * core + vColor * (halo + inner * 0.9) + vColor * rim * 1.6;
    } else if (vSprite == 3) {
        // クナイ: 涙滴SDF
        vec2 q = uv;
        float d = length(vec2(q.x * 2.4, (q.y - 0.25) * 1.05 + abs(q.x) * 0.9));
        float core = smoothstep(0.55, 0.28, d);
        float halo = exp(-d * 2.8) * 0.85;
        float rim = ring(d, 0.8, 0.13);
        col = vec3(1.0) * core + vColor * halo + vColor * rim * 1.4;
    } else if (vSprite == 4) {
        // ザコ敵: 菱形＋発光縁
        float d = abs(uv.x) + abs(uv.y);
        float core = smoothstep(0.55, 0.30, d);
        float halo = exp(-d * 2.4) * 0.6;
        float rim = ring(d, 0.72, 0.12);
        col = vColor * core * 0.9 + vec3(1.0) * core * 0.25 + vColor * (halo + rim * 1.3);
    } else if (vSprite == 5) {
        // ボス: 多角コア＋トゲ回転
        float d = length(uv);
        float ang = atan(uv.y, uv.x);
        float spikes = 1.0 + 0.18 * sin(ang * 5.0);
        float dd = d * spikes;
        float core = smoothstep(0.55, 0.25, dd);
        float halo = exp(-d * 2.0) * 0.7;
        float rim = ring(dd, 0.7, 0.1);
        float eye = smoothstep(0.18, 0.05, d);
        col = vColor * core + vec3(1.0) * eye + vColor * (halo + rim * 1.5);
    } else if (vSprite == 6) {
        // 自機: 上向き三角＋エンジン光（コア白・シアン#40F0FF）
        float body = max(abs(uv.x) * 1.5 + uv.y * 0.75 - 0.55, -uv.y - 0.85);
        float core = smoothstep(0.10, -0.04, body);
        float d = length(uv);
        float halo = exp(-d * 2.6) * 0.7;
        vec2 eq = vec2(uv.x, (uv.y - 0.65) * 0.7);
        float engine = exp(-length(eq) * 5.0) * 0.9;
        col = vec3(1.0) * core * 0.7 + vColor * core * 0.6 + vColor * (halo + engine);
    } else if (vSprite == 7) {
        // 判定点: 白コア＋赤リング
        float d = length(uv);
        float core = smoothstep(0.32, 0.16, d);
        float rim = ring(d, 0.7, 0.1);
        col = vec3(1.0) * core + vColor * rim * 2.0;
    } else {
        // 8: 衝撃波リング
        float d = length(uv);
        float rim = ring(d, 0.82, 0.16);
        col = (vColor * 1.4 + vec3(0.35)) * rim;
    }
    // 加算合成 (GL_SRC_ALPHA, GL_ONE)
    fragColor = vec4(col * vAlpha, 1.0);
}
"""
    }

    private var program = 0
    private var uVP = 0
    private var uRight = 0
    private var uUp = 0
    private var quadVbo = 0
    private var instVbo = 0
    private var vao = 0
    private val instBytes = maxInstances * STRIDE_FLOATS * 4
    private lateinit var instBuf: FloatBuffer

    fun init() {
        program = GfxUtil.buildProgram(VS, FS)
        uVP = GLES30.glGetUniformLocation(program, "uVP")
        uRight = GLES30.glGetUniformLocation(program, "uRight")
        uUp = GLES30.glGetUniformLocation(program, "uUp")

        instBuf = ByteBuffer.allocateDirect(instBytes)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        quadVbo = ids[0]
        instVbo = ids[1]

        // クアッド頂点（TRIANGLE_STRIP）
        val quad = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        val qb = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        qb.put(quad).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, qb, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instBytes, null, GLES30.GL_DYNAMIC_DRAW)

        val vaoIds = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoIds, 0)
        vao = vaoIds[0]
        GLES30.glBindVertexArray(vao)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        val stride = STRIDE_FLOATS * 4
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 16)
        GLES30.glVertexAttribDivisor(2, 1)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 32)
        GLES30.glVertexAttribDivisor(3, 1)

        GLES30.glBindVertexArray(0)
    }

    /** 1 draw call。dataはfill済みインスタンス配列 */
    fun draw(vp: FloatArray, right: FloatArray, up: FloatArray, data: FloatArray, count: Int) {
        if (count <= 0) return
        val n = if (count > maxInstances) maxInstances else count
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        GLES30.glUniform3f(uRight, right[0], right[1], right[2])
        GLES30.glUniform3f(uUp, up[0], up[1], up[2])

        instBuf.position(0)
        instBuf.put(data, 0, n * STRIDE_FLOATS)
        instBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        // orphaning
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instBytes, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * STRIDE_FLOATS * 4, instBuf)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, n)
        GLES30.glBindVertexArray(0)
    }
}
