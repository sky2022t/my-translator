package com.mytranslator.app

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

class SolarOrbDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
    }
    private val rectF = RectF()
    private var boostUntilMillis = 0L

    fun triggerPulseBoost() {
        boostUntilMillis = SystemClock.uptimeMillis() + 1800L
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val now = SystemClock.uptimeMillis()
        val isBoost = TranslateEngine.isTranslatingState || now < boostUntilMillis
        val speedMultiplier = if (isBoost) 3.6f else 1.0f
        val t = (now % 60000L) / 1000f * speedMultiplier

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val maxR = (b.width().coerceAtMost(b.height()) / 2f)
        val coreR = maxR * (0.50f + 0.03f * sin(t * 2.5f))

        // 1. 外部深空暗色微光底座（增强在浅色背景下的对比度）
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, maxR * 0.96f,
            intArrayOf(
                Color.argb(180, 15, 23, 42),
                Color.argb(120, 30, 27, 75),
                Color.argb(0, 15, 23, 42)
            ),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, maxR * 0.96f, paint)

        // 2. 翻译状态能量扩散脉冲环
        if (isBoost) {
            val pulseProgress = ((now % 900L) / 900f)
            val pulseR = coreR + (maxR - coreR) * pulseProgress
            val pulseAlpha = ((1f - pulseProgress) * 200).toInt().coerceIn(0, 255)
            orbitPaint.shader = null
            orbitPaint.color = Color.argb(pulseAlpha, 56, 189, 248)
            orbitPaint.strokeWidth = 3.5f
            canvas.drawCircle(cx, cy, pulseR, orbitPaint)
        }

        // 3. 呼吸日冕金芒光晕
        paint.shader = RadialGradient(
            cx, cy, maxR * 0.85f,
            intArrayOf(
                Color.argb(220, 255, 193, 7),
                Color.argb(95, 255, 111, 0),
                Color.argb(0, 255, 87, 34)
            ),
            floatArrayOf(0.2f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, maxR * 0.85f, paint)

        // 4. 绘制双倾角 3D 星轨后半环（Z < 0）
        drawOrbitLayer(canvas, cx, cy, maxR * 0.82f, maxR * 0.32f, -28f, t * 1.8f, isBackHalf = true)
        drawOrbitLayer(canvas, cx, cy, maxR * 0.74f, maxR * 0.26f, 32f, -t * 1.3f + 2.1f, isBackHalf = true)

        // 5. 3D 太阳主星体（多层高光球体渐变）
        val lightX = cx - coreR * 0.30f
        val lightY = cy - coreR * 0.30f
        paint.shader = RadialGradient(
            lightX, lightY, coreR * 1.35f,
            intArrayOf(
                Color.parseColor("#FFFDE7"),
                Color.parseColor("#FFD54F"),
                Color.parseColor("#FF8F00"),
                Color.parseColor("#D84315")
            ),
            floatArrayOf(0f, 0.32f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreR, paint)

        // 6. 球体左上方玻璃高光折射弧
        paint.shader = LinearGradient(
            cx - coreR, cy - coreR, cx, cy,
            Color.argb(190, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - coreR * 0.22f, cy - coreR * 0.22f, coreR * 0.45f, paint)

        // 7. 绘制双倾角 3D 星轨前半环与环绕光子粒子（Z >= 0）
        drawOrbitLayer(canvas, cx, cy, maxR * 0.82f, maxR * 0.32f, -28f, t * 1.8f, isBackHalf = false)
        drawOrbitLayer(canvas, cx, cy, maxR * 0.74f, maxR * 0.26f, 32f, -t * 1.3f + 2.1f, isBackHalf = false)

        // 持续触发 60fps 帧动画
        invalidateSelf()
    }

    private fun drawOrbitLayer(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        tiltDeg: Float,
        angleRad: Float,
        isBackHalf: Boolean
    ) {
        canvas.save()
        canvas.rotate(tiltDeg, cx, cy)
        rectF.set(cx - rx, cy - ry, cx + rx, cy + ry)

        orbitPaint.strokeWidth = 2.0f
        orbitPaint.color = if (isBackHalf) Color.argb(70, 255, 213, 79) else Color.argb(175, 255, 236, 179)
        val startAngle = if (isBackHalf) 180f else 0f
        canvas.drawArc(rectF, startAngle, 180f, false, orbitPaint)

        // 计算轨道光子坐标
        val px = cx + rx * cos(angleRad)
        val py = cy + ry * sin(angleRad)
        val sinVal = sin(angleRad)
        val inCurrentHalf = if (isBackHalf) (sinVal < 0f) else (sinVal >= 0f)

        if (inCurrentHalf) {
            val pRadius = if (isBackHalf) 3.2f else 5.2f
            paint.shader = RadialGradient(
                px, py, pRadius * 2.2f,
                intArrayOf(Color.WHITE, Color.parseColor("#FFD54F"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, pRadius * 2.2f, paint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        orbitPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
