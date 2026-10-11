package com.mytranslator.app

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation

class SunFloatingView(context: Context) : View(context) {
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB300") }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#FFA000")
        strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF8A80") }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D4037")
        strokeWidth = 4f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    var isTranslating = false
        set(value) {
            field = value
            if (value) startSpin() else stopSpin()
            postInvalidate()
        }

    private fun startSpin() {
        val anim = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 1200; repeatCount = Animation.INFINITE; interpolator = LinearInterpolator()
        }
        startAnimation(anim)
    }

    private fun stopSpin() { clearAnimation() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = width * 0.32f

        canvas.save()
        for (i in 0 until 8) {
            canvas.drawLine(cx, cy - r - 6f, cx, cy - r - 16f, rayPaint)
            canvas.rotate(45f, cx, cy)
        }
        canvas.restore()

        canvas.drawCircle(cx, cy, r, bodyPaint)
        canvas.drawCircle(cx - r * 0.45f, cy + r * 0.15f, 6f, blushPaint)
        canvas.drawCircle(cx + r * 0.45f, cy + r * 0.15f, 6f, blushPaint)

        if (!isTranslating) {
            canvas.drawCircle(cx - r * 0.3f, cy - r * 0.15f, 3.5f, facePaint.apply { style = Paint.Style.FILL })
            canvas.drawCircle(cx + r * 0.3f, cy - r * 0.15f, 3.5f, facePaint.apply { style = Paint.Style.FILL })
            facePaint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(cx - 10f, cy, cx + 10f, cy + 12f), 20f, 140f, false, facePaint)
        } else {
            facePaint.style = Paint.Style.STROKE
            canvas.drawLine(cx - 16f, cy - 8f, cx - 8f, cy - 4f, facePaint)
            canvas.drawLine(cx - 8f, cy - 4f, cx - 16f, cy, facePaint)
            canvas.drawLine(cx + 16f, cy - 8f, cx + 8f, cy - 4f, facePaint)
            canvas.drawLine(cx + 8f, cy - 4f, cx + 16f, cy, facePaint)
            canvas.drawCircle(cx, cy + 6f, 4f, facePaint)
        }
    }
}
