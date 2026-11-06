package com.example.livetranslator

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

// アプリ内YouTubeモード:上にYouTube、下に字幕
class PlayerActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var captionArea: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        Theme.load(prefs)
        val ui = CyberUi(this)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
        }

        // 上の細いバー:戻る / タイトル / 終了
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.px(10), ui.px(4), ui.px(10), ui.px(4))
            setBackgroundColor(Theme.BG)
        }
        fun barButton(t: String, color: Int, onTap: () -> Unit) = TextView(this).apply {
            text = t; textSize = 14f
            typeface = ui.monoBold
            setTextColor(color)
            setPadding(ui.px(10), ui.px(6), ui.px(10), ui.px(6))
            setOnClickListener { onTap() }
        }
        fun spacer() = View(this).also { bar.addView(it, LinearLayout.LayoutParams(0, 1, 1f)) }
        bar.addView(barButton("◀ 戻る", Theme.CYAN) { if (web.canGoBack()) web.goBack() })
        spacer()
        bar.addView(TextView(this).apply {
            text = "NEON//LINGUA"; textSize = 12f
            typeface = ui.mono; setTextColor(Theme.YELLOW)
        })
        spacer()
        bar.addView(barButton("■ 終了", Theme.RED) { quit() })
        root.addView(bar)

        // YouTube(スマホ版サイト)。リンクはこの画面の中で開く
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = false
            }
            loadUrl("https://m.youtube.com/")
        }
        // 字幕の割合(詳細設定の WINDOW_HEIGHT と共通)
        val captionRatio = prefs.getFloat(Prefs.WINDOW_RATIO, Prefs.DEFAULT_WINDOW_RATIO).coerceIn(0.2f, 0.8f)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f - captionRatio))

        // 字幕(CaptionService が作った字幕の画面をここに組み込む)
        captionArea = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        root.addView(captionArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, captionRatio))
        captionArea.addView(TextView(this).apply {
            text = "> 字幕を準備中…"; textSize = 16f
            typeface = ui.mono; setTextColor(Theme.YELLOW)
            setPadding(ui.px(16), ui.px(16), ui.px(16), ui.px(16))
        })
        setContentView(root)

        CaptionService.onEmbeddedReady = { v -> runOnUiThread { attachCaption(v) } }
        CaptionService.embeddedView?.let { attachCaption(it) }
        // 字幕の「■ 終了」で止まったら、この画面も閉じる
        CaptionService.onStopped = { runOnUiThread { finishAndRemoveTask() } }

        // 戻る操作はまずYouTubeの中で戻る
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })
    }

    private fun attachCaption(v: View) {
        (v.parent as? ViewGroup)?.removeView(v)
        captionArea.removeAllViews()
        captionArea.addView(v, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun quit() {
        stopService(Intent(this, CaptionService::class.java))
        MainActivity.current?.finishAndRemoveTask()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        CaptionService.onEmbeddedReady = null
        CaptionService.onStopped = null
        if (isFinishing) stopService(Intent(this, CaptionService::class.java))
        web.destroy()
        super.onDestroy()
    }
}
