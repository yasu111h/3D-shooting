package com.teamhappslab.shooting3d.gfx

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 背景描画（2 draw call）。
 * 1. 星空＋fbm星雲（フルスクリーン手続きシェーダー、輝度上限0.35）
 * 2. Z方向に流れるワイヤーグリッド地形（3層パララックス、ライン描画）
 * 配色: ベース#050510 / 星雲#1A1040,#0A2040 / グリッド線#3080C0
 */
class BackgroundRenderer {
    companion object {
        private const val SKY_VS = """#version 300 es
layout(location=0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos;
    gl_Position = vec4(aPos, 0.9999, 1.0);
}
"""
        private const val SKY_FS = """#version 300 es
precision highp float;
in vec2 vUv;
uniform float uTime;
uniform float uAspect;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.07 + vec2(13.7, 7.1);
        a *= 0.5;
    }
    return v;
}
float starLayer(vec2 uv, float density, float t) {
    vec2 g = floor(uv);
    vec2 f = fract(uv) - 0.5;
    float h = hash(g);
    if (h > density) return 0.0;
    vec2 off = vec2(hash(g + 1.7), hash(g + 9.3)) - 0.5;
    float d = length(f - off * 0.8);
    float tw = 0.6 + 0.4 * sin(t * 3.0 + h * 40.0);
    return smoothstep(0.08, 0.0, d) * tw;
}
void main() {
    vec2 uv = vec2(vUv.x * uAspect, vUv.y);
    // 背景ベース #050510
    vec3 col = vec3(0.0196, 0.0196, 0.0627);
    // fbm星雲（α0.3以下） #1A1040 / #0A2040
    vec2 np = uv * 1.6 + vec2(0.0, uTime * 0.025);
    float n1 = fbm(np);
    float n2 = fbm(np * 1.7 + vec2(5.2, 1.3) + uTime * 0.012);
    vec3 neb1 = vec3(0.102, 0.063, 0.251);
    vec3 neb2 = vec3(0.039, 0.125, 0.251);
    col += neb1 * smoothstep(0.45, 0.85, n1) * 0.30;
    col += neb2 * smoothstep(0.5, 0.9, n2) * 0.28;
    // 星空2層（下方向へ流れる＝前進感）
    col += vec3(0.9, 0.95, 1.0) * starLayer(uv * 22.0 + vec2(0.0, uTime * 1.6), 0.10, uTime) * 0.30;
    col += vec3(0.8, 0.85, 1.0) * starLayer(uv * 11.0 + vec2(3.7, uTime * 0.7), 0.08, uTime) * 0.22;
    // 輝度上限0.35（背景は弾より常に暗く）
    col = min(col, vec3(0.35));
    fragColor = vec4(col, 1.0);
}
"""
        private const val GRID_VS = """#version 300 es
layout(location=0) in vec4 aPos;   // x,y,z, w=flags(層*2 + wrap)
uniform mat4 uVP;
uniform float uScroll;
out float vBright;
out float vDepth;
void main() {
    float flags = aPos.w;
    float layer = floor(flags * 0.5);
    float doWrap = mod(flags, 2.0);
    float speed = 1.0 / (1.0 + layer * 0.9);
    float z = aPos.z;
    if (doWrap > 0.5) {
        z = mod(z + uScroll * speed * 14.0 + 30.0, 44.0) - 30.0;
    }
    vec4 wp = vec4(aPos.x, aPos.y, z, 1.0);
    gl_Position = uVP * wp;
    vBright = 0.5 / (1.0 + layer * 1.1);
    vDepth = smoothstep(-30.0, -16.0, z);
}
"""
        private const val GRID_FS = """#version 300 es
precision mediump float;
in float vBright;
in float vDepth;
out vec4 fragColor;
void main() {
    // 線色 #3080C0 α0.5、遠方フェード、輝度上限0.35
    vec3 col = vec3(0.188, 0.5, 0.752) * vBright * (0.25 + 0.75 * vDepth);
    col = min(col, vec3(0.35));
    fragColor = vec4(col * 0.5, 1.0);
}
"""
    }

    private var skyProg = 0
    private var uTime = 0
    private var uAspect = 0
    private var skyVbo = 0
    private var skyVao = 0

    private var gridProg = 0
    private var gVP = 0
    private var gScroll = 0
    private var gridVbo = 0
    private var gridVao = 0
    private var gridVerts = 0

    fun init() {
        // --- 星空 ---
        skyProg = GfxUtil.buildProgram(SKY_VS, SKY_FS)
        uTime = GLES30.glGetUniformLocation(skyProg, "uTime")
        uAspect = GLES30.glGetUniformLocation(skyProg, "uAspect")
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val qb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        qb.put(quad).position(0)
        var ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        skyVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, qb, GLES30.GL_STATIC_DRAW)
        ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        skyVao = ids[0]
        GLES30.glBindVertexArray(skyVao)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)

        // --- ワイヤーグリッド3層 ---
        gridProg = GfxUtil.buildProgram(GRID_VS, GRID_FS)
        gVP = GLES30.glGetUniformLocation(gridProg, "uVP")
        gScroll = GLES30.glGetUniformLocation(gridProg, "uScroll")
        val verts = buildGrid()
        gridVerts = verts.size / 4
        val gb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        gb.put(verts).position(0)
        ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        gridVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, gb, GLES30.GL_STATIC_DRAW)
        ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        gridVao = ids[0]
        GLES30.glBindVertexArray(gridVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun buildGrid(): FloatArray {
        // 3層: y = -2 / -7 / -13。縦線（z方向・wrapなし）＋横線（wrapあり）
        val list = ArrayList<Float>()
        val ys = floatArrayOf(-2f, -7f, -13f)
        var layer = 0
        while (layer < 3) {
            val y = ys[layer]
            val w = 16f + layer * 8f
            // 縦線
            var x = -w
            while (x <= w + 0.01f) {
                addLine(list, x, y, -30f, layer * 2f + 0f, x, y, 14f, layer * 2f + 0f)
                x += 2f
            }
            // 横線（スクロール・wrap）
            var z = -30f
            while (z <= 14f + 0.01f) {
                addLine(list, -w, y, z, layer * 2f + 1f, w, y, z, layer * 2f + 1f)
                z += 2f
            }
            layer++
        }
        val out = FloatArray(list.size)
        var i = 0
        while (i < list.size) { out[i] = list[i]; i++ }
        return out
    }

    private fun addLine(list: ArrayList<Float>,
                        x0: Float, y0: Float, z0: Float, f0: Float,
                        x1: Float, y1: Float, z1: Float, f1: Float) {
        list.add(x0); list.add(y0); list.add(z0); list.add(f0)
        list.add(x1); list.add(y1); list.add(z1); list.add(f1)
    }

    fun drawSky(time: Float, aspect: Float) {
        GLES30.glUseProgram(skyProg)
        GLES30.glUniform1f(uTime, time)
        GLES30.glUniform1f(uAspect, aspect)
        GLES30.glBindVertexArray(skyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    fun drawGrid(vp: FloatArray, scroll: Float) {
        GLES30.glUseProgram(gridProg)
        GLES30.glUniformMatrix4fv(gVP, 1, false, vp, 0)
        GLES30.glUniform1f(gScroll, scroll)
        GLES30.glBindVertexArray(gridVao)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVerts)
        GLES30.glBindVertexArray(0)
    }
}
