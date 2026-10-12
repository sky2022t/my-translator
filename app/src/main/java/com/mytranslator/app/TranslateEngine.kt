package com.mytranslator.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslateEngine {

    // 1. 杂质过滤器：清除无意义分隔符、超细长虚线与标点
    fun isGarbageNoise(text: String, rect: Rect): Boolean {
        val clean = text.trim()
        if (clean.length <= 1 && !clean.any { it.isLetterOrDigit() }) return true
        if (rect.width() < 12 || rect.height() < 10) return true
        if (clean.matches(Regex("^[\\-_|/\\\\.,:;!?'\"\\s\\[\\]{}()<>v^]+$"))) return true
        return false
    }

    // 2. 标点净化器：移除标题、按钮、UI组件末尾生硬的句号（如“热带花絮。”->“热带花絮”）
    fun cleanUiPunctuation(text: String, isShortUi: Boolean): String {
        var res = text.trim()
        if (isShortUi) {
            res = res.removeSuffix("。").removeSuffix(".").removeSuffix("；").removeSuffix(";")
        }
        return res
    }

    // 3. 边缘环带采样提取纯净背景色
    fun extractSolidBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val w = bitmap.width; val h = bitmap.height
        val samples = mutableListOf<Int>()
        val pts = listOf(
            Pair(rect.left.coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair((rect.right - 1).coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair(rect.left.coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1)),
            Pair((rect.right - 1).coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1)),
            Pair(rect.centerX().coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair(rect.centerX().coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1))
        )
        for ((x, y) in pts) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) > 100) samples.add(c)
        }
        if (samples.isEmpty()) return Color.rgb(24, 24, 24)
        val raw = samples.groupBy { it }.maxByOrNull { it.value.size }?.key ?: samples[0]
        return Color.rgb(Color.red(raw), Color.green(raw), Color.blue(raw))
    }

    // 4. 高对比度前景色算法
    fun getHighContrastTextColor(bgColor: Int): Int {
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.52) Color.parseColor("#111111") else Color.WHITE
    }

    // 5. 进阶排版测算：黄金高度比例测算 + 图表细微自适应
    data class TypographyResult(val textSize: Float, val isSingleLine: Boolean)

    fun calculateAdaptiveTypography(text: String, targetW: Int, targetH: Int): TypographyResult {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val availW = (targetW - 10).coerceAtLeast(14)
        val availH = (targetH - 6).coerceAtLeast(14)

        // 黄金比例高度基准：单行文字的最佳视觉字号约为容器高度的 58% ~ 65%
        val optimalHeightSize = targetH * 0.62f
        paint.textSize = optimalHeightSize
        val singleLineWidth = paint.measureText(text)

        // 判断是否能优雅地放入单行
        if (singleLineWidth <= availW) {
            // 单行能够放下：锁定在最佳高度比，绝不无限制膨胀（解决真彩 vs 水蒸气字号不均问题）
            val boundedSize = optimalHeightSize.coerceIn(9f, 60f)
            return TypographyResult(boundedSize, true)
        }

        // 单行放不下：测试按宽度压小单行，看是否仍然足够清晰
        val shrinkSingleSize = (availW / singleLineWidth) * optimalHeightSize
        // 若容器很扁长（长宽比大）且单行压小后仍有一定可读性，优先保留单行
        val isWide = (targetW.toFloat() / targetH) > 2.8f
        if (isWide && shrinkSingleSize >= (targetH * 0.42f).coerceAtLeast(10f)) {
            return TypographyResult(shrinkSingleSize.coerceIn(9f, 55f), true)
        }

        // 否则进入多行自适应：二分逼近查找排版最佳字号
        var low = 8f  // 允许下潜到 8px，图表/极小标注永不溢出
        var high = (targetH * 0.65f).coerceAtLeast(16f)
        var best = 10f

        while (high - low > 1.0f) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, availW)
                    .setLineSpacing(1f, 1.05f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, availW, Layout.Alignment.ALIGN_NORMAL, 1.05f, 1f, false)
            }
            if (layout.height <= availH && layout.width <= availW) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return TypographyResult(best, false)
    }

    // 6. 极速 Google 翻译
    suspend fun googleTranslate(text: String): String = withContext(Dispatchers.IO) {
        try {
            val clean = text.replace("\n", " ").trim()
            if (clean.isEmpty()) return@withContext ""
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 4000
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(res).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until array.length()) { sb.append(array.getJSONArray(i).getString(0)) }
            sb.toString()
        } catch (e: Exception) { text }
    }
}
