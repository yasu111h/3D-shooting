package com.teamhappslab.shooting3d.game

/**
 * HUDで使う全文字列（起動時にCanvas経由で1枚のテクスチャアトラスに焼き込む）。
 * ゲーム中の文字列生成を避けるため、すべてIDで参照する。
 */
object Labels {
    const val TITLE_LOGO = 0
    const val TAP_START = 1
    const val SCORE = 2
    const val HISCORE = 3
    const val GRAZE = 4
    const val PAUSE = 5
    const val RESUME = 6
    const val RETRY = 7
    const val TO_TITLE = 8
    const val GAME_OVER = 9
    const val STAGE_CLEAR = 10
    const val RESULT = 11
    const val WARNING = 12
    const val BOMB_BTN = 13
    const val SLOW_BTN = 14
    const val HEART = 15
    const val STAR = 16
    const val STAGE1 = 17
    const val BOSS_NAME = 18
    const val SPELL1 = 19
    const val SPELL2 = 20
    const val SPELL3 = 21
    const val PAUSE_ICON = 22
    const val SPELL_BONUS = 23
    const val TOTAL = 24
    const val TAP_CONT = 25

    val strings = arrayOf(
        "BULLET BLOOM",
        "TAP TO START",
        "SCORE",
        "HI",
        "Gz",
        "PAUSE",
        "RESUME",
        "RETRY",
        "TITLE",
        "GAME OVER",
        "STAGE CLEAR",
        "RESULT",
        "WARNING",
        "BOMB",
        "SLOW",
        "♥",
        "✦",
        "STAGE 1",
        "緋焔のヴェスパー",
        "紅蓮符「緋色の螺旋」",
        "焔符「火車輪・八重」",
        "終符「真紅の大花火」",
        "II",
        "SPELL BONUS",
        "TOTAL",
        "TAP TO CONTINUE"
    )
}
