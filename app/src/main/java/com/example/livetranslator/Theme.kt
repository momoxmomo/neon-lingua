package com.example.livetranslator

import android.content.SharedPreferences
import android.graphics.Color

// 画面のテーマと、字幕の色
object Theme {

    // ---------- テーマごとの配色 ----------
    // 上の10色は画面用、下の6色は字幕の初期色
    class Palette(
        val white: Int, val cyan: Int, val red: Int, val yellow: Int,
        val bg: Int, val panel: Int, val dim: Int, val dim2: Int, val dim3: Int, val menuBg: Int,
        val ja: Int, val en: Int, val noiseA: Int, val noiseB: Int, val accent: Int, val overlay: Int,
    )

    const val CYBER = "cyber"
    const val TERMINAL = "terminal"
    const val MATRIX = "matrix"
    const val GHOST = "ghost"

    // テーマの一覧(設定画面に並ぶ順)。id → (表示名, 配色)
    val THEMES: Map<String, Pair<String, Palette>> = linkedMapOf(
        // サイバーパンク風:紺の背景に黄・シアン・赤
        CYBER to ("サイバーパンク" to Palette(
            white = Color.rgb(240, 240, 240), cyan = Color.rgb(94, 246, 255),
            red = Color.rgb(255, 59, 92), yellow = Color.rgb(252, 238, 10),
            bg = Color.rgb(10, 10, 20), panel = Color.rgb(16, 16, 32),
            dim = Color.rgb(90, 90, 110), dim2 = Color.rgb(60, 60, 80), dim3 = Color.rgb(120, 120, 140),
            menuBg = Color.argb(220, 20, 20, 30),
            ja = Color.rgb(240, 240, 240), en = Color.rgb(94, 246, 255),
            noiseA = Color.rgb(255, 59, 92), noiseB = Color.rgb(94, 246, 255),
            accent = Color.rgb(252, 238, 10), overlay = Color.rgb(90, 210, 120))),
        // レトロ端末風:深緑の背景に明るい緑
        TERMINAL to ("レトロ端末" to Palette(
            white = Color.rgb(184, 255, 207), cyan = Color.rgb(150, 255, 180),
            red = Color.rgb(31, 158, 76), yellow = Color.rgb(61, 255, 122),
            bg = Color.rgb(6, 26, 12), panel = Color.rgb(8, 36, 18),
            dim = Color.rgb(31, 120, 64), dim2 = Color.rgb(20, 70, 40), dim3 = Color.rgb(40, 140, 80),
            menuBg = Color.argb(225, 6, 30, 14),
            ja = Color.rgb(184, 255, 207), en = Color.rgb(61, 255, 122),
            noiseA = Color.rgb(31, 158, 76), noiseB = Color.rgb(61, 255, 122),
            accent = Color.rgb(61, 255, 122), overlay = Color.rgb(61, 255, 122))),
        // マトリックス風:真っ黒な背景に、鮮やかな緑と暗い緑
        MATRIX to ("マトリックス" to Palette(
            white = Color.rgb(200, 255, 205), cyan = Color.rgb(120, 255, 150),
            red = Color.rgb(0, 143, 17), yellow = Color.rgb(0, 255, 65),
            bg = Color.rgb(0, 0, 0), panel = Color.rgb(0, 18, 4),
            dim = Color.rgb(0, 110, 30), dim2 = Color.rgb(0, 60, 15), dim3 = Color.rgb(0, 140, 40),
            menuBg = Color.argb(230, 0, 12, 2),
            ja = Color.rgb(215, 255, 220), en = Color.rgb(0, 255, 65),
            noiseA = Color.rgb(0, 143, 17), noiseB = Color.rgb(0, 255, 65),
            accent = Color.rgb(0, 255, 65), overlay = Color.rgb(0, 255, 65))),
        // 攻殻機動隊風:暗い紺青の背景に、ティールの文字とオレンジの差し色
        GHOST to ("攻殻機動隊" to Palette(
            white = Color.rgb(215, 240, 245), cyan = Color.rgb(150, 230, 255),
            red = Color.rgb(255, 128, 48), yellow = Color.rgb(64, 224, 208),
            bg = Color.rgb(6, 14, 22), panel = Color.rgb(10, 26, 38),
            dim = Color.rgb(60, 100, 120), dim2 = Color.rgb(30, 60, 75), dim3 = Color.rgb(90, 140, 160),
            menuBg = Color.argb(225, 8, 22, 32),
            ja = Color.rgb(225, 245, 250), en = Color.rgb(64, 224, 208),
            noiseA = Color.rgb(255, 128, 48), noiseB = Color.rgb(64, 224, 208),
            accent = Color.rgb(255, 128, 48), overlay = Color.rgb(64, 224, 208))),
    )

