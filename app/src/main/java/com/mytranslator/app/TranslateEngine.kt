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
    fun extractSolidBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val px = rect.left.coerceIn(0, bitmap.width - 1)
        val py = rect.top.coerceIn(0, bitmap.height - 1)
        val c = bitmap.getPixel(px, py)
        return Color.rgb(Color.red(c), Color.green(c), Color.blue(c))
    }

    fun getHighContrastTextColor(bgColor: Int): Int {
        val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        return if (lum > 0.55) Color.parseColor("#1A1A1A") else Color.WHITE
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
