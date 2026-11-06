package com.example.livetranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

// 変換中の行だけに重ねる、緑の走査線・砂嵐・流れるライン
class NoiseView(ctx: Context) : View(ctx) {

    /** 変換の進み具合(0〜1)。マイナスのときは何も描かない */
    var progress = -1f

    private val paint = Paint()
    private val dp = ctx.resources.displayMetrics.density

    // 自分からは高さを取らない(文字の高さにだけ合わせる)。
    // これがないと行が画面いっぱいまで伸びて、ほかの行が見えなくなる
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(c: Canvas) {
        if (progress < 0f || progress >= 1f) return
        // 終わり際(最後の25%)でノイズがすっと消える
        val fade = ((1f - progress) * 4f).coerceIn(0f, 1f)
        val col = Theme.overlayNoise
        val r = Color.red(col); val g = Color.green(col); val b = Color.blue(col)
        val w = width.toFloat(); val h = height.toFloat()
        val now = SystemClock.uptimeMillis()

        // うっすら全体を緑に染める
        paint.color = Color.argb((28 * fade).toInt(), r, g, b)
        c.drawRect(0f, 0f, w, h, paint)

        // 走査線(太く・濃く。2本おきに明るい線)
        var y = 0f
        var n = 0
        while (y < h) {
            paint.color = Color.argb(((if (n % 2 == 0) 110 else 60) * fade).toInt(), r, g, b)
            c.drawRect(0f, y, w, y + dp, paint)
            y += dp * 2.5f
            n++
        }

        // 砂嵐(0.08秒ごとに入れ替わる。数も明るさも多め)
        val rnd = Random(now / 80 + id)
        repeat(140) {
            paint.color = Color.argb(((100 + rnd.nextInt(155)) * fade).toInt(), r, g, b)
            val x = rnd.nextFloat() * w
            val yy = rnd.nextFloat() * h
            c.drawRect(x, yy, x + dp * (1 + rnd.nextInt(3)), yy + dp * (1 + rnd.nextInt(2)), paint)
        }

        // ときどき走る横ずれの帯(グリッチ)
        if (rnd.nextFloat() < 0.35f) {
            val gy = rnd.nextFloat() * h
            val gh = dp * (3 + rnd.nextInt(6))
            paint.color = Color.argb((90 * fade).toInt(), r, g, b)
            c.drawRect(0f, gy, w, gy + gh, paint)
            paint.color = Color.argb((200 * fade).toInt(), r, g, b)
            var x = 0f
            while (x < w) {
                if (rnd.nextFloat() < 0.5f) c.drawRect(x, gy, x + dp * 4, gy + dp, paint)
                x += dp * 7
            }
        }

        // 上から下へ流れる光るライン(2本、太め)
        paint.color = Color.argb((220 * fade).toInt(), r, g, b)
        val by1 = (now / 12 % 100) / 100f * h
        c.drawRect(0f, by1, w, by1 + dp * 3, paint)
        paint.color = Color.argb((140 * fade).toInt(), r, g, b)
        val by2 = ((now / 12 + 50) % 100) / 100f * h
        c.drawRect(0f, by2, w, by2 + dp * 2, paint)
    }
}
