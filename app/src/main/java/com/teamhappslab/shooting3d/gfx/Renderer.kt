package com.teamhappslab.shooting3d.gfx

import android.opengl.GLES30
import com.teamhappslab.shooting3d.engine.FastMath
import com.teamhappslab.shooting3d.engine.HudLayout
import com.teamhappslab.shooting3d.game.Boss
import com.teamhappslab.shooting3d.game.GameWorld
import com.teamhappslab.shooting3d.game.Labels

/**
 * シーンレンダラー。描画順（視認性ルール厳守）:
 * 背景 → 敵 → パーティクル → 自機 → 自弾 → 敵弾(最前面) → ビネット → HUD
 */
class Renderer {
    private val camera = Camera()
    private val bg = BackgroundRenderer()
    private val sprites = SpriteRenderer(8192)
    private val hud = HudRenderer()

    private val bulletInst = FloatArray(4096 * GameWorld.STRIDE)
    private val shotInst = FloatArray(256 * GameWorld.STRIDE)
    private val enemyInst = FloatArray(64 * GameWorld.STRIDE)
    private val particleInst = FloatArray(8192 * GameWorld.STRIDE)
    private val playerInst = FloatArray(4 * GameWorld.STRIDE)

    private var screenW = 1080f
    private var screenH = 1920f
    private var aspect = 0.5625f

    fun init(w: Int, h: Int) {
        screenW = w.toFloat()
        screenH = h.toFloat()
        aspect = screenW / screenH
        camera.setViewport(w, h)
        bg.init()
        sprites.init()
        hud.init()
        GLES30.glViewport(0, 0, w, h)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glClearColor(0.0196f, 0.0196f, 0.0627f, 1f)
    }

    fun render(world: GameWorld, alpha: Float, time: Float) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val intro = if (world.introTimer > 0f) FastMath.clamp(world.introTimer / 2.5f, 0f, 1f) else 0f
        camera.update(time, world.shakeTrauma, intro)
        val vp = camera.viewProj
        val right = camera.right
        val up = camera.up

        // 1. 背景
        bg.drawSky(time, aspect)
        bg.drawGrid(vp, time)

        // 2. 敵
        var c = world.fillEnemies(enemyInst, alpha)
        sprites.draw(vp, right, up, enemyInst, c)
        // 3. パーティクル
        c = world.fillParticles(particleInst, alpha)
        sprites.draw(vp, right, up, particleInst, c)
        // 4. 自機
        c = world.fillPlayer(playerInst, alpha)
        sprites.draw(vp, right, up, playerInst, c)
        // 5. 自弾
        c = world.fillShots(shotInst, alpha)
        sprites.draw(vp, right, up, shotInst, c)
        // 6. 敵弾（最前面）
        c = world.fillBullets(bulletInst, alpha)
        sprites.draw(vp, right, up, bulletInst, c)

        // 7. ビネット＋フラッシュ
        hud.drawVignette(world.vignetteRed, world.flashWhite)

