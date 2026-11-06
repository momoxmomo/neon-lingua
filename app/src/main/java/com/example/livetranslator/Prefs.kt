package com.example.livetranslator

import android.content.Context
import android.content.SharedPreferences

// 設定の保存先と、保存名(キー)の一覧
object Prefs {
    private const val NAME = "settings"

    const val DEEPL_KEY = "deepl_key"
    const val GEMINI_KEY = "gemini_key"
    const val USE_DEEPL = "use_deepl"
    const val USE_GEMINI = "use_gemini"
    const val FONT_SCALE = "font_scale"       // 字幕の文字サイズ(0.5〜1.5)
    const val WINDOW_RATIO = "window_ratio"   // 字幕ウィンドウの高さ(画面に対する割合)
    const val SCANLINE_FX = "scanline_fx"     // 変換中の走査線ノイズ
    const val THEME = "theme"                 // 画面のテーマ
    const val TONE = "tone"                   // 訳の口調(0:まじめ 1:ふつう 2:ワル)

    const val TONE_SERIOUS = 0
    const val TONE_NORMAL = 1
    const val TONE_BAD = 2
    val TONE_NAMES = listOf("まじめ", "ふつう", "ワル")

    const val DEFAULT_WINDOW_RATIO = 0.6f

    fun of(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun tone(p: SharedPreferences) = p.getInt(TONE, TONE_NORMAL).coerceIn(TONE_SERIOUS, TONE_BAD)

    // 入力済みのAPIキー(未入力なら空。アプリを更新しても保存した値は残る)
    fun deeplKey(p: SharedPreferences) = p.getString(DEEPL_KEY, "").orEmpty().trim()
    fun geminiKey(p: SharedPreferences) = p.getString(GEMINI_KEY, "").orEmpty().trim()
}
