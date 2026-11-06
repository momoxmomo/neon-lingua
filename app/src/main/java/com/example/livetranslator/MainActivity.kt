package com.example.livetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// 最初の画面:開始(2種類)・終了・詳細設定
class MainActivity : AppCompatActivity() {

    companion object {
        // 字幕の「■ 終了」からアプリを閉じるため
        var current: MainActivity? = null
    }

    private lateinit var info: TextView
    private var createdTheme = ""   // この画面を作ったときのテーマ
    private var embedMode = false   // true:アプリ内YouTubeで開始

    // 画面・音声の取り込み許可の結果
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode != RESULT_OK || data == null) {
                info.text = "> 音声の取り込みが許可されませんでした"
                return@registerForActivityResult
            }
            val service = Intent(this, CaptionService::class.java)
                .putExtra(CaptionService.EXTRA_CODE, res.resultCode)
                .putExtra(CaptionService.EXTRA_DATA, data)
                .putExtra(CaptionService.EXTRA_EMBED, embedMode)
            if (embedMode) {
                // 先に画面を開いておき、字幕ができたら下に組み込む
                startActivity(Intent(this, PlayerActivity::class.java))
                ContextCompat.startForegroundService(this, service)
                info.text = "> アプリ内YouTubeで開始しました"
            } else {
                ContextCompat.startForegroundService(this, service)
                info.text = "> 開始しました。YouTubeアプリでライブを再生してください"
                moveTaskToBack(true)
            }
        }

    // マイク・通知の権限の結果
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission()) startFlow()
            else info.text = "> マイク権限が必要です(再生音の取り込みに使います)"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(Prefs.of(this))
        createdTheme = Theme.name
        current = this

        val ui = CyberUi(this)
        val (scroll, root) = ui.screen()
        ui.header(root, "NEON//LINGUA", "リアルタイム翻訳字幕  EN ▸ JA  v${BuildConfig.VERSION_NAME}")

        // 状態表示(ターミナル風)
        info = TextView(this).apply {
            text = "> 「開始」を押してください"
            textSize = 18f
            typeface = ui.mono
            setTextColor(Theme.CYAN)
            background = ui.box(Theme.PANEL, Theme.CYAN, 1)
            setPadding(ui.px(14), ui.px(12), ui.px(14), ui.px(12))
        }
        root.addView(info, LinearLayout.LayoutParams(ui.full, ui.wrap).apply { topMargin = ui.px(8) })

        fun add(v: android.view.View, top: Int) =
            root.addView(v, LinearLayout.LayoutParams(ui.full, ui.wrap).apply { topMargin = ui.px(top) })

        // 開始:YouTubeアプリの上に字幕を重ねる
        add(ui.button("▶ 開始(字幕ウィンドウ)", Theme.YELLOW, Theme.BG, Theme.YELLOW, 20f) {
            embedMode = false; startFlow()
        }, 28)
        // 開始:アプリの中でYouTubeを開き、下に字幕を出す
        add(ui.button("▶ 開始(アプリ内YouTube)", Color.TRANSPARENT, Theme.YELLOW, Theme.YELLOW, 20f) {
            embedMode = true; startFlow()
        }, 12)
        // 終了:字幕を止めて、アプリも閉じる
        add(ui.button("■ 終了", Color.TRANSPARENT, Theme.RED, Theme.RED) {
            stopService(Intent(this, CaptionService::class.java))
            finishAndRemoveTask()
        }, 12)
        // 詳細設定(APIキー・文字サイズ・色・演出)は別画面
        add(ui.button("⚙ 詳細設定", Color.TRANSPARENT, Theme.CYAN, Theme.CYAN, 18f) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, 28)

        root.addView(ui.footer())
        setContentView(scroll)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    // 開始までの手順:重ねて表示の許可 → マイク権限 → 画面・音声の取り込み許可
    private fun startFlow() {
        // 1. 他のアプリの上に表示する権限(字幕ウィンドウのときだけ必要)
        if (!embedMode && !Settings.canDrawOverlays(this)) {
            info.text = "> 「他のアプリの上に重ねて表示」を許可してから、戻って「開始」をもう一度押してください"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        // 2. マイク権限と通知権限
        if (!hasAudioPermission()) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permLauncher.launch(perms.toTypedArray())
            return
        }
        // 3. 画面・音声の取り込み許可
        // Android 14以降は「画面全体」だけを選ぶ形にして、アプリの一覧を出さない
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    // 詳細設定でテーマを変えて戻ってきたら、新しい配色で作り直す
    override fun onResume() {
        super.onResume()
        Theme.load(Prefs.of(this))
        if (createdTheme.isNotEmpty() && createdTheme != Theme.name) recreate()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
}
