package com.mytranslator.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslateEngine {
    fun extractBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val samples = mutableListOf<Int>()
        val w = bitmap.width; val h = bitmap.height
        val points = listOf(
            Pair(rect.left.coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair((rect.right - 1).coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair(rect.left.coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1)),
            Pair((rect.right - 1).coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1)),
            Pair(rect.centerX().coerceIn(0, w - 1), rect.top.coerceIn(0, h - 1)),
            Pair(rect.centerX().coerceIn(0, w - 1), (rect.bottom - 1).coerceIn(0, h - 1))
        )
        for ((x, y) in points) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) > 100) samples.add(c)
        }
        if (samples.isEmpty()) return Color.parseColor("#E6121212")
        return samples.groupBy { it }.maxByOrNull { it.value.size }?.key ?: samples[0]
    }

    fun getTextColor(bgColor: Int): Int {
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.5) Color.BLACK else Color.WHITE
    }

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
