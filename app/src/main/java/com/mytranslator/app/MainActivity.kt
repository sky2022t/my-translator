package com.mytranslator.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val REQ_OVERLAY = 101
    private val REQ_PROJ = 102
    private lateinit var mgr: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 100, 60, 60) }
        val tip = TextView(this).apply {
            text = "自制游戏悬浮翻译助手\n\n1. 授予悬浮窗权限\n2. 启动悬浮球并授权截屏\n3. 进入游戏点击『译』即可！"
            textSize = 18f; setPadding(0, 0, 0, 60)
        }
        layout.addView(tip)
        val btn1 = Button(this).apply {
            text = "第一步：授予悬浮窗权限"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQ_OVERLAY)
                } else Toast.makeText(this@MainActivity, "悬浮窗权限已开通！", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btn1)
        val btn2 = Button(this).apply {
            text = "第二步：启动悬浮翻译球"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请先开启悬浮窗权限！", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJ)
            }
        }
        layout.addView(btn2)
        setContentView(layout)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_PROJ && res == Activity.RESULT_OK && data != null) {
            FloatingTranslateService.mediaProjection = mgr.getMediaProjection(res, data)
            startService(Intent(this, FloatingTranslateService::class.java))
            Toast.makeText(this, "悬浮球已启动！返回游戏即可使用", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }
}
