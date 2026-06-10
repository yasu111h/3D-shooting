package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath
import com.teamhappslab.shooting3d.engine.XorShift

/** 効果音ID */
object SfxId {
    const val SHOT = 0
    const val EXPLOSION = 1
    const val PLAYER_DEATH = 2
    const val BOMB = 3
    const val GRAZE = 4
    const val ITEM = 5
    const val SPELL = 6
    const val SELECT = 7
    const val WARNING = 8
    const val COUNT = 9
}

interface SfxPlayer {
    fun play(id: Int)
}

interface ScorePersist {
    fun loadHiScore(): Long
    fun saveHiScore(v: Long)
}

/**
 * ゲームワールド（純ロジック・GL非依存）。
 * 120Hz固定タイムステップで更新。ゲームループ内アロケーションゼロ。
 */
class GameWorld {
    companion object {
        const val DT = 1f / 120f
        const val ST_TITLE = 0
        const val ST_PLAY = 1
        const val ST_PAUSE = 2
        const val ST_GAMEOVER = 3
        const val ST_RESULT = 4
        const val STRIDE = 12   // インスタンスfloat数
    }

    var state = ST_TITLE
        private set
    var stateTime = 0f
        private set

    val bullets = BulletPool(4096)
    val shots = PlayerShots(256)
    val particles = ParticlePool(8192)
    val player = Player()
    val boss = Boss()
    private val enemies = Array(48) { Enemy() }
    private val grid = CollisionGrid(4096, -World.BOUND_X, World.BOUND_X,
        World.BOUND_Z_FAR, World.BOUND_Z_NEAR, 1.6f)
    private val queryBuf = IntArray(512)
    val rng = XorShift(20260610L)

    var sfx: SfxPlayer? = null
    var persist: ScorePersist? = null

    // スコア・ゲーム状態
    var score = 0L; private set
    var hiScore = 0L
    var lives = 3; private set
    var bombs = 3; private set
    var graze = 0; private set
    var multiplier = 1f; private set

    // ステージ進行
    private var stageTime = 0f
    private var eventIdx = 0
    private var bossStarted = false
    private var phaseMissed = false
    var stageClearTimer = 0f; private set

    // 演出
    var shakeTrauma = 0f; private set
    var flashWhite = 0f; private set
    var vignetteRed = 0f; private set
    var cutinTimer = 0f; private set
    var cutinLabel = -1; private set
    var introTimer = 0f; private set
    private var deathTimer = 0f
    private var bombActiveT = 0f
    private var dyingFxT = 0f

    // 実時間スロー制御（threadがadvanceRealTimeで進める）
    private var hitStop = 0f
    private var slowTimer = 0f
    private var slowScale = 1f

    // SE間引き
    private var grazeSfxCd = 0f
    private var itemSfxCd = 0f
    private var explSfxCd = 0f

    // 画面サイズ（タッチ→world変換用）
    private var viewW = 1080f
    private var viewH = 1920f

    // タイトルデモ
    private val titleEmitter = Emitter()

    init {
        boss.phases = Stage1.buildBossPhases()
        enterTitle()
    }

    fun setViewport(w: Float, h: Float) {
        viewW = w
        viewH = h
    }

    // ================= 状態遷移 =================

    fun enterTitle() {
        state = ST_TITLE
        stateTime = 0f
        bullets.clear(); shots.clear(); particles.clear()
        boss.reset()
        var i = 0
        while (i < enemies.size) { enemies[i].active = false; i++ }
        titleEmitter.set(Stage1.titleDemo)
        hiScore = persist?.loadHiScore() ?: hiScore
        slowTimer = 0f; hitStop = 0f
        shakeTrauma = 0f; flashWhite = 0f; vignetteRed = 0f
        cutinTimer = 0f
    }

