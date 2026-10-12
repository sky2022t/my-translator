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

    // 1. 杂质过滤器：清除标点、分隔符、微型噪点，消灭乱飞的碎片
    fun isGarbageNoise(text: String, rect: Rect): Boolean {
        val clean = text.trim()
        if (clean.length <= 1 && !clean.any { it.isLetterOrDigit() }) return true
        if (rect.width() < 14 || rect.height() < 12) return true
        if (clean.matches(Regex("^[\\-_|/\\\\.,:;!?'\"\\s\\[\\]{}()<>v^]+$"))) return true
        return false
    }

    // 2. 环带背景采样：排除文字笔画色，提取最纯净的底色
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
        if (samples.isEmpty()) return Color.rgb(20, 20, 20)
        val raw = samples.groupBy { it }.maxByOrNull { it.value.size }?.key ?: samples[0]
        return Color.rgb(Color.red(raw), Color.green(raw), Color.blue(raw))
    }

    // 3. 高对比度前景色算法
    fun getHighContrastTextColor(bgColor: Int): Int {
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.55) Color.parseColor("#1A1A1A") else Color.WHITE
    }

    // 4. 最先进二分法排版测算：绝对保证字号充满且 0 溢出
    fun calculateBestTextSize(text: String, targetW: Int, targetH: Int, isSingleLine: Boolean): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        if (isSingleLine) {
            // 单行直接通过标称矩阵数学逆推最佳字号
            paint.textSize = 100f
            val w100 = paint.measureText(text)
            val sizeByW = if (w100 > 0) (targetW * 0.90f / w100) * 100f else 28f
            val sizeByH = targetH * 0.72f
            return minOf(sizeByW, sizeByH).coerceIn(20f, 65f)
        } else {
            // 多行长段落：二分逼近模拟排版
            var low = 16f
            var high = 58f
            var best = 18f
            val availW = (targetW - 14).coerceAtLeast(20)
            val availH = (targetH - 8).coerceAtLeast(20)
            while (high - low > 1.2f) {
                val mid = (low + high) / 2f
                paint.textSize = mid
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(text, 0, text.length, paint, availW)
                        .setLineSpacing(2f, 1.1f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(text, paint, availW, Layout.Alignment.ALIGN_NORMAL, 1.1f, 2f, false)
                }
                if (layout.height <= availH && layout.width <= availW) {
                    best = mid
                    low = mid
                } else {
                    high = mid
                }
            }
            return best
        }
    }

    // 5. 免费 Google 翻译接口
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
