package com.example.livetranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

// 詳細設定画面(表示・翻訳API・字幕の色)
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_OVERLAY = "from_overlay"   // 字幕ウィンドウから開いたか
        private const val YOUTUBE = "com.google.android.youtube"
    }

    private lateinit var geminiInput: EditText
    private lateinit var deeplInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        Theme.load(prefs)

        val ui = CyberUi(this)
        val (scroll, root) = ui.screen()
        root.setPadding(ui.px(20), ui.px(20), ui.px(20), ui.px(20))
        fun add(v: View, top: Int = 6) =
            root.addView(v, LinearLayout.LayoutParams(ui.full, ui.wrap).apply { topMargin = ui.px(top) })
        fun section(t: String) = add(TextView(this).apply {
            text = t
            textSize = 13f
            typeface = ui.monoBold
            setTextColor(Theme.CYAN)
            setPadding(0, 0, 0, ui.px(2))
        }, 20)

        // ---------- 上部:戻る+タイトル ----------
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "◀ 戻る"
            textSize = 16f
            typeface = ui.monoBold
            setTextColor(Theme.CYAN)
            background = ui.box(Theme.PANEL, Theme.CYAN, 1)
            setPadding(ui.px(12), ui.px(8), ui.px(12), ui.px(8))
            setOnClickListener { close() }
        })
        top.addView(TextView(this).apply {
            text = "SETTINGS//"
            textSize = 22f
            typeface = ui.monoBold
            setTextColor(Theme.YELLOW)
            setShadowLayer(10f, 0f, 0f, Theme.RED)
            setPadding(ui.px(16), 0, 0, 0)
        })
        root.addView(top)
        root.addView(View(this).apply { setBackgroundColor(Theme.YELLOW) },
            LinearLayout.LayoutParams(ui.full, ui.px(2)).apply { topMargin = ui.px(12) })

        // ---------- 表示 ----------
        section("■ 表示")
        // テーマ(切り替えると字幕の色もそのテーマの初期色に戻る)
        val themeIds = Theme.THEMES.keys.toList()
        add(ui.dropdown(Theme.THEMES.values.map { "THEME: ${it.first}" }, themeIds.indexOf(Theme.name)) { i ->
            if (themeIds[i] != Theme.name) {
                Theme.switchTo(prefs, themeIds[i])
                recreate()   // 新しい配色で画面を作り直す
            }
        })
        // 文字サイズ(50%〜150%)
        val sizeLabel = ui.label("")
        root.addView(sizeLabel)
        val initSize = (prefs.getFloat(Prefs.FONT_SCALE, 1f) * 100).toInt()
        sizeLabel.text = "文字サイズ  $initSize%"
        add(ui.slider(50, 150, initSize) { pct, _ ->
            sizeLabel.text = "文字サイズ  $pct%"
            prefs.edit().putFloat(Prefs.FONT_SCALE, pct / 100f).apply()
        }, 0)
        // 字幕ウィンドウの高さ(20%〜90%。字幕の下のつまみでも変えられる)
        val heightLabel = ui.label("")
        root.addView(heightLabel)
        val initH = (prefs.getFloat(Prefs.WINDOW_RATIO, Prefs.DEFAULT_WINDOW_RATIO) * 100).toInt()
        heightLabel.text = "字幕ウィンドウの高さ  $initH%"
        add(ui.slider(20, 90, initH) { pct, fromUser ->
            heightLabel.text = "字幕ウィンドウの高さ  $pct%"
            if (fromUser) prefs.edit().putFloat(Prefs.WINDOW_RATIO, pct / 100f).apply()
        }, 0)
        // 変換中の走査線ノイズ
        add(ui.toggle("走査線ノイズ:ON", "走査線ノイズ:OFF",
            { prefs.getBoolean(Prefs.SCANLINE_FX, true) },
            { prefs.edit().putBoolean(Prefs.SCANLINE_FX, it).apply() }), 12)

        // ---------- 翻訳API(オフ・空欄なら端末内のML Kitだけで翻訳) ----------
        section("■ 翻訳API")
        // 訳の口調(Gemini・DeepLの訳に反映。端末内のML Kitの訳は変わらない)
        root.addView(ui.label("訳の口調"))
        val toneRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun showTones() {
            toneRow.removeAllViews()
            Prefs.TONE_NAMES.forEachIndexed { i, name ->
                toneRow.addView(ui.choice(name, Prefs.tone(prefs) == i) {
                    prefs.edit().putInt(Prefs.TONE, i).apply(); showTones()
                }, LinearLayout.LayoutParams(0, ui.wrap, 1f).apply { if (i > 0) marginStart = ui.px(6) })
            }
        }
        showTones()
        add(toneRow, 0)
        add(TextView(this).apply {
            text = "※ Gemini・DeepL の訳に反映されます(DeepL は まじめ/それ以外 の2段階)"
            textSize = 11f
            typeface = ui.mono
            setTextColor(Theme.DIM3)
        }, 4)
        fun apiToggle(name: String, key: String) = ui.toggle("$name を使う", "$name を使わない",
            { prefs.getBoolean(key, true) }, { prefs.edit().putBoolean(key, it).apply() })
        add(apiToggle("Gemini", Prefs.USE_GEMINI))
        // キーの取得ページへの小さなリンク
        fun keyLink(t: String, url: String) = add(TextView(this).apply {
            text = "▸ $t"
            textSize = 11f
            typeface = ui.mono
            setTextColor(Theme.CYAN)
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(ui.px(2), 0, 0, 0)
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
            }
        }, 4)
        geminiInput = ui.secretField("Gemini APIキー(未入力)", Prefs.geminiKey(prefs))
        add(geminiInput, 4)
        keyLink("Gemini APIキーを取得(Google AI Studio)", "https://aistudio.google.com/apikey")
        add(apiToggle("DeepL", Prefs.USE_DEEPL), 12)
        deeplInput = ui.secretField("DeepL APIキー(未入力)", Prefs.deeplKey(prefs))
        add(deeplInput, 4)
        keyLink("DeepL APIキーを取得(DeepL API Free)", "https://www.deepl.com/ja/pro-api")

        // ---------- 字幕の色(変える場所をプルダウンで選んで、色をタップ) ----------
        section("■ 字幕の色")
        var item = Theme.COLOR_ITEMS[0]
        val swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = Theme.PALETTE.map { c -> View(this) to c }
        fun refresh() {
            val cur = prefs.getInt(item.key, item.def)
            swatches.forEach { (v, c) -> v.background = ui.swatch(c, c == cur) }   // 選ばれている色は白い太枠
        }
        swatches.forEach { (v, c) ->
            v.setOnClickListener { prefs.edit().putInt(item.key, c).apply(); refresh() }
            swatchRow.addView(v, LinearLayout.LayoutParams(ui.px(34), ui.px(34)).apply { marginEnd = ui.px(10) })
        }
        add(ui.dropdown(Theme.COLOR_ITEMS.map { it.title }, 0) { i -> item = Theme.COLOR_ITEMS[i]; refresh() })
        add(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(swatchRow) }, 10)
        add(TextView(this).apply {
            text = "↺ 色を初期設定に戻す"
            textSize = 14f
            typeface = ui.mono
            setTextColor(Theme.CYAN)
            setPadding(0, ui.px(6), 0, 0)
            setOnClickListener { Theme.resetColors(prefs); refresh() }
        }, 8)
        refresh()

        // ---------- このアプリについて ----------
        section("■ このアプリについて")
        add(TextView(this).apply {
            text = "ネオン・リンガ v${BuildConfig.VERSION_NAME}\n" +
                "YouTubeなどの英語音声を、端末内で文字起こしして日本語字幕にします。\n\n" +
                "使用しているソフトウェア:\n" +
                "・Vosk 音声認識と英語モデル(Apache License 2.0 / Alpha Cephei)\n" +
                "・ML Kit 翻訳(Google ML Kit 利用規約)\n" +
                "・JNA(Apache License 2.0)\n" +
                "・AndroidX(Apache License 2.0)\n\n" +
                "Gemini・DeepL を使う場合は、各サービスの利用規約に従ってご自身のAPIキーでご利用ください。\n" +
                "本アプリは YouTube・Google・DeepL とは関係のない個人制作のアプリです。"
            textSize = 11f
            typeface = ui.mono
            setTextColor(Theme.DIM3)
            setLineSpacing(0f, 1.2f)
        }, 4)

        root.addView(ui.footer())
        setContentView(scroll)

        // 戻る操作も「◀ 戻る」と同じ動きにする
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = close()
        })
    }

    // 閉じる:字幕ウィンドウから開いたときは、YouTubeを前面(全画面)に戻してから閉じる
    private fun close() {
        if (intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false)) {
            packageManager.getLaunchIntentForPackage(YOUTUBE)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                try { startActivity(it) } catch (_: Exception) {}
            }
        }
        finish()
    }

    // 設定画面を開いている間は、字幕ウィンドウを最小化しておく
    override fun onStart() {
        super.onStart()
        CaptionService.setOverlayMinimized?.invoke(true)
    }

    // 画面を離れるときにAPIキーを保存する
    override fun onPause() {
        super.onPause()
        Prefs.of(this).edit()
            .putString(Prefs.DEEPL_KEY, deeplInput.text.toString().trim())
            .putString(Prefs.GEMINI_KEY, geminiInput.text.toString().trim())
            .apply()
    }

    // 閉じたら字幕を元に戻す(テーマ切り替えで作り直すときは戻さない)
    override fun onDestroy() {
        if (!isChangingConfigurations) CaptionService.setOverlayMinimized?.invoke(false)
        super.onDestroy()
    }
}