    fun startGame() {
        state = ST_PLAY
        stateTime = 0f
        score = 0L
        lives = 3
        bombs = 3
        graze = 0
        multiplier = 1f
        stageTime = 0f
        eventIdx = 0
        bossStarted = false
        phaseMissed = false
        stageClearTimer = 0f
        deathTimer = 0f
        bombActiveT = 0f
        cutinTimer = 0f
        introTimer = 2.5f
        bullets.clear(); shots.clear(); particles.clear()
        boss.reset()
        var i = 0
        while (i < enemies.size) { enemies[i].active = false; i++ }
        player.reset()
        rng.reseed(20260610L)
        sfx?.play(SfxId.SELECT)
    }

    private fun gameOver() {
        state = ST_GAMEOVER
        stateTime = 0f
        if (score > hiScore) {
            hiScore = score
            persist?.saveHiScore(hiScore)
        }
    }

    private fun stageClear() {
        state = ST_RESULT
        stateTime = 0f
        if (score > hiScore) {
            hiScore = score
            persist?.saveHiScore(hiScore)
        }
    }

    // ================= 入力（GameThreadから呼ぶ） =================

    /** 移動デルタ(px)。world座標に変換して適用 */
    fun onMove(dxPx: Float, dyPx: Float) {
        if (state != ST_PLAY) return
        val k = (22f / viewW) * 1.55f  // 感度
        player.move(dxPx * k, dyPx * k)
    }

    fun setSlow(held: Boolean) {
        player.slowMode = held
    }

