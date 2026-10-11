package com.mytranslator.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "悬浮翻译核心引擎构建成功！"
            textSize = 20f
        }
        setContentView(tv)
    }
}
