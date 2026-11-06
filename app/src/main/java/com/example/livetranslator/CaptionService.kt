package com.example.livetranslator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.random.Random

// 再生中の音声を取り込み → 英語に文字起こし → 日本語に翻訳 → 字幕として表示する
class CaptionService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_EMBED = "embed"   // true:アプリ内YouTubeの画面の下に字幕を表示する

        // アプリ内YouTubeモード:字幕の画面を渡す/終了を知らせる
        var embeddedView: View? = null
        var onEmbeddedReady: ((View) -> Unit)? = null
        var onStopped: (() -> Unit)? = null
        // 字幕ウィンドウの最小化/元に戻す(設定画面を開いている間は最小化する)
        var setOverlayMinimized: ((Boolean) -> Unit)? = null

        private const val CH_ID = "caption"
        private const val SAMPLE_RATE = 16000
        private const val MAX_WORDS = 22               // 話し続ける配信を区切る語数
        private const val MAX_LINES = 10               // 字幕に表示する最大行数
        private const val CONTEXT_LINES = 3            // 翻訳の文脈として渡す直前の英文の数
        private const val SHOW_PARTIAL = true          // 話し中の文字起こしをリアルタイムで表示するか
        private const val SHOW_ORIGINAL_BELOW = true   // 変換後も原文を下に小さく残すか
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var embed = false                          // アプリ内YouTubeモードかどうか

    // ---------- 音声・認識・翻訳 ----------
    private var projection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private var model: Model? = null
    private var translator: Translator? = null
    @Volatile private var translatorReady = false
    private val net = Executors.newSingleThreadExecutor()
    private val deepl = DeepLClient()
    private val gemini = GeminiClient()

    // ---------- 字幕の内容 ----------
    // 1文ぶんの字幕。訳が入れ替わるたびに変換演出を始める
    private class Line(val en: String) {
        var ja = ""
        var natural = false   // Gemini / DeepL の訳が届いたか
        var from = ""         // 演出の開始時の文
        var start = 0L        // 演出の開始時刻
        fun updateJa(newJa: String) {
            from = if (ja.isBlank()) en else ja
            ja = newJa
            start = SystemClock.uptimeMillis()
        }
        fun morphing(now: Long) = ja.isNotBlank() && start > 0 && now - start < CyberDecode.DURATION
    }
    private val history = ArrayDeque<Line>()           // 確定した文
    private val recentEnglish = ArrayDeque<String>()   // 文脈用の直前の英文
    private var lastPartial = ""
    private var partialEn = ""                         // 話し中の英語

    // ---------- 字幕の画面 ----------
    private class Row(val row: LinearLayout, val bar: View, val ja: TextView, val en: TextView, val noise: NoiseView)
    private val rows = mutableListOf<Row>()
    private val sizedViews = mutableListOf<Pair<TextView, Float>>()   // 文字, 基準サイズ
    private var wm: WindowManager? = null
    private var overlay: LinearLayout? = null
    private lateinit var statusView: TextView
    private var applyWindowRatio: ((Float) -> Unit)? = null

    // 変換演出の間だけ、40ミリ秒ごとに描き直す
    private var tickerOn = false
    private val ticker = object : Runnable {
        override fun run() {
            renderLines()
            val now = SystemClock.uptimeMillis()
            if (visibleLines().any { it.morphing(now) }) main.postDelayed(this, 40) else tickerOn = false
        }
    }
    private fun startTicker() {
        if (!tickerOn) { tickerOn = true; main.post(ticker) }
    }

    // 設定画面での変更を、表示中の字幕にすぐ反映する
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when {
            key == null -> {}
            key.startsWith("color_") || key == Prefs.SCANLINE_FX || key == Prefs.THEME -> main.post { applyColors() }
            key == Prefs.WINDOW_RATIO ->
                main.post { applyWindowRatio?.invoke(prefs.getFloat(Prefs.WINDOW_RATIO, Prefs.DEFAULT_WINDOW_RATIO)) }
            key == Prefs.FONT_SCALE ->
                main.post { val sc = fontScale(); sizedViews.forEach { (v, base) -> v.textSize = base * sc } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_NOT_STICKY
        prefs = Prefs.of(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        embed = intent?.getBooleanExtra(EXTRA_EMBED, false) ?: false
        startAsForeground()
        showCaptionWindow()

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)?.also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, main)
        }

        setStatus("モデルを準備中…")
        prepareTranslator()
        StorageService.unpack(this, "model-en-us", "model",
            { m -> model = m; startCapture() },
            { e -> setStatus("音声認識モデルの読み込みに失敗: ${e.message}") })
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        setOverlayMinimized = null
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        running = false
        main.removeCallbacks(ticker)
        worker?.join(500)
        audioRecord?.run { try { stop() } catch (_: Exception) {}; release() }
        projection?.stop()
        net.shutdownNow()
        translator?.close()
        model?.close()
        overlay?.let {
            if (embed) (it.parent as? ViewGroup)?.removeView(it)
            else try { wm?.removeView(it) } catch (_: Exception) {}
        }
        embeddedView = null
        onStopped?.invoke()
        super.onDestroy()
    }

    // 通知(フォアグラウンドサービスに必須)
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ID, "ライブ翻訳", NotificationManager.IMPORTANCE_LOW))
        val notif = Notification.Builder(this, CH_ID)
            .setContentTitle("ライブ翻訳字幕を表示中")
            .setContentText("終了するには字幕の「■ 終了」")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    // =====================================================================
    // 音声の取り込み + 文字起こし(Vosk、端末内)
    // =====================================================================
    @SuppressLint("MissingPermission")
    private fun startCapture() {
        val proj = projection ?: run { setStatus("取り込みの許可が無効になりました"); return }
        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 4)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        } catch (e: Exception) {
            setStatus("音声の取り込みを開始できません: ${e.message}"); return
        }
        audioRecord = record

        val rec = Recognizer(model, SAMPLE_RATE.toFloat())
        running = true
        record.startRecording()
        setStatus("待機中…ライブを再生してください")

        worker = Thread {
            val buf = ShortArray(SAMPLE_RATE / 10)   // 0.1秒ずつ読む(小さいほど低遅延)
            var silentSince = System.currentTimeMillis()
            var warned = false
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue

                // 無音チェック:取り込みを拒否されていると、ずっと無音になる
                if (rms(buf, n) > 50) {
                    silentSince = System.currentTimeMillis(); warned = false
                } else if (!warned && System.currentTimeMillis() - silentSince > 8000) {
                    warned = true
                    setStatus("音声が取れていません(再生中ならYouTube側で拒否されている可能性？)")
                }

                if (rec.acceptWaveForm(buf, n)) {
                    onFinal(JSONObject(rec.result).optString("text"))
                } else {
                    val partial = JSONObject(rec.partialResult).optString("partial")
                    // 切れ目なく話し続ける配信向け:一定の語数で強制的に区切る
                    if (partial.split(" ").size >= MAX_WORDS) onFinal(JSONObject(rec.finalResult).optString("text"))
                    else onPartial(partial)
                }
            }
            rec.close()
        }.apply { start() }
    }

    // 話し中:英語の文字起こしだけをリアルタイムで表示(翻訳は文が確定してから)
    private fun onPartial(text: String) {
        if (!SHOW_PARTIAL || text.isBlank() || text == lastPartial) return
        lastPartial = text
        main.post { partialEn = text; startTicker() }
    }

    private fun onFinal(text: String) {
        lastPartial = ""
        if (text.isBlank()) return
        main.post { translateFinal(text) }
    }

    private fun rms(b: ShortArray, n: Int): Double {
        var s = 0.0
        for (i in 0 until n) s += b[i] * b[i].toDouble()
        return sqrt(s / n)
    }

    // =====================================================================
    // 翻訳
    // =====================================================================
    // ML Kit(端末内)を準備する
    private fun prepareTranslator() {
        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.JAPANESE)
            .build()
        translator = Translation.getClient(opts).also { t ->
            t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { translatorReady = true }
                .addOnFailureListener { setStatus("翻訳モデルのダウンロードに失敗: ${it.message}") }
        }
    }

    // 確定した文:まずML Kitの訳を出し、Gemini / DeepL の自然な訳が届いたら差し替える
    private fun translateFinal(text: String) {
        val line = Line(text)
        history.addLast(line)
        while (history.size > MAX_LINES) history.removeFirst()
        partialEn = ""
        startTicker()

        translator?.takeIf { translatorReady }?.translate(text)?.addOnSuccessListener { ja ->
            if (!line.natural) { line.updateJa(ja); startTicker() }
        }

        // 優先順:Gemini(口語に強い)→ DeepL → ML Kit のまま
        // 設定でオフにしたAPIは使わない。直前の英文を文脈として渡す
        val context = recentEnglish.joinToString("\n")
        val gKey = if (prefs.getBoolean(Prefs.USE_GEMINI, true)) Prefs.geminiKey(prefs) else ""
        val dKey = if (prefs.getBoolean(Prefs.USE_DEEPL, true)) Prefs.deeplKey(prefs) else ""
        val tone = Prefs.tone(prefs)
        if (gKey.isNotBlank() || dKey.isNotBlank()) {
            net.execute {
                val ja = (if (gKey.isNotBlank()) gemini.translate(gKey, text, context, tone) else null)
                    ?: (if (dKey.isNotBlank()) deepl.translate(dKey, text, context, tone) else null)
                if (ja != null) main.post { line.natural = true; line.updateJa(ja); startTicker() }
            }
        }
        recentEnglish.addLast(text)
        while (recentEnglish.size > CONTEXT_LINES) recentEnglish.removeFirst()
    }

    // =====================================================================
    // 字幕の表示
    // =====================================================================
    // 表示する文:最新(話し中)が一番上に出て、古い行が下へ押し出されていく
    private fun visibleLines(): List<Line> {
        val all = mutableListOf<Line>()
        if (SHOW_PARTIAL && partialEn.isNotBlank()) all.add(Line(partialEn))
        all.addAll(history.reversed())
        return all.take(MAX_LINES)
    }

    // 1組ごとに 上:訳(変換演出付き)/ 下:原文
    private fun renderLines() {
        val lines = visibleLines()
        val now = SystemClock.uptimeMillis()
        rows.forEachIndexed { i, r ->
            val line = lines.getOrNull(i)
            if (line == null) {
                r.row.visibility = View.GONE
                r.noise.progress = -1f
                return@forEachIndexed
            }
            r.row.visibility = View.VISIBLE
            val morphing = line.morphing(now)
            val p = (now - line.start).toFloat() / CyberDecode.DURATION

            // 変換中だけノイズを重ね、ときどき行を横にずらして残像を出す
            if (morphing && Theme.scanlineFx) {
                r.noise.progress = p
                r.noise.invalidate()
                val glitch = Random(now / 120 * 3 + i).nextFloat() < 0.3f && p < 0.8f
                val dpx = resources.displayMetrics.density
                r.row.translationX = if (glitch) (if ((now / 120 + i) % 2 == 0L) 9f else -9f) * dpx else 0f
                r.ja.setShadowLayer(if (glitch) 1f else 10f, if (glitch) -3f * dpx else 0f, 0f,
                    if (glitch) Theme.overlayNoise else Theme.enColor)
            } else if (r.noise.progress >= 0f) {
                r.noise.progress = -1f
                r.noise.invalidate()
                r.row.translationX = 0f
                r.ja.setShadowLayer(10f, 0f, 0f, Theme.enColor)
            }

            // 原文:変換し終わったら下に小さく出す
            r.en.text = "» " + line.en
            val converted = line.ja.isNotBlank() && (line.from != line.en || !morphing)
            r.en.visibility = if (SHOW_ORIGINAL_BELOW && converted) View.VISIBLE else View.GONE

            // 訳:届くまでは原文のまま → 届いたらその場で訳文へ変換されていく
            r.ja.text = when {
                line.ja.isBlank() -> CyberDecode.colored(line.en, Theme.enColor)
                morphing -> CyberDecode.frame(line.from, line.ja, p,
                    if (line.from == line.en) Theme.enColor else Theme.DIM_WHITE)
                else -> line.ja
            }
        }
        if (lines.isNotEmpty()) statusView.visibility = View.GONE
    }

    // 色やテーマが変わったとき
    private fun applyColors() {
        Theme.load(prefs)
        rows.forEach {
            it.bar.setBackgroundColor(Theme.accent)
            it.ja.setTextColor(Theme.jaColor)
            it.ja.setShadowLayer(10f, 0f, 0f, Theme.enColor)
            it.en.setTextColor(Theme.enColor)
        }
        if (::statusView.isInitialized) statusView.setTextColor(Theme.accent)
        // 背景もテーマの色に(最小化中で透明のときはそのまま)
        overlay?.let { o ->
            if ((o.background as? ColorDrawable)?.color != Color.TRANSPARENT) o.setBackgroundColor(Theme.BG)
        }
        renderLines()
    }

    private fun fontScale() = prefs.getFloat(Prefs.FONT_SCALE, 1f)

    // 文字サイズを設定に合わせる(あとで変わったときのために覚えておく)
    private fun sized(v: TextView, base: Float): TextView {
        sizedViews.add(v to base)
        v.textSize = base * fontScale()
        return v
    }

    private fun setStatus(s: String) {
        main.post {
            if (::statusView.isInitialized) {
                statusView.text = s
                statusView.visibility = View.VISIBLE
            }
        }
    }

    // =====================================================================
    // 字幕ウィンドウを作る(字幕ウィンドウモード:画面に浮かべる / アプリ内YouTube:画面に組み込む)
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun showCaptionWindow() {
        Theme.load(prefs)
        val w = getSystemService(WindowManager::class.java)
        wm = w
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)   // 透過させない
            setPadding(24, 16, 24, 16)
        }
        // 字幕の中身(最小化のときに丸ごと隠す)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // 最小化したときに出る小さなチップ(タップで元に戻す)
        val chip = TextView(this).apply {
            text = "◤ 字幕"
            sized(this, 16f)
            setTextColor(Theme.accent)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Theme.BG); setStroke(3, Theme.accent) }
            setPadding(24, 12, 24, 12)
            visibility = View.GONE
        }
        box.addView(chip)
        // 高さを変えるつまみ(一番下)
        val handle = TextView(this).apply {
            text = "═══"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Theme.accent)
            setPadding(0, 6, 0, 6)
        }
        box.addView(handle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // お知らせ(準備中・エラーなど)
        statusView = TextView(this).apply {
            setTextColor(Theme.accent); text = "▶ 準備中…"
            sized(this, 20f)
            typeface = Typeface.MONOSPACE
        }
        content.addView(statusView)

        // ---------- ミニ設定(常に表示) ----------
        var minimized = false
        var setMinimized: (Boolean) -> Unit = {}
        fun menuButton(label: String, color: Int, onTap: () -> Unit) = TextView(this).apply {
            text = label
            sized(this, 16f)
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Theme.MENU_BG); setStroke(3, color) }
            setPadding(24, 12, 24, 12)
            setOnClickListener { onTap() }
        }
        fun changeSize(delta: Float) =
            prefs.edit().putFloat(Prefs.FONT_SCALE, (fontScale() + delta).coerceIn(0.5f, 1.5f)).apply()
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 8, 0, 8)
        }
        val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = 16 }
        menu.addView(menuButton("A−", Theme.CYAN) { changeSize(-0.1f) }, gap)
        menu.addView(menuButton("A＋", Theme.CYAN) { changeSize(0.1f) }, gap)
        menu.addView(menuButton("⚙ 設定", Theme.CYAN) {
            // 開いている間は字幕が最小化され、閉じると元に戻る(SettingsActivity 側で切り替え)
            startActivity(Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SettingsActivity.EXTRA_FROM_OVERLAY, !embed))   // 字幕ウィンドウなら閉じたらYouTubeへ
        }, gap)
        if (!embed) menu.addView(menuButton("▁ 最小化", Theme.YELLOW) { setMinimized(true) }, gap)
        menu.addView(menuButton("■ 終了", Theme.RED) {
            stopSelf()
            MainActivity.current?.finishAndRemoveTask()   // アプリの画面が残っていれば閉じる
        }, gap)
        // 幅の狭いスマホでは横にスクロールできる
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(menu) })

        // ---------- 字幕の行(縦ライン+訳+原文、変換中はノイズを重ねる) ----------
        repeat(MAX_LINES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(5), 0, px(5))
                visibility = View.GONE
            }
            val bar = View(this).apply { setBackgroundColor(Theme.accent) }
            val ja = TextView(this).apply {
                setTextColor(Theme.jaColor); sized(this, 28f)
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(10f, 0f, 0f, Theme.enColor)   // ネオンの発光
            }
            val en = TextView(this).apply {
                setTextColor(Theme.enColor); sized(this, 20f)
                typeface = Typeface.MONOSPACE
                alpha = 0.85f
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(10), 0, 0, 0)
                addView(ja); addView(en)
            }
            val noise = NoiseView(this)
            val stack = FrameLayout(this).apply {
                addView(texts)
                addView(noise, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            row.addView(bar, LinearLayout.LayoutParams(px(3), LinearLayout.LayoutParams.MATCH_PARENT))
            row.addView(stack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            content.addView(row)
            rows.add(Row(row, bar, ja, en, noise))
        }

        overlay = box
        if (embed) {
            // アプリ内YouTube:画面の下半分に組み込む(位置・大きさの調整はしない)
            handle.visibility = View.GONE
            embeddedView = box
            onEmbeddedReady?.invoke(box)
            return
        }

        // ---------- 浮かぶウィンドウ(位置・高さ・最小化) ----------
        // 高さは画面に対する割合。収まらない古い行は下からはみ出して見えなくなる
        val screenH = resources.displayMetrics.heightPixels
        var windowH = (screenH * prefs.getFloat(Prefs.WINDOW_RATIO, Prefs.DEFAULT_WINDOW_RATIO)).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, windowH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; y = minOf(600, screenH - windowH) }
        fun relayout() = w.updateViewLayout(box, lp)

        // 最小化:字幕を隠して、右上に小さなチップだけを出す(翻訳は裏で続ける)
        setMinimized = { on ->
            minimized = on
            content.visibility = if (on) View.GONE else View.VISIBLE
            chip.visibility = if (on) View.VISIBLE else View.GONE
            handle.visibility = if (on) View.GONE else View.VISIBLE
            (content.layoutParams as LinearLayout.LayoutParams).apply {
                height = if (on) LinearLayout.LayoutParams.WRAP_CONTENT else 0
                weight = if (on) 0f else 1f
            }
            box.setBackgroundColor(if (on) Color.TRANSPARENT else Theme.BG)
            box.setPadding(if (on) 0 else 24, if (on) 0 else 16, if (on) 0 else 24, if (on) 0 else 16)
            lp.width = if (on) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
            lp.height = if (on) WindowManager.LayoutParams.WRAP_CONTENT else windowH
            lp.gravity = if (on) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.START
            relayout()
        }

        setOverlayMinimized = { on -> main.post { setMinimized(on) } }

        // 最小化中はタップで元に戻す
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (minimized) setMinimized(false)
                return true
            }
        })

        // ドラッグで上下に移動
        var startY = 0
        var touchY = 0f
        box.setOnTouchListener { _, e ->
            detector.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = lp.y; touchY = e.rawY }
                MotionEvent.ACTION_MOVE -> { lp.y = startY + (e.rawY - touchY).toInt(); relayout() }
            }
            true
        }

        // 下のつまみをドラッグして高さを変える(指を離したら保存)
        var resizeStartY = 0f
        var resizeStartH = 0
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { resizeStartY = e.rawY; resizeStartH = lp.height }
                MotionEvent.ACTION_MOVE -> {
                    windowH = (resizeStartH + (e.rawY - resizeStartY)).toInt()
                        .coerceIn((screenH * 0.15f).toInt(), (screenH * 0.95f).toInt())
                    lp.height = windowH
                    relayout()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    prefs.edit().putFloat(Prefs.WINDOW_RATIO, windowH.toFloat() / screenH).apply()
            }
            true
        }
        // 設定画面のスライダーで高さを変えたとき
        applyWindowRatio = { r ->
            windowH = (screenH * r).toInt()
            if (!minimized) { lp.height = windowH; relayout() }
        }

        w.addView(box, lp)
    }
}