    var name = CYBER
        private set
    private val palette get() = THEMES[name]?.second ?: THEMES.getValue(CYBER).second

    // ---------- 画面で使う色(テーマで中身が変わる) ----------
    val WHITE get() = palette.white      // 明るい文字
    val CYAN get() = palette.cyan        // 補助の色
    val RED get() = palette.red          // 枠・終了
    val YELLOW get() = palette.yellow    // メインの色(見出し・開始)
    val BG get() = palette.bg            // 背景
    val PANEL get() = palette.panel      // 枠の中の背景
    val DIM get() = palette.dim          // 薄い文字
    val DIM2 get() = palette.dim2        // オフのときの枠
    val DIM3 get() = palette.dim3        // オフのときの文字
    val MENU_BG get() = palette.menuBg   // 字幕メニューのボタン背景
    val DIM_WHITE = Color.rgb(150, 150, 150)   // 差し替え前の訳の色

    // ---------- 字幕の色(設定画面で変更できる。初期色はテーマごと) ----------
    class ColorItem(val key: String, val title: String, private val pick: (Palette) -> Int) {
        val def: Int get() = pick(palette)
    }
    val COLOR_ITEMS = listOf(
        ColorItem("color_ja", "訳文") { it.ja },
        ColorItem("color_en", "原文・文字起こし") { it.en },
        ColorItem("color_noise_a", "ノイズ1") { it.noiseA },
        ColorItem("color_noise_b", "ノイズ2") { it.noiseB },
        ColorItem("color_accent", "ライン・お知らせ") { it.accent },
        ColorItem("color_overlay", "変換中の画面ノイズ") { it.overlay },
    )
    // 選べる色
    val PALETTE = listOf(
        Color.rgb(240, 240, 240), Color.rgb(94, 246, 255), Color.rgb(255, 59, 92),
        Color.rgb(252, 238, 10), Color.rgb(90, 210, 120), Color.rgb(255, 79, 216),
        Color.rgb(255, 159, 28), Color.rgb(167, 139, 250), Color.rgb(77, 159, 255),
        Color.rgb(150, 150, 150), Color.rgb(0, 255, 65), Color.rgb(184, 255, 207),
        Color.rgb(64, 224, 208), Color.rgb(255, 128, 48),
    )

    var jaColor = COLOR_ITEMS[0].def
        private set
    var enColor = COLOR_ITEMS[1].def
        private set
    var noiseA = COLOR_ITEMS[2].def
        private set
    var noiseB = COLOR_ITEMS[3].def
        private set
    var accent = COLOR_ITEMS[4].def
        private set
    var overlayNoise = COLOR_ITEMS[5].def
        private set
    var scanlineFx = true   // 変換中の走査線ノイズを出すか
        private set

    // 保存されたテーマと色を読み込む(各画面の最初と、設定が変わったときに呼ぶ)
    fun load(p: SharedPreferences) {
        name = (p.getString(Prefs.THEME, CYBER) ?: CYBER).takeIf { it in THEMES } ?: CYBER
        fun get(i: Int) = p.getInt(COLOR_ITEMS[i].key, COLOR_ITEMS[i].def)
        jaColor = get(0); enColor = get(1); noiseA = get(2); noiseB = get(3); accent = get(4)
        overlayNoise = get(5)
        scanlineFx = p.getBoolean(Prefs.SCANLINE_FX, true)
    }

    // テーマを切り替える(字幕の色もそのテーマの初期色に戻す)
    fun switchTo(p: SharedPreferences, theme: String) {
        val e = p.edit().putString(Prefs.THEME, theme)
        COLOR_ITEMS.forEach { e.remove(it.key) }
        e.apply()
    }

    fun resetColors(p: SharedPreferences) {
        val e = p.edit()
        COLOR_ITEMS.forEach { e.remove(it.key) }
        e.apply()
    }
}
