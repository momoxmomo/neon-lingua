package com.example.livetranslator

import android.os.SystemClock
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import kotlin.random.Random

// 翻訳チップが文字を解読していくような変換演出を作る
object CyberDecode {

    const val DURATION = 2200L       // 1文を変換しきるまでの時間(ミリ秒)
    private const val TAIL = 3       // 確定位置の先に出すノイズの文字数
    private const val FLICKER = 120L // ノイズ文字が切り替わる間隔(ミリ秒)

    //private const val GLYPHS = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホ01"
    //private const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉ"
    //private const val GLYPHS = "01234567789ABCDEF"
    private const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"  // 変換中のノイズ(半角カタカナ)

    // 一定間隔でしか変わらないノイズ文字(激しく点滅しないように)
    private fun glyph(pos: Int): Char {
        val bucket = SystemClock.uptimeMillis() / FLICKER
        return GLYPHS[Random(bucket * 31 + pos).nextInt(GLYPHS.length)]
    }

    // ノイズ文字の色:ノイズ1とノイズ2を混ぜる(ノイズ1が4割くらい)
    private fun glyphColor(pos: Int): Int {
        val bucket = SystemClock.uptimeMillis() / FLICKER
        return if (Random(bucket * 17 + pos * 3 + 1).nextFloat() < 0.4f) Theme.noiseA else Theme.noiseB
    }

    /**
     * 変換途中の1コマ:元の文が左から順にノイズを経て訳文に置き換わっていく
     * from:元の文(英語、または差し替え前の訳)、to:新しい訳、progress:0〜1
     * fromColor:まだ変換されていない元の文の色
     */
    fun frame(from: String, to: String, progress: Float, fromColor: Int): CharSequence {
        if (progress >= 1f) return to
        val sb = SpannableStringBuilder()
        // 訳文は左から確定、元の文は同じ割合で左から消えていく
        val revealed = (to.length * progress).toInt()
        val consumed = (from.length * progress).toInt()
        append(sb, to.substring(0, revealed), Theme.jaColor)
        val tail = minOf(TAIL, to.length - revealed)
        for (i in 0 until tail) append(sb, glyph(revealed + i).toString(), glyphColor(revealed + i))
        val restFrom = (consumed + tail).coerceAtMost(from.length)
        append(sb, from.substring(restFrom), fromColor)
        return sb
    }

    // 1色で塗った文字
    fun colored(text: String, color: Int): CharSequence =
        SpannableString(text).apply {
            if (text.isNotEmpty())
                setSpan(ForegroundColorSpan(color), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun append(sb: SpannableStringBuilder, s: String, color: Int) {
        if (s.isEmpty()) return   // 空の文字には色を付けない(ログにエラーが出るため)
        val start = sb.length
        sb.append(s)
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
