package com.mytranslator.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val REQ_OVERLAY = 101
    private val REQ_PROJ = 102
    private lateinit var mgr: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F7F9FC"))
            setPadding(50, 100, 50, 50); gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "☀️ 晴天小太阳翻译官"; textSize = 24f; setTextColor(Color.parseColor("#1E293B"))
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "极速悬浮 · 离线OCR · 游戏无痕汉化"; textSize = 14f; setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 70)
        }
        root.addView(subtitle)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 32f; setStroke(2, Color.parseColor("#E2E8F0")) }
        }
        val step1Btn = Button(this).apply {
            text = "① 开启悬浮窗权限"; setTextColor(Color.parseColor("#0F172A")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = GradientDrawable().apply { setColor(Color.parseColor("#F1F5F9")); cornerRadius = 24f }
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQ_OVERLAY)
                } else Toast.makeText(this@MainActivity, "悬浮窗权限已就绪！", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(step1Btn)
        card.addView(TextView(this).apply { height = 30 })

        val step2Btn = Button(this).apply {
            text = "☀️ 唤醒小太阳悬浮球 ☀️"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.parseColor("#FF9800")); cornerRadius = 24f }
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请先点上方开启悬浮窗权限哦！", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJ)
            }
        }
        card.addView(step2Btn)
        root.addView(card)
        setContentView(root)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_PROJ && res == Activity.RESULT_OK && data != null) {
            val svcIntent = Intent(this, FloatingTranslateService::class.java).apply {
                putExtra("code", res); putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            Toast.makeText(this, "小太阳已升起！进游戏即可使用 ☀️", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }
}
