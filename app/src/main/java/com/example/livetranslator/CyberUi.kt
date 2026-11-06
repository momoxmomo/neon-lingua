package com.example.livetranslator

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

// 各画面で共通の、テーマに合わせた部品を作る
class CyberUi(private val act: Activity) {

    private val dp = act.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()

    val mono: Typeface = Typeface.MONOSPACE
    val monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    val full = LinearLayout.LayoutParams.MATCH_PARENT
    val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

    // 枠付きの背景
    fun box(fill: Int, stroke: Int, strokeW: Int = 2) = GradientDrawable().apply {
        setColor(fill); setStroke(px(strokeW), stroke)
    }

    // 画面全体(縦に並べる+スクロール)。root に部品を追加していく
    fun screen(): Pair<ScrollView, LinearLayout> {
        act.window.statusBarColor = Theme.BG
        act.window.navigationBarColor = Theme.BG
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            setPadding(px(24), px(40), px(24), px(24))
        }
        val scroll = ScrollView(act).apply {
            setBackgroundColor(Theme.BG)
            isFillViewport = true
            addView(root)
        }
        return scroll to root
    }

    // タイトル・サブタイトル・区切り線をまとめて追加
    fun header(root: LinearLayout, title: String, subtitle: String) {
        root.addView(TextView(act).apply {
            text = title
            textSize = 30f
            typeface = monoBold
            setTextColor(Theme.YELLOW)
            setShadowLayer(12f, 0f, 0f, Theme.RED)
            letterSpacing = 0.05f
        })
        root.addView(TextView(act).apply {
            text = subtitle
            textSize = 14f
            typeface = mono
            setTextColor(Theme.CYAN)
            setPadding(0, px(4), 0, px(12))
        })
        root.addView(View(act).apply { setBackgroundColor(Theme.YELLOW) },
            LinearLayout.LayoutParams(full, px(2)).apply { bottomMargin = px(12) })
    }

    // 小さな見出し
    fun label(t: String) = TextView(act).apply {
        text = t
        textSize = 12f
        typeface = monoBold
        setTextColor(Theme.YELLOW)
        setPadding(0, px(18), 0, px(4))
    }

    // 大きなボタン
    fun button(t: String, fill: Int, textColor: Int, stroke: Int, size: Float = 24f, onTap: () -> Unit) =
        Button(act).apply {
            text = t
            textSize = size
            typeface = monoBold
            isAllCaps = false
            setTextColor(textColor)
            background = box(fill, stroke, 2)
            stateListAnimator = null
            setPadding(0, px(14), 0, px(14))
            setOnClickListener { onTap() }
        }

    // [■] / [  ] のチェックボックス風の切り替え
    fun toggle(onText: String, offText: String, get: () -> Boolean, set: (Boolean) -> Unit, size: Float = 15f) =
        TextView(act).apply {
            textSize = size
            typeface = monoBold
            setPadding(px(12), px(10), px(12), px(10))
            fun show() {
                val on = get()
                text = if (on) "[■] $onText" else "[  ] $offText"
                setTextColor(if (on) Theme.YELLOW else Theme.DIM3)
                background = box(Theme.PANEL, if (on) Theme.YELLOW else Theme.DIM2, 1)
            }
            show()
            setOnClickListener { set(!get()); show() }
        }

    // 選択肢のボタン(テーマ選択など)
    fun choice(title: String, selected: Boolean, onTap: () -> Unit) = TextView(act).apply {
        text = if (selected) "[■] $title" else "[  ] $title"
        textSize = 15f
        typeface = monoBold
        gravity = Gravity.CENTER
        setPadding(px(8), px(10), px(8), px(10))
        setTextColor(if (selected) Theme.YELLOW else Theme.DIM3)
        background = box(Theme.PANEL, if (selected) Theme.YELLOW else Theme.DIM2, 1)
        setOnClickListener { onTap() }
    }

    // APIキーの入力欄(入力した文字は「*」で隠す)
    fun secretField(hint: String, value: String) = EditText(act).apply {
        this.hint = hint
        textSize = 16f
        typeface = mono
        setTextColor(Theme.WHITE)
        setHintTextColor(Theme.DIM)
        background = box(Theme.PANEL, Theme.RED, 1)
        setPadding(px(12), px(10), px(12), px(10))
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        transformationMethod = AsteriskMask
        setText(value)
    }

    // スライダー(min〜max の値を選ぶ)
    fun slider(min: Int, max: Int, value: Int, onChange: (Int, Boolean) -> Unit) = SeekBar(act).apply {
        this.max = max - min
        progress = (value - min).coerceIn(0, max - min)
        progressTintList = ColorStateList.valueOf(Theme.YELLOW)
        thumbTintList = ColorStateList.valueOf(Theme.CYAN)
        progressBackgroundTintList = ColorStateList.valueOf(Theme.RED)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = onChange(p + min, fromUser)
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // プルダウン(選び直したときだけ、選んだ番号を onSelect に渡す)
    fun dropdown(items: List<String>, selected: Int, onSelect: (Int) -> Unit) = Spinner(act).apply {
        fun cell(text: String, drop: Boolean) = TextView(act).apply {
            this.text = if (drop) text else "▼ $text"
            textSize = 15f
            typeface = monoBold
            setTextColor(Theme.YELLOW)
            setBackgroundColor(if (drop) Theme.PANEL else Color.TRANSPARENT)
            setPadding(px(12), px(10), px(12), px(10))
        }
        adapter = object : ArrayAdapter<String>(act, 0, items) {
            override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup) = cell(items[pos], false)
            override fun getDropDownView(pos: Int, convertView: View?, parent: android.view.ViewGroup) = cell(items[pos], true)
        }
        background = box(Theme.PANEL, Theme.YELLOW, 1)
        setPopupBackgroundDrawable(box(Theme.PANEL, Theme.YELLOW, 1))
        setSelection(selected, false)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var last = selected   // 今選ばれている番号(同じ番号の通知は無視する)
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != last) { last = pos; onSelect(pos) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // 色を選ぶ丸いボタン
    fun swatch(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (selected) setStroke(px(3), Color.WHITE) else setStroke(px(1), Theme.DIM2)
    }

    // 画面の一番下の小さな文字
    fun footer() = TextView(act).apply {
        text = "SYS://LINGUA_CHIP  STATUS: ONLINE"
        textSize = 11f
        typeface = mono
        setTextColor(Theme.DIM)
        setPadding(0, px(24), 0, 0)
    }
}

// 入力した文字をすべて「*」で表示する
object AsteriskMask : PasswordTransformationMethod() {
    override fun getTransformation(source: CharSequence, view: View): CharSequence =
        object : CharSequence {
            override val length: Int get() = source.length
            override fun get(index: Int): Char = '*'
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                "*".repeat(endIndex - startIndex)
            override fun toString(): String = "*".repeat(length)
        }
}