        // 8. HUD
        drawHud(world, time)
    }

    // ================= HUD =================

    private fun drawHud(w: GameWorld, time: Float) {
        hud.begin()
        when (w.state) {
            GameWorld.ST_TITLE -> hudTitle(w, time)
            GameWorld.ST_PLAY -> hudPlay(w, time)
            GameWorld.ST_PAUSE -> { hudPlay(w, time); hudPause(w) }
            GameWorld.ST_GAMEOVER -> { hudPlay(w, time); hudGameOver(w) }
            GameWorld.ST_RESULT -> hudResult(w)
        }
        hud.end(screenW, screenH)
    }

    private fun hudTitle(w: GameWorld, time: Float) {
        val pulse = 0.6f + 0.4f * FastMath.sin(time * 2.5f)
        hud.label(Labels.TITLE_LOGO, screenW * 0.5f, screenH * 0.28f, screenW * 0.085f, 1,
            0.45f, 0.95f, 1f, 1f)
        hud.label(Labels.TITLE_LOGO, screenW * 0.5f, screenH * 0.28f, screenW * 0.085f, 1,
            0.25f, 0.94f, 1f, 0.35f * pulse)  // 擬似グロー重ね
        hud.label(Labels.TAP_START, screenW * 0.5f, screenH * 0.62f, screenW * 0.042f, 1,
            1f, 1f, 1f, pulse)
        hud.label(Labels.HISCORE, screenW * 0.03f, screenH * 0.02f, screenW * 0.032f, 0,
            1f, 0.85f, 0.4f, 0.9f)
        hud.number(w.hiScore, screenW * 0.30f, screenH * 0.02f, screenW * 0.032f,
            1f, 1f, 1f, 0.9f)
    }

    private fun hudPlay(w: GameWorld, time: Float) {
        val s = screenW
        val th = s * 0.030f   // 基本文字高

        // --- 上部HUD: スコア・残機・ボム・グレイズ ---
        hud.label(Labels.SCORE, s * 0.025f, screenH * 0.015f, th, 0, 0.6f, 0.9f, 1f, 0.9f)
        hud.number(w.score, s * 0.42f, screenH * 0.015f, th, 1f, 1f, 1f, 0.95f)
        hud.label(Labels.HISCORE, s * 0.025f, screenH * 0.015f + th * 1.2f, th * 0.8f, 0,
            1f, 0.85f, 0.4f, 0.7f)
        hud.number(w.hiScore, s * 0.42f, screenH * 0.015f + th * 1.2f, th * 0.8f,
            1f, 1f, 1f, 0.7f)

        // 残機♥（中央上）
        var i = 0
        val lifeY = screenH * 0.015f
        while (i < w.lives) {
            hud.label(Labels.HEART, s * 0.47f + i * th * 1.3f, lifeY, th, 0,
                1f, 0.3f, 0.45f, 0.95f)
            i++
        }
        // ボム✦
        i = 0
        while (i < w.bombs) {
            hud.label(Labels.STAR, s * 0.47f + i * th * 1.3f, lifeY + th * 1.2f, th, 0,
                0.4f, 0.95f, 1f, 0.95f)
            i++
        }
        // グレイズ
        hud.label(Labels.GRAZE, s * 0.70f, lifeY, th * 0.9f, 0, 1f, 0.8f, 0.95f, 0.85f)
        hud.number(w.graze.toLong(), s * 0.84f, lifeY, th * 0.9f, 1f, 1f, 1f, 0.85f)

        // ポーズボタン（右上）
        hud.label(Labels.PAUSE_ICON, s * (HudLayout.PAUSE_X + 0.05f), screenH * 0.03f, th * 1.2f, 1,
            1f, 1f, 1f, 0.6f)

        // --- ボスHPバー＋スペカ ---
        if (w.bossActive) {
            val barY = screenH * 0.065f
            val barW = s * 0.94f
            hud.rect(s * 0.03f, barY, barW, s * 0.012f, 0.15f, 0.05f, 0.1f, 0.7f)
            hud.rect(s * 0.03f, barY, barW * w.bossHpFrac, s * 0.012f, 1f, 0.19f, 0.38f, 0.95f)
            // 残フェーズ数（バー右下に✦）
            i = 0
            while (i < w.bossPhasesLeft - 1) {
                hud.label(Labels.STAR, s * 0.03f + i * th, barY + s * 0.018f, th * 0.7f, 0,
                    1f, 0.4f, 0.5f, 0.8f)
                i++
            }
            // スペカ名＋残り秒数
            val sid = w.spellNameId
            if (sid >= 0) {
                hud.label(sid, s * 0.03f, barY + s * 0.045f, th * 1.05f, 0, 1f, 0.75f, 0.85f, 0.95f)
                val tLeft = w.spellTimeLeft
                val blink = if (tLeft < 10f && (tLeft * 2f).toInt() % 2 == 0) 1f else 0f
                hud.number(tLeft.toLong(), s * 0.97f, barY + s * 0.045f, th * 1.05f,
                    1f, 1f - blink * 0.7f, 1f - blink * 0.7f, 0.95f)
            } else if (w.spellTimeLeft > 0f) {
                hud.number(w.spellTimeLeft.toLong(), s * 0.97f, barY + s * 0.045f, th,
                    1f, 1f, 1f, 0.7f)
            }
        }

        // WARNING（ボス登場）
        if (w.bossWarning) {
            val blink = 0.5f + 0.5f * FastMath.sin(time * 10f)
            hud.rect(0f, screenH * 0.38f, s, screenH * 0.07f, 0.5f, 0f, 0.05f, 0.4f * blink)
            hud.label(Labels.WARNING, s * 0.5f, screenH * 0.385f, screenH * 0.05f, 1,
                1f, 0.15f, 0.2f, 0.6f + 0.4f * blink)
            hud.label(Labels.BOSS_NAME, s * 0.5f, screenH * 0.46f, screenH * 0.026f, 1,
                1f, 0.6f, 0.65f, 0.9f)
        }

        // スペカ宣言カットイン
        if (w.cutinTimer > 0f && w.cutinLabel >= 0) {
            val t = 1f - w.cutinTimer / Boss.CUTIN_TIME  // 0→1
            val slide = if (t < 0.25f) (1f - t / 0.25f) * s else if (t > 0.8f) -(t - 0.8f) / 0.2f * s else 0f
            hud.rect(0f, screenH * 0.30f, s, screenH * 0.085f, 0.05f, 0f, 0.1f, 0.65f)
            hud.label(w.cutinLabel, s * 0.5f + slide * 0.6f, screenH * 0.315f, screenH * 0.038f, 1,
                1f, 0.85f, 0.9f, 1f)
        }

        // ステージ開始題字
        if (w.stageIntroVisible) {
            val a = FastMath.clamp(w.introTimer / 2.5f, 0f, 1f)
            val fade = if (a > 0.85f) (1f - a) / 0.15f else if (a < 0.3f) a / 0.3f else 1f
            hud.label(Labels.STAGE1, s * 0.5f, screenH * 0.40f, screenH * 0.045f, 1,
                0.6f, 0.95f, 1f, fade)
        }

        // STAGE CLEAR
        if (w.stageClearTimer > 0f) {
            hud.label(Labels.STAGE_CLEAR, s * 0.5f, screenH * 0.42f, screenH * 0.045f, 1,
                0.45f, 1f, 0.8f, 1f)
        }

        // --- 下部ボタン（半透明70%） ---
        drawCircleButton(Labels.BOMB_BTN, HudLayout.BOMB_CX, HudLayout.BOMB_CY, HudLayout.BOMB_R,
            1f, 0.5f, 0.3f)
        drawCircleButton(Labels.SLOW_BTN, HudLayout.SLOW_CX, HudLayout.SLOW_CY, HudLayout.SLOW_R,
            0.3f, 0.8f, 1f)
    }

    private fun drawCircleButton(labelId: Int, cxF: Float, cyF: Float, rF: Float,
                                 r: Float, g: Float, b: Float) {
        val cx = screenW * cxF
        val cy = screenH * cyF
        val rad = screenW * rF
        hud.rect(cx - rad, cy - rad * 0.55f, rad * 2f, rad * 1.1f, r * 0.25f, g * 0.25f, b * 0.25f, 0.30f)
        hud.label(labelId, cx, cy - rad * 0.28f, rad * 0.52f, 1, r, g, b, 0.70f)
    }

    private fun hudPause(w: GameWorld) {
        hud.rect(0f, 0f, screenW, screenH, 0f, 0f, 0.02f, 0.6f)
        hud.label(Labels.PAUSE, screenW * 0.5f, screenH * 0.30f, screenH * 0.040f, 1, 0.6f, 0.95f, 1f, 1f)
        menuButton(Labels.RESUME, 0.45f)
        menuButton(Labels.RETRY, 0.56f)
        menuButton(Labels.TO_TITLE, 0.67f)
    }

    private fun hudGameOver(w: GameWorld) {
        hud.rect(0f, 0f, screenW, screenH, 0.05f, 0f, 0.02f, 0.65f)
        hud.label(Labels.GAME_OVER, screenW * 0.5f, screenH * 0.30f, screenH * 0.045f, 1, 1f, 0.25f, 0.35f, 1f)
        hud.label(Labels.SCORE, screenW * 0.34f, screenH * 0.40f, screenH * 0.022f, 0, 0.7f, 0.9f, 1f, 0.9f)
        hud.number(w.score, screenW * 0.70f, screenH * 0.40f, screenH * 0.022f, 1f, 1f, 1f, 1f)
        menuButton(Labels.RETRY, 0.56f)
        menuButton(Labels.TO_TITLE, 0.67f)
    }

    private fun hudResult(w: GameWorld) {
        hud.rect(0f, 0f, screenW, screenH, 0f, 0.01f, 0.04f, 0.55f)
        hud.label(Labels.STAGE_CLEAR, screenW * 0.5f, screenH * 0.22f, screenH * 0.040f, 1, 0.45f, 1f, 0.8f, 1f)
        hud.label(Labels.RESULT, screenW * 0.5f, screenH * 0.32f, screenH * 0.026f, 1, 0.7f, 0.9f, 1f, 0.9f)
        hud.label(Labels.SCORE, screenW * 0.28f, screenH * 0.42f, screenH * 0.024f, 0, 0.7f, 0.9f, 1f, 0.9f)
        hud.number(w.score, screenW * 0.75f, screenH * 0.42f, screenH * 0.024f, 1f, 1f, 1f, 1f)
        hud.label(Labels.GRAZE, screenW * 0.28f, screenH * 0.48f, screenH * 0.024f, 0, 1f, 0.8f, 0.95f, 0.9f)
        hud.number(w.graze.toLong(), screenW * 0.75f, screenH * 0.48f, screenH * 0.024f, 1f, 1f, 1f, 1f)
        hud.label(Labels.HISCORE, screenW * 0.28f, screenH * 0.54f, screenH * 0.024f, 0, 1f, 0.85f, 0.4f, 0.9f)
        hud.number(w.hiScore, screenW * 0.75f, screenH * 0.54f, screenH * 0.024f, 1f, 1f, 1f, 1f)
        hud.label(Labels.TAP_CONT, screenW * 0.5f, screenH * 0.70f, screenH * 0.022f, 1, 1f, 1f, 1f, 0.85f)
    }

    private fun menuButton(labelId: Int, cyF: Float) {
        val cx = screenW * 0.5f
        val cy = screenH * cyF
        val hw = screenW * 0.28f
        val hh = screenH * 0.038f
        hud.rect(cx - hw, cy - hh, hw * 2f, hh * 2f, 0.1f, 0.25f, 0.4f, 0.5f)
        hud.rect(cx - hw, cy - hh, hw * 2f, 3f, 0.4f, 0.85f, 1f, 0.8f)
        hud.rect(cx - hw, cy + hh - 3f, hw * 2f, 3f, 0.4f, 0.85f, 1f, 0.8f)
        hud.label(labelId, cx, cy - hh * 0.55f, hh * 1.1f, 1, 0.85f, 0.97f, 1f, 0.95f)
    }
}
