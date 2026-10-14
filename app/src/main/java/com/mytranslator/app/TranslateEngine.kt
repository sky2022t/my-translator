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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object TranslateEngine {

    // --- 新增：全局可调配置与状态 ---
    var sourceLang: String = "auto"
    var targetLang: String = "zh-CN"
    // 0: 标准全屏汉化, 1: 游戏UI强力降噪, 2: 长文阅读精排
    var sceneMode: Int = 0
    // 0: 智能无痕取色, 1: 暗黑磨砂高对比, 2: 暖光护眼羊皮纸
    var overlayTheme: Int = 0
    // 悬浮球外观参数
    var orbScale: Float = 1.0f
    var orbIdleAlpha: Float = 0.88f
    @Volatile var isTranslatingState: Boolean = false
    var lastTranslateStartTime: Long = 0L

    data class HistoryItem(val original: String, val translated: String, val time: String)
    val historyList = CopyOnWriteArrayList<HistoryItem>()

    fun addHistory(orig: String, trans: String) {
        val cleanOrig = orig.trim()
        val cleanTrans = trans.trim()
        if (cleanOrig.isEmpty() || cleanTrans.isEmpty() || cleanOrig == cleanTrans) return
        if (historyList.isNotEmpty() && historyList[0].original == cleanOrig) return
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        historyList.add(0, HistoryItem(cleanOrig, cleanTrans, timeStr))
        while (historyList.size > 50) {
            historyList.removeAt(historyList.size - 1)
        }
    }

    // 1. 杂质过滤器：支持根据“游戏模式”动态强化噪点拦截
    fun isGarbageNoise(text: String, rect: Rect, screenH: Int): Boolean {
        val clean = text.trim()
        if (clean.length <= 1 && !clean.any { it.isLetterOrDigit() }) return true
        val minW = if (sceneMode == 1) 16 else 12
        val minH = if (sceneMode == 1) 12 else 10
        if (rect.width() < minW || rect.height() < minH) return true
        if (clean.matches(Regex("^[\\-_|/\\\\.,:;!?'\"\\s\\[\\]{}()<>v^0-9+%$#@*]+$"))) return true
        if (rect.bottom > screenH - 140 && (clean.contains(".goog") || clean.contains("http") || clean.contains(".com") || clean.contains("tidbits]"))) {
            return true
        }
        return false
    }

    // 2. 标点净化器
    fun cleanUiPunctuation(text: String, isShortUi: Boolean): String {
        var res = text.trim()
        if (isShortUi || sceneMode == 1) {
            res = res.removeSuffix("。").removeSuffix(".").removeSuffix("；").removeSuffix(";")
        }
        return res
    }

    // 3. 背景色提取（支持智能无痕取色 / 暗黑磨砂 / 暖光护眼三模式）
    fun extractSolidBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        if (overlayTheme == 1) return Color.rgb(22, 24, 34)
        if (overlayTheme == 2) return Color.rgb(246, 239, 220)

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
        if (overlayTheme == 1) return Color.parseColor("#F5F7FA")
        if (overlayTheme == 2) return Color.parseColor("#2C221E")
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.52) Color.parseColor("#111111") else Color.WHITE
    }

    data class TypographyResult(val textSize: Float, val isSingleLine: Boolean)

    // 5. 核心排版引擎
    fun calculateAdaptiveTypography(text: String, targetW: Int, targetH: Int, originalLineCount: Int): TypographyResult {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val availW = (targetW - 12).coerceAtLeast(14)
        val availH = (targetH - 8).coerceAtLeast(14)

        val baseLineHeight = if (originalLineCount > 1) {
            (targetH.toFloat() / originalLineCount).coerceAtLeast(24f)
        } else {
            targetH.toFloat()
        }

        val charCount = text.length
        val hasNoNewlines = !text.contains("\n")
        val forceSingleMax = if (sceneMode == 1) 8 else 6

        if (charCount <= forceSingleMax && hasNoNewlines) {
            val targetSizeByHeight = (baseLineHeight * 0.65f).coerceIn(13f, 26f)
            paint.textSize = targetSizeByHeight
            val textWidth = paint.measureText(text)
            val finalSize = if (textWidth > availW) {
                ((availW.toFloat() / textWidth) * targetSizeByHeight).coerceIn(10f, 26f)
            } else {
                targetSizeByHeight
            }
            return TypographyResult(finalSize, true)
        }

        if (charCount <= 10 && hasNoNewlines && sceneMode != 2) {
            val trySingleSize = (baseLineHeight * 0.58f).coerceIn(12f, 24f)
            paint.textSize = trySingleSize
            val w = paint.measureText(text)
            if (w <= availW) {
                return TypographyResult(trySingleSize, true)
            }
            val shrinkSize = (availW.toFloat() / w) * trySingleSize
            if (shrinkSize >= 13f) {
                return TypographyResult(shrinkSize, true)
            }
        }

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
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, availW, Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f, false)
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

    // 6. Google 翻译通道（接入动态源语言/目标语言，并自动写入翻译历史）
    suspend fun googleTranslate(text: String): String = withContext(Dispatchers.IO) {
        try {
            val clean = text.replace("\n", " ").trim()
            if (clean.isEmpty()) return@withContext ""
            isTranslatingState = true
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 4000
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(res).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until array.length()) { sb.append(array.getJSONArray(i).getString(0)) }
            val result = sb.toString()
            addHistory(clean, result)
            isTranslatingState = false
            result
        } catch (e: Exception) {
            isTranslatingState = false
            text
        }
    }
}