    fun onBomb() {
        if (state != ST_PLAY || !player.alive || bombs <= 0 || bombActiveT > 0f) return
        bombs--
        bombActiveT = 1.0f
        phaseMissed = true
        player.invulnTimer = maxOf(player.invulnTimer, 1.5f)
        multiplier = maxOf(1f, multiplier * 0.6f)
        // 全弾消去＋衝撃波
        bullets.clear()
        spawnShockwave(player.x, player.z, 0.25f, 0.94f, 1f, 16f)
        spawnShockwave(player.x, player.z, 1f, 1f, 1f, 22f)
        // 全敵にダメージ
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            if (e.active) {
                e.hp -= 40f
                e.flashT = 0.1f
                if (e.hp <= 0f) killEnemy(e)
            }
            i++
        }
        boss.damage(80f)
        shakeTrauma = minOf(1f, shakeTrauma + 0.7f)
        flashWhite = 0.5f
        sfx?.play(SfxId.BOMB)
    }

    fun onBack() {
        if (state == ST_PLAY) {
            state = ST_PAUSE
            stateTime = 0f
        } else if (state == ST_PAUSE) {
            state = ST_PLAY
        }
    }

    /** タップ（画面px） */
    fun onTap(x: Float, y: Float) {
        val fx = x / viewW
        val fy = y / viewH
        when (state) {
            ST_TITLE -> {
                if (stateTime > 0.5f) startGame()
            }
            ST_PLAY -> {
                // ポーズボタン（右上）
                if (fx > 0.84f && fy < 0.13f) {
                    state = ST_PAUSE
                    stateTime = 0f
                    sfx?.play(SfxId.SELECT)
                }
            }
            ST_PAUSE -> {
                if (inBtn(fx, fy, 0.5f, 0.45f)) { state = ST_PLAY; sfx?.play(SfxId.SELECT) }
                else if (inBtn(fx, fy, 0.5f, 0.56f)) { startGame() }
                else if (inBtn(fx, fy, 0.5f, 0.67f)) { enterTitle(); sfx?.play(SfxId.SELECT) }
            }
            ST_GAMEOVER -> {
                if (stateTime < 0.8f) return
                if (inBtn(fx, fy, 0.5f, 0.56f)) { startGame() }
                else if (inBtn(fx, fy, 0.5f, 0.67f)) { enterTitle(); sfx?.play(SfxId.SELECT) }
            }
            ST_RESULT -> {
                if (stateTime > 1.2f) { enterTitle(); sfx?.play(SfxId.SELECT) }
            }
        }
    }

    private fun inBtn(fx: Float, fy: Float, cx: Float, cy: Float): Boolean =
        fx > cx - 0.28f && fx < cx + 0.28f && fy > cy - 0.038f && fy < cy + 0.038f

    // ================= 実時間スロー制御 =================

    /**
     * threadが毎フレーム呼ぶ。ヒットストップ/スロー演出を実時間で進め、
     * 現在のtimeScaleを返す（UI非影響）。
     */
    fun advanceRealTime(realDt: Float): Float {
        grazeSfxCd -= realDt
        itemSfxCd -= realDt
        explSfxCd -= realDt
        if (flashWhite > 0f) flashWhite = maxOf(0f, flashWhite - realDt * 5f)
        if (vignetteRed > 0f) vignetteRed = maxOf(0f, vignetteRed - realDt * 1.5f)
        shakeTrauma = maxOf(0f, shakeTrauma - realDt * 1.8f * (0.5f + shakeTrauma))
        if (cutinTimer > 0f) cutinTimer -= realDt
        if (introTimer > 0f) introTimer -= realDt

        if (state != ST_PLAY) return 1f
        if (hitStop > 0f) {
            hitStop -= realDt
            return 0f
        }
        if (slowTimer > 0f) {
            slowTimer -= realDt
            return slowScale
        }
        return 1f
    }

    // ================= メインステップ（120Hz） =================

    fun step(dt: Float) {
        stateTime += dt
        when (state) {
            ST_TITLE -> stepTitle(dt)
            ST_PLAY -> stepPlay(dt)
            ST_GAMEOVER, ST_RESULT -> {
                bullets.update(dt, player.x, player.z)
                particles.update(dt)
            }
            // ST_PAUSE: 何もしない
        }
    }

    private fun stepTitle(dt: Float) {
        titleEmitter.update(dt, 0f, -9f, 0f, 6f, bullets, rng, 1f)
        bullets.update(dt, 0f, 6f)
        particles.update(dt)
    }

    private fun stepPlay(dt: Float) {
        stageTime += dt

        // 道中スポーン
        val ev = Stage1.events
        while (eventIdx < ev.size && ev[eventIdx].time <= stageTime) {
            spawnEnemy(ev[eventIdx])
            eventIdx++
        }

        // ボス開始
        if (!bossStarted && stageTime >= Stage1.BOSS_TIME) {
            bossStarted = true
            boss.startEntry()
            sfx?.play(SfxId.WARNING)
        }

        // 自機
        if (player.alive) {
            if (player.update(dt, shots)) sfx?.play(SfxId.SHOT)
        } else {
            deathTimer -= dt
            if (deathTimer <= 0f) {
                if (lives < 0) {
                    gameOver()
                    return
                }
                player.respawn()
                bullets.clear()
            }
        }
        shots.update(dt)

        // 敵
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            if (e.active) {
                if (!e.update(dt, player.x, player.z, bullets, rng, 1f)) {
                    e.active = false
                }
            }
            i++
        }

        // ボス
        if (boss.alive) {
            boss.update(dt, player.x, player.z, bullets, rng, 1f)
            handleBossEvent()
            if (boss.state == Boss.ST_DYING) {
                dyingFxT -= dt
                if (dyingFxT <= 0f) {
                    dyingFxT = 0.13f
                    val ex = boss.x + rng.range(-2f, 2f)
                    val ez = boss.z + rng.range(-1.5f, 1.5f)
                    explosion(ex, ez, 1f, 0.3f, 0.45f, 1.4f)
                    if (explSfxCd <= 0f) { sfx?.play(SfxId.EXPLOSION); explSfxCd = 0.1f }
                }
            }
        } else if (bossStarted && boss.state == Boss.ST_DEAD && stageClearTimer == 0f) {
            stageClearTimer = 3.0f
        }
        if (stageClearTimer > 0f) {
            stageClearTimer -= dt
            if (stageClearTimer <= 0f) {
                stageClear()
                return
            }
        }

        // 弾
        bullets.update(dt, player.x, player.z)

        // 当たり判定
        grid.build(bullets.posX, bullets.posZ, bullets.count)
        collidePlayerVsBullets()
        collideShotsVsEnemies()

        // パーティクル・タイマー
        particles.update(dt)
        if (bombActiveT > 0f) bombActiveT -= dt
    }

    private fun spawnEnemy(ev: SpawnEvent) {
        var i = 0
        while (i < enemies.size) {
            if (!enemies[i].active) {
                enemies[i].spawn(ev.type, ev.x, ev.z, ev.pathId,
                    ev.p0, ev.p1, ev.p2, ev.p3, ev.defA, ev.defB)
                return
            }
            i++
        }
    }

    private fun handleBossEvent() {
        when (boss.consumeEvent()) {
            Boss.EV_PHASE_BROKEN -> {
                bullets.convertAllToItems()
                explosion(boss.x, boss.z, 1f, 0.3f, 0.45f, 2.2f)
                shakeTrauma = minOf(1f, shakeTrauma + 0.6f)
                sfx?.play(SfxId.EXPLOSION)
                if (phaseMissedCheckIsSpellPrev()) {
                    // スペカボーナス（ノーミス・ノーボム時）
                    if (!phaseMissed) {
                        score += (50000 * multiplier).toLong()
                        sfx?.play(SfxId.ITEM)
                    }
                }
                phaseMissed = false
                val p = boss.currentPhase()
                if (p != null && p.isSpell) declareSpell(p.nameId)
            }
            Boss.EV_SPELL_DECLARE -> {
                val p = boss.currentPhase()
                if (p != null) declareSpell(p.nameId)
            }
            Boss.EV_DEFEATED -> {
                bullets.convertAllToItems()
                score += (100000 * multiplier).toLong()
                if (!phaseMissed) score += (50000 * multiplier).toLong()
                slowTimer = 1.0f
                slowScale = 0.3f
                shakeTrauma = 1f
                flashWhite = 0.7f
                dyingFxT = 0f
                sfx?.play(SfxId.EXPLOSION)
            }
        }
    }

    private fun phaseMissedCheckIsSpellPrev(): Boolean {
        val idx = boss.phaseIndex - 1
        return idx >= 0 && idx < boss.phases.size && boss.phases[idx].isSpell
    }

    private fun declareSpell(nameId: Int) {
        cutinLabel = nameId
        cutinTimer = Boss.CUTIN_TIME
        slowTimer = 0.55f
        slowScale = 0.2f
        sfx?.play(SfxId.SPELL)
    }

    // ================= 当たり判定 =================

    private fun collidePlayerVsBullets() {
        if (!player.alive) return
        val px = player.x
        val pz = player.z
        val n = grid.queryNeighbors(px, pz, queryBuf)
        var k = 0
        while (k < n) {
            val i = queryBuf[k]
            if (i >= bullets.count) { k++; continue }
            val dx = bullets.posX[i] - px
            val dz = bullets.posZ[i] - pz
            val d2 = dx * dx + dz * dz
            if ((bullets.flags[i] and BulletPool.FLAG_ITEM) != 0) {
                // アイテム取得
                val pr = 1.1f
                if (d2 < pr * pr) {
                    score += (500 * multiplier).toLong()
                    if (itemSfxCd <= 0f) { sfx?.play(SfxId.ITEM); itemSfxCd = 0.05f }
                    spawnSpark(bullets.posX[i], bullets.posZ[i], 1f, 0.85f, 0.3f)
                    bullets.kill(i)
                    // swap-removeでiに別の弾が来るが、queryBufは古いので飛ばす
                }
                k++
                continue
            }
            val hr = player.hitRadius + bullets.radius[i]
            if (d2 < hr * hr && player.invulnTimer <= 0f) {
                playerHit()
                return
            }
            // グレイズ
            val gr = player.grazeRadius + bullets.radius[i]
            if (d2 < gr * gr && (bullets.flags[i] and BulletPool.FLAG_GRAZED) == 0) {
                bullets.flags[i] = bullets.flags[i] or BulletPool.FLAG_GRAZED
                graze++
                multiplier = minOf(8f, 1f + graze * 0.015f)
                score += 10
                spawnSpark(px + dx * 0.5f, pz + dz * 0.5f, 0.25f, 0.94f, 1f)
                if (grazeSfxCd <= 0f) { sfx?.play(SfxId.GRAZE); grazeSfxCd = 0.07f }
            }
            k++
        }
    }

    private fun playerHit() {
        player.alive = false
        deathTimer = 1.2f
        lives--
        phaseMissed = true
        multiplier = maxOf(1f, multiplier * 0.4f)
        hitStop = 0.1f          // ヒットストップ
        slowTimer = 0.5f        // 被弾スロー
        slowScale = 0.2f
        flashWhite = 0.8f       // 白フラッシュ（上限α0.8）
        vignetteRed = 0.6f
        shakeTrauma = 1f
        explosion(player.x, player.z, 0.25f, 0.94f, 1f, 1.8f)  // 自機色シアン爆発
        sfx?.play(SfxId.PLAYER_DEATH)
    }

    private fun collideShotsVsEnemies() {
        // ザコ・中ボス
        var ei = 0
        while (ei < enemies.size) {
            val e = enemies[ei]
            if (e.active) {
                val rr = e.radius + 0.3f
                var si = 0
                while (si < shots.count) {
                    val dx = shots.posX[si] - e.x
                    val dz = shots.posZ[si] - e.z
                    if (dx * dx + dz * dz < rr * rr) {
                        e.hp -= 1f
                        e.flashT = 0.05f
                        spawnSpark(shots.posX[si], shots.posZ[si], 0.5f, 1f, 1f)
                        shots.kill(si)
                        if (e.hp <= 0f) {
                            killEnemy(e)
                            break
                        }
                    } else {
                        si++
                    }
                }
            }
            ei++
        }
        // ボス
        if (boss.alive && !boss.invincible) {
            val rr = boss.radius + 0.3f
            var si = 0
            while (si < shots.count) {
                val dx = shots.posX[si] - boss.x
                val dz = shots.posZ[si] - boss.z
                if (dx * dx + dz * dz < rr * rr) {
                    boss.damage(1f)
                    spawnSpark(shots.posX[si], shots.posZ[si], 0.5f, 1f, 1f)
                    shots.kill(si)
                } else {
                    si++
                }
            }
        }
    }

    private fun killEnemy(e: Enemy) {
        e.active = false
        score += (e.score * multiplier).toLong()
        explosion(e.x, e.z, e.colR, e.colG, e.colB, e.scale * 0.6f)
        shakeTrauma = minOf(1f, shakeTrauma + if (e.type == 2) 0.8f else 0.18f)
        if (explSfxCd <= 0f) { sfx?.play(SfxId.EXPLOSION); explSfxCd = 0.05f }
        if (e.type == 2) {
            // 中ボス撃破: 弾アイテム化
            bullets.convertAllToItems()
            explosion(e.x, e.z, 1f, 0.5f, 0.2f, 2.5f)
        }
    }

    // ================= エフェクト生成 =================

    /** 撃破3層爆発: ①白フラッシュ ②衝撃波リング ③破片 */
    private fun explosion(x: Float, z: Float, r: Float, g: Float, b: Float, power: Float) {
        // ①白フラッシュ0.1秒
        particles.spawn(x, 0f, z, 0f, 0f, 0f, 0.1f,
            2.2f * power, 3.2f * power, 1f, 1f, 1f, ParticlePool.SPR_GLOW, 0f)
        // ②衝撃波リング0.4秒
        particles.spawn(x, 0f, z, 0f, 0f, 0f, 0.4f,
            0.6f * power, 5.5f * power, r, g, b, ParticlePool.SPR_RING, 0f)
        // ③破片パーティクル20〜40個0.6秒
        val n = 20 + rng.nextInt(21)
        var i = 0
        while (i < n) {
            val a = rng.range(0f, FastMath.TWO_PI)
            val spd = rng.range(3f, 11f) * power
            particles.spawn(x, 0f, z,
                FastMath.sin(a) * spd, rng.range(0.5f, 3f), FastMath.cos(a) * spd,
                rng.range(0.3f, 0.65f), rng.range(0.25f, 0.5f) * power, 0.05f,
                r, g, b, ParticlePool.SPR_GLOW, 2.2f)
            i++
        }
    }

    private fun spawnShockwave(x: Float, z: Float, r: Float, g: Float, b: Float, size: Float) {
        particles.spawn(x, 0f, z, 0f, 0f, 0f, 0.9f, 1f, size, r, g, b, ParticlePool.SPR_RING, 0f)
    }

    private fun spawnSpark(x: Float, z: Float, r: Float, g: Float, b: Float) {
        val a = rng.range(0f, FastMath.TWO_PI)
        val spd = rng.range(2f, 6f)
        particles.spawn(x, 0f, z, FastMath.sin(a) * spd, 1.5f, FastMath.cos(a) * spd,
            0.25f, 0.3f, 0.04f, r, g, b, ParticlePool.SPR_GLOW, 1.5f)
    }

    // ================= 描画用インスタンスデータ生成 =================
    // レイアウト: [x,y,z,scale, rot,sprite,alpha,pad, r,g,b,pad] STRIDE=12

    fun fillBullets(out: FloatArray, alpha: Float): Int {
        val n = bullets.count
        var i = 0
        var o = 0
        while (i < n) {
            out[o] = FastMath.lerp(bullets.prevX[i], bullets.posX[i], alpha)
            out[o + 1] = FastMath.lerp(bullets.prevY[i], bullets.posY[i], alpha) + 0.3f
            out[o + 2] = FastMath.lerp(bullets.prevZ[i], bullets.posZ[i], alpha)
            val age = bullets.age[i]
            var s = bullets.scale[i]
            if (age < 0.12f) s *= 1f + (0.12f - age) * 7f   // スポーン時ポップ
            out[o + 3] = s
            out[o + 4] = bullets.rotation[i]
            out[o + 5] = bullets.spriteId[i]
            out[o + 6] = if (age < 0.15f) age * 6.6f else 1f
            out[o + 7] = 0f
            out[o + 8] = bullets.colR[i]
            out[o + 9] = bullets.colG[i]
            out[o + 10] = bullets.colB[i]
            out[o + 11] = 0f
            i++
            o += STRIDE
        }
        return n
    }

    fun fillShots(out: FloatArray, alpha: Float): Int {
        val n = shots.count
        var i = 0
        var o = 0
        while (i < n) {
            out[o] = FastMath.lerp(shots.prevX[i], shots.posX[i], alpha)
            out[o + 1] = 0.2f
            out[o + 2] = FastMath.lerp(shots.prevZ[i], shots.posZ[i], alpha)
            out[o + 3] = 1.15f
            out[o + 4] = FastMath.atan2(shots.velX[i], shots.velZ[i])
            out[o + 5] = BulletPool.SPR_RICE
            out[o + 6] = 0.6f  // 自機ショットは敵弾より暗く α0.6
            out[o + 7] = 0f
            // #80FFFF
            out[o + 8] = 0.5f
            out[o + 9] = 1f
            out[o + 10] = 1f
            out[o + 11] = 0f
            i++
            o += STRIDE
        }
        return n
    }

    fun fillEnemies(out: FloatArray, alpha: Float): Int {
        var n = 0
        var o = 0
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            if (e.active) {
                out[o] = FastMath.lerp(e.prevX, e.x, alpha)
                out[o + 1] = 0.1f
                out[o + 2] = FastMath.lerp(e.prevZ, e.z, alpha)
                out[o + 3] = e.scale
                out[o + 4] = e.t * 1.8f
                out[o + 5] = e.sprite
                out[o + 6] = 0.7f   // 敵機輝度0.7
                out[o + 7] = 0f
                val f = if (e.flashT > 0f) 1f else 0f
                out[o + 8] = FastMath.lerp(e.colR, 1f, f)
                out[o + 9] = FastMath.lerp(e.colG, 1f, f)
                out[o + 10] = FastMath.lerp(e.colB, 1f, f)
                out[o + 11] = 0f
                n++
                o += STRIDE
            }
            i++
        }
        if (boss.alive) {
            out[o] = FastMath.lerp(boss.prevX, boss.x, alpha)
            out[o + 1] = 0.1f
            out[o + 2] = FastMath.lerp(boss.prevZ, boss.z, alpha)
            var s = boss.scale
            var rot = boss.stateTime * 1.2f
            if (boss.state == Boss.ST_ENTER) {
                val t = FastMath.clamp(boss.stateTime / Boss.ENTER_TIME, 0f, 1f)
                s *= 0.3f + 0.7f * t
                rot = boss.stateTime * 9f * (1.2f - t)  // 3D回転降下演出
            } else if (boss.state == Boss.ST_DYING) {
                s *= 1f - boss.stateTime / Boss.DYING_TIME * 0.7f
            }
            out[o + 3] = s
            out[o + 4] = rot
            out[o + 5] = 5f
            out[o + 6] = 0.8f
            out[o + 7] = 0f
            val f = if (boss.flashT > 0f) 1f else 0f
            out[o + 8] = FastMath.lerp(boss.colR, 1f, f)
            out[o + 9] = FastMath.lerp(boss.colG, 1f, f)
            out[o + 10] = FastMath.lerp(boss.colB, 1f, f)
            out[o + 11] = 0f
            n++
        }
        return n
    }

    fun fillParticles(out: FloatArray, alpha: Float): Int {
        val n = particles.count
        var i = 0
        var o = 0
        while (i < n) {
            val lf = particles.life[i] / particles.maxLife[i]
            out[o] = particles.posX[i]
            out[o + 1] = particles.posY[i]
            out[o + 2] = particles.posZ[i]
            out[o + 3] = FastMath.lerp(particles.scale1[i], particles.scale0[i], lf)
            out[o + 4] = 0f
            out[o + 5] = particles.spriteId[i]
            out[o + 6] = lf
            out[o + 7] = 0f
            out[o + 8] = particles.colR[i]
            out[o + 9] = particles.colG[i]
            out[o + 10] = particles.colB[i]
            out[o + 11] = 0f
            i++
            o += STRIDE
        }
        return n
    }

    /** 自機＋低速時の判定点。戻り値=インスタンス数 */
    fun fillPlayer(out: FloatArray, alpha: Float): Int {
        if (state != ST_PLAY || !player.alive) return 0
        // 無敵点滅
        if (player.invulnTimer > 0f && ((player.invulnTimer * 12f).toInt() and 1) == 1) return 0
        var o = 0
        val x = FastMath.lerp(player.prevX, player.x, alpha)
        val z = FastMath.lerp(player.prevZ, player.z, alpha)
        out[o] = x; out[o + 1] = 0.2f; out[o + 2] = z
        out[o + 3] = 1.7f
        out[o + 4] = 0f
        out[o + 5] = 6f   // 自機スプライト
        out[o + 6] = 1f
        out[o + 7] = 0f
        // #40F0FF
        out[o + 8] = 0.25f; out[o + 9] = 0.94f; out[o + 10] = 1f
        out[o + 11] = 0f
        var n = 1
        o += STRIDE
        if (player.slowMode) {
            // 当たり判定点可視化（白コア＋赤リング）
            out[o] = x; out[o + 1] = 0.25f; out[o + 2] = z
            out[o + 3] = 0.55f
            out[o + 4] = stateTime * 3f
            out[o + 5] = 7f
            out[o + 6] = 1f
            out[o + 7] = 0f
            out[o + 8] = 1f; out[o + 9] = 0.25f; out[o + 10] = 0.38f
            out[o + 11] = 0f
            n++
        }
        return n
    }

    // ================= HUD用ゲッター =================

    val bossActive: Boolean get() = boss.alive && boss.state != Boss.ST_ENTER
    val bossWarning: Boolean get() = boss.state == Boss.ST_ENTER
    val bossHpFrac: Float
        get() {
            val p = boss.currentPhase() ?: return 0f
            return FastMath.clamp(boss.hp / p.hp, 0f, 1f)
        }
    val bossPhasesLeft: Int get() = boss.phases.size - boss.phaseIndex
    val spellNameId: Int
        get() {
            val p = boss.currentPhase() ?: return -1
            return if (p.isSpell && boss.state == Boss.ST_FIGHT) p.nameId else -1
        }
    val spellTimeLeft: Float get() = if (boss.state == Boss.ST_FIGHT) boss.phaseTimer else 0f
    val stageIntroVisible: Boolean get() = state == ST_PLAY && introTimer > 0f
}
