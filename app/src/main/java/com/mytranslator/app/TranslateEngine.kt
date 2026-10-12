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

    // 1. 杂质过滤器：清除无意义分隔符、超细长虚线、标点以及屏幕底部的浏览器地址栏噪点
    fun isGarbageNoise(text: String, rect: Rect, screenH: Int): Boolean {
        val clean = text.trim()
        if (clean.length <= 1 && !clean.any { it.isLetterOrDigit() }) return true
        if (rect.width() < 12 || rect.height() < 10) return true
        if (clean.matches(Regex("^[\\-_|/\\\\.,:;!?'\"\\s\\[\\]{}()<>v^]+$"))) return true
        // 过滤底部浏览器导航栏/网址识别残留
        if (rect.bottom > screenH - 140 && (clean.contains(".goog") || clean.contains("http") || clean.contains(".com") || clean.contains("tidbits]"))) {
            return true
        }
        return false
    }

    // 2. 标点净化器：移除 UI、卡片、标题末尾生硬的句号与分号
    fun cleanUiPunctuation(text: String, isShortUi: Boolean): String {
        var res = text.trim()
        if (isShortUi) {
            res = res.removeSuffix("。").removeSuffix(".").removeSuffix("；").removeSuffix(";")
        }
        return res
    }

    // 3. 环带边缘采样提取纯净背景色
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

    data class TypographyResult(val textSize: Float, val isSingleLine: Boolean)

    // 5. 核心排版引擎：解决中文字数保整、英文多行基准归一、底部防裁切
    fun calculateAdaptiveTypography(text: String, targetW: Int, targetH: Int, originalLineCount: Int): TypographyResult {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val availW = (targetW - 12).coerceAtLeast(14)
        // 底部预留 8px 安全呼吸区，彻底防止文字底部笔画被切除
        val availH = (targetH - 8).coerceAtLeast(14)

        // 归一化基准单行高度：如果原英文是多行，按单行比例计算，防止卡片标题暴涨
        val baseLineHeight = if (originalLineCount > 1) {
            (targetH.toFloat() / originalLineCount).coerceAtLeast(24f)
        } else {
            targetH.toFloat()
        }

        val charCount = text.length
        val hasNoNewlines = !text.contains("\n")

        // 规则 1：中文字数 <= 6（卡片标题、按钮、菜单），绝对强制单行！彻底消灭“图像生 / 成”
        if (charCount <= 6 && hasNoNewlines) {
            // 优雅的卡片标题字号上限：26px（防止大盒子出现巨型字体）
            val targetSizeByHeight = (baseLineHeight * 0.65f).coerceIn(13f, 26f)
            paint.textSize = targetSizeByHeight
            val textWidth = paint.measureText(text)
            val finalSize = if (textWidth > availW) {
                // 宽度不够时平滑微缩，绝不折行
                ((availW / textWidth) * targetSizeByHeight).coerceIn(10f, 26f)
            } else {
                targetSizeByHeight
            }
            return TypographyResult(finalSize, true)
        }

        // 规则 2：字数在 7~10 之间，先尝试以可读字号放入单行
        if (charCount <= 10 && hasNoNewlines) {
            val trySingleSize = (baseLineHeight * 0.58f).coerceIn(12f, 24f)
            paint.textSize = trySingleSize
            val w = paint.measureText(text)
            if (w <= availW) {
                return TypographyResult(trySingleSize, true)
            }
            // 挤一挤放入单行
            val shrinkSize = (availW / w) * trySingleSize
            if (shrinkSize >= 13f) {
                return TypographyResult(shrinkSize, true)
            }
        }

        // 规则 3：长段落多行排版（使用均衡折行算法，避免孤字与腰斩）
        var low = 9f
        var high = (baseLineHeight * 0.70f).coerceIn(15f, 32f)
        var best = 11f

        while (high - low > 0.8f) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, availW)
                    .setLineSpacing(0f, 1.05f)
                    .setIncludePad(false)
                    .setBreakStrategy(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) LineBreaker.BREAK_STRATEGY_BALANCED else 2)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, availW, Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f, false)
            }
            // 严格检查高度和宽度，保留 descent 缓冲
            if (layout.height <= availH && layout.width <= availW) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return TypographyResult(best, false)
    }

    // 6. Google 翻译通道
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
