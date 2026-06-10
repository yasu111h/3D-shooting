package com.teamhappslab.shooting3d.game

import com.teamhappslab.shooting3d.engine.FastMath
import com.teamhappslab.shooting3d.engine.XorShift

/** ボスのフェーズ定義（通常攻撃 or スペルカード） */
class BossPhase(
    val hp: Float,
    val timeLimit: Float,
    val isSpell: Boolean,
    val nameId: Int,           // スペカ名ラベルID（通常は-1）
    val movePattern: Int,      // 0=ゆらゆら 1=8の字 2=ほぼ静止
    val defA: EmitterDef?,
    val defB: EmitterDef? = null,
    val defC: EmitterDef? = null
)

/**
 * ステージ1ボス「緋焔のヴェスパー」。通常2＋スペカ3のフェーズ制。
 */
class Boss {
    companion object {
        const val ST_HIDDEN = 0
        const val ST_ENTER = 1      // WARNING演出＋降下（無敵）
        const val ST_FIGHT = 2
        const val ST_TRANSITION = 3 // スペカ宣言カットイン（無敵・弾消去）
        const val ST_DYING = 4
        const val ST_DEAD = 5

        const val ENTER_TIME = 3.5f
        const val CUTIN_TIME = 2.0f
        const val DYING_TIME = 1.8f

        // イベント（worldが消費）
        const val EV_NONE = 0
        const val EV_PHASE_BROKEN = 1   // フェーズ撃破（弾アイテム化）
        const val EV_SPELL_DECLARE = 2  // スペカ宣言開始
        const val EV_DEFEATED = 3       // 全フェーズ撃破
    }

    var state = ST_HIDDEN
    var x = 0f; var z = -12f
    var prevX = 0f; var prevZ = -12f
    var hp = 0f
    var phaseIndex = 0
    var stateTime = 0f
    var phaseTimer = 0f
    var flashT = 0f
    var event = EV_NONE
    val radius = 1.6f
    val scale = 4.2f
    // ボス色 #FF3060
    val colR = 1f; val colG = 0x30 / 255f; val colB = 0x60 / 255f

    private val emA = Emitter()
    private val emB = Emitter()
    private val emC = Emitter()

    lateinit var phases: Array<BossPhase>

    fun currentPhase(): BossPhase? =
        if (phaseIndex < phases.size) phases[phaseIndex] else null

    val alive: Boolean get() = state != ST_HIDDEN && state != ST_DEAD
    val invincible: Boolean get() = state != ST_FIGHT

    fun reset() {
        state = ST_HIDDEN
        phaseIndex = 0
        x = 0f; z = -22f
        prevX = x; prevZ = z
        stateTime = 0f
        event = EV_NONE
        flashT = 0f
    }

    fun startEntry() {
        state = ST_ENTER
        stateTime = 0f
        x = 0f; z = -24f
        prevX = x; prevZ = z
    }

    private fun beginPhase() {
        val p = currentPhase() ?: return
        hp = p.hp
        phaseTimer = p.timeLimit
        emA.set(p.defA)
        emB.set(p.defB)
        emC.set(p.defC)
        state = ST_FIGHT
        stateTime = 0f
    }

    fun damage(d: Float) {
        if (state != ST_FIGHT) return
        hp -= d
        flashT = 0.06f
    }

    fun update(dt: Float, px: Float, pz: Float, bullets: BulletPool,
               rng: XorShift, rank: Float) {
        prevX = x; prevZ = z
        stateTime += dt
        if (flashT > 0f) flashT -= dt

        when (state) {
            ST_ENTER -> {
                // 3D回転降下（zを-24→-12へ）
                val t = FastMath.clamp(stateTime / ENTER_TIME, 0f, 1f)
                val e = 1f - (1f - t) * (1f - t)
                z = -24f + 12f * e
                if (stateTime >= ENTER_TIME) {
                    val p = currentPhase()
                    if (p != null && p.isSpell) {
                        state = ST_TRANSITION
                        stateTime = 0f
                        event = EV_SPELL_DECLARE
                    } else {
                        beginPhase()
                    }
                }
            }
            ST_TRANSITION -> {
                if (stateTime >= CUTIN_TIME) beginPhase()
            }
            ST_FIGHT -> {
                val p = currentPhase() ?: return
                move(p.movePattern, dt)
                emA.update(dt, x, z, px, pz, bullets, rng, rank)
                emB.update(dt, x, z, px, pz, bullets, rng, rank)
                emC.update(dt, x, z, px, pz, bullets, rng, rank)
                phaseTimer -= dt
                if (hp <= 0f || phaseTimer <= 0f) {
                    phaseIndex++
                    if (phaseIndex >= phases.size) {
                        state = ST_DYING
                        stateTime = 0f
                        event = EV_DEFEATED
                    } else {
                        event = EV_PHASE_BROKEN
                        val np = phases[phaseIndex]
                        if (np.isSpell) {
                            state = ST_TRANSITION
                            stateTime = 0f
                        } else {
                            beginPhase()
                        }
                    }
                }
            }
            ST_DYING -> {
                if (stateTime >= DYING_TIME) state = ST_DEAD
            }
        }
    }

    private fun move(pattern: Int, dt: Float) {
        when (pattern) {
            0 -> { // ゆらゆら横移動
                x += FastMath.sin(stateTime * 0.9f) * 3.0f * dt
                z = -12f + FastMath.sin(stateTime * 0.5f) * 1.0f
            }
            1 -> { // 8の字
                x = FastMath.sin(stateTime * 0.7f) * 5.0f
                z = -12f + FastMath.sin(stateTime * 1.4f) * 1.8f
            }
            else -> { // ほぼ静止（最終スペカ）
                x += (0f - x) * 1.5f * dt
                z += (-11f - z) * 1.5f * dt
            }
        }
        x = FastMath.clamp(x, -7f, 7f)
    }

    /** worldがイベントを消費 */
    fun consumeEvent(): Int {
        val e = event
        event = EV_NONE
        return e
    }
}
