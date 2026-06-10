package com.teamhappslab.shooting3d.gfx

import android.opengl.GLES30
import android.util.Log

object GfxUtil {
    fun compileShader(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val status = IntArray(1)
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh)
            Log.e("GfxUtil", "Shader compile error: $log\n$src")
            GLES30.glDeleteShader(sh)
            throw RuntimeException("Shader compile failed: $log")
        }
        return sh
    }

    fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES30.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            Log.e("GfxUtil", "Program link error: $log")
            throw RuntimeException("Program link failed: $log")
        }
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return p
    }
}
