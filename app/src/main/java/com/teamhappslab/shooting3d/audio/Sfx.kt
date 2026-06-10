package com.teamhappslab.shooting3d.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.teamhappslab.shooting3d.game.SfxId
import com.teamhappslab.shooting3d.game.SfxPlayer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 効果音。音源ファイルは持たず、起動時にプログラム合成したPCMを
 * WAVとしてキャッシュへ書き出しSoundPoolで再生する。
 */
class Sfx(context: Context) : SfxPlayer {
    private val pool: SoundPool
    private val ids = IntArray(SfxId.COUNT)
    private val volumes = FloatArray(SfxId.COUNT)
    private var loadedCount = 0

    companion object {
        private const val SR = 22050
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loadedCount++
        }
        val dir = File(context.cacheDir, "sfx")
        dir.mkdirs()
        var rngState = 12345L
        fun noise(): Float {
            rngState = rngState xor (rngState shl 13)
            rngState = rngState xor (rngState ushr 7)
            rngState = rngState xor (rngState shl 17)
            return ((rngState ushr 40).toInt() / 16777216f) * 2f - 1f
        }

        // ショット: 短い矩形波ブリップ
        load(dir, SfxId.SHOT, 0.22f) { t, _ ->
            val f = 1500f - t * 6000f
            val sq = if (sin(2.0 * PI * f * t) > 0) 1f else -1f
            sq * exp(-t * 70f)
        }
        // 敵爆発: ノイズバースト＋低音
        load(dir, SfxId.EXPLOSION, 0.5f) { t, _ ->
            (noise() * 0.7f + sin(2.0 * PI * (90.0 - t * 60.0) * t).toFloat() * 0.5f) * exp(-t * 9f)
        }
        // 被弾: 下降トーン＋ノイズ
        load(dir, SfxId.PLAYER_DEATH, 0.8f) { t, _ ->
            (sin(2.0 * PI * (700.0 - t * 800.0) * t).toFloat() * 0.6f + noise() * 0.45f) * exp(-t * 5f)
        }
        // ボム: 巨大ノイズスイープ
        load(dir, SfxId.BOMB, 0.9f) { t, d ->
            val sweep = sin(2.0 * PI * (60.0 + t * 220.0) * t).toFloat()
            (noise() * 0.6f + sweep * 0.55f) * exp(-t * 3.2f) * (if (t < 0.04f) t / 0.04f else 1f)
        }
        // グレイズ: 高音チック
        load(dir, SfxId.GRAZE, 0.16f) { t, _ ->
            sin(2.0 * PI * 4200.0 * t).toFloat() * exp(-t * 90f)
        }
        // アイテム: 上昇ブリップ
        load(dir, SfxId.ITEM, 0.3f) { t, _ ->
            sin(2.0 * PI * (900.0 + t * 2400.0) * t).toFloat() * exp(-t * 25f)
        }
        // スペカ宣言: 和音ヒット
        load(dir, SfxId.SPELL, 0.45f) { t, _ ->
            (sin(2.0 * PI * 440.0 * t) + sin(2.0 * PI * 554.0 * t) + sin(2.0 * PI * 659.0 * t))
                .toFloat() * 0.33f * exp(-t * 6f)
        }
        // 決定音
        load(dir, SfxId.SELECT, 0.5f) { t, _ ->
            (sin(2.0 * PI * 880.0 * t) * exp(-t * 12f) +
                    sin(2.0 * PI * 1320.0 * t) * exp(-(t - 0.08) * 12.0) * (if (t > 0.08f) 1.0 else 0.0))
                .toFloat() * 0.6f
        }
        // WARNING: 低音うねり
        load(dir, SfxId.WARNING, 0.5f) { t, _ ->
            (sin(2.0 * PI * 160.0 * t) * (0.6 + 0.4 * sin(2.0 * PI * 9.0 * t))).toFloat() * exp(-t * 3f)
        }

        volumes[SfxId.SHOT] = 0.16f
        volumes[SfxId.EXPLOSION] = 0.55f
        volumes[SfxId.PLAYER_DEATH] = 0.8f
        volumes[SfxId.BOMB] = 0.9f
        volumes[SfxId.GRAZE] = 0.18f
        volumes[SfxId.ITEM] = 0.25f
        volumes[SfxId.SPELL] = 0.7f
        volumes[SfxId.SELECT] = 0.5f
        volumes[SfxId.WARNING] = 0.7f
    }

    private inline fun load(dir: File, id: Int, durSec: Float, gen: (Float, Float) -> Float) {
        val n = (SR * durSec).toInt()
        val pcm = ShortArray(n)
        var i = 0
        while (i < n) {
            val t = i.toFloat() / SR
            val v = gen(t, durSec)
            val s = (v * 28000f).toInt()
            pcm[i] = (if (s > 32767) 32767 else if (s < -32768) -32768 else s).toShort()
            i++
        }
        val f = File(dir, "se$id.wav")
        writeWav(f, pcm)
        ids[id] = pool.load(f.absolutePath, 1)
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val buf = ByteArray(44 + dataLen)
        fun le32(o: Int, v: Int) {
            buf[o] = (v and 0xFF).toByte()
            buf[o + 1] = ((v shr 8) and 0xFF).toByte()
            buf[o + 2] = ((v shr 16) and 0xFF).toByte()
            buf[o + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun le16(o: Int, v: Int) {
            buf[o] = (v and 0xFF).toByte()
            buf[o + 1] = ((v shr 8) and 0xFF).toByte()
        }
        "RIFF".toByteArray().copyInto(buf, 0)
        le32(4, 36 + dataLen)
        "WAVE".toByteArray().copyInto(buf, 8)
        "fmt ".toByteArray().copyInto(buf, 12)
        le32(16, 16)
        le16(20, 1)         // PCM
        le16(22, 1)         // mono
        le32(24, SR)
        le32(28, SR * 2)
        le16(32, 2)
        le16(34, 16)
        "data".toByteArray().copyInto(buf, 36)
        le32(40, dataLen)
        var i = 0
        var o = 44
        while (i < pcm.size) {
            val s = pcm[i].toInt()
            buf[o] = (s and 0xFF).toByte()
            buf[o + 1] = ((s shr 8) and 0xFF).toByte()
            i++
            o += 2
        }
        FileOutputStream(file).use { it.write(buf) }
    }

    override fun play(id: Int) {
        if (id < 0 || id >= SfxId.COUNT) return
        val v = volumes[id]
        pool.play(ids[id], v, v, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
