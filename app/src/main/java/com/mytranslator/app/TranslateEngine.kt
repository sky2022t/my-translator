package com.mytranslator.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

fun AppCompatTextView.setSafeAutoSize(maxHeight: Int) {
    val safeMax = if (maxHeight <= 5) 6 else maxHeight
    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 5, safeMax, 2, TypedValue.COMPLEX_UNIT_PX)
}

object TranslateEngine {
    fun extractBackgroundColor(bitmap: Bitmap, textRect: Rect, padding: Int = 8): Int {
        val oL = (textRect.left - padding).coerceAtLeast(0)
        val oT = (textRect.top - padding).coerceAtLeast(0)
        val oR = (textRect.right + padding).coerceAtMost(bitmap.width)
        val oB = (textRect.bottom + padding).coerceAtMost(bitmap.height)
        val w = oR - oL
        val h = oB - oT
        if (w <= 0 || h <= 0) return Color.parseColor("#E6202020")
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, oL, oT, w, h)
        val rL = textRect.left - oL; val rT = textRect.top - oT
        val rR = textRect.right - oL; val rB = textRect.bottom - oT
        val hist = HashMap<Int, Int>(64)
        for (y in 0 until h) {
            val offset = y * w
            val inY = y in rT until rB
            for (x in 0 until w) {
                if (!inY || x < rL || x >= rR) {
                    val color = pixels[offset + x]
                    if (Color.alpha(color) > 128) hist[color] = (hist[color] ?: 0) + 1
                }
            }
        }
        return hist.maxByOrNull { it.value }?.key ?: Color.parseColor("#E6202020")
    }

    fun getTextColor(bgColor: Int): Int {
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.5) Color.BLACK else Color.WHITE
    }

    suspend fun googleTranslate(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
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
