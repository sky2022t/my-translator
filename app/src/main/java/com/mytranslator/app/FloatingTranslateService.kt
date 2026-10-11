package com.mytranslator.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*

class FloatingTranslateService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var btn: AppCompatButton
    private var overlay: FrameLayout? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var proj: MediaProjection? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        initBall()
    }

    private fun startForegroundNotification() {
        val channelId = "translate_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "悬浮翻译服务", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮翻译正在运行")
            .setContentText("点击屏幕上的悬浮球进行翻译")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1001, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        if (code == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            proj = mgr.getMediaProjection(code, data)
        }
        return START_NOT_STICKY
    }

    private fun initBall() {
        btn = AppCompatButton(this).apply {
            text = "译"; textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1E88E5"))
        }
        val p = WindowManager.LayoutParams(
            140, 140, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 50; y = 300 }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    p.x = sx + dx; p.y = sy + dy; wm.updateViewLayout(btn, p); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) capture(); true }
                else -> false
            }
        }
        wm.addView(btn, p)
    }

    private fun capture() {
        val p = proj ?: run {
            Toast.makeText(this, "截屏权限未就绪，请在App内重新授权", Toast.LENGTH_SHORT).show(); return
        }
        clear()
        btn.text = "..."
        try {
            val m = resources.displayMetrics
            val ir = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
            val vd = p.createVirtualDisplay("Cap", m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.surface, null, null)

            scope.launch {
                delay(300)
                val img = ir.acquireLatestImage()
                if (img == null) { btn.text = "译"; vd.release(); return@launch }
                val bmp = imgToBmp(img); img.close(); vd.release()

                recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { res ->
                    scope.launch { render(bmp, res.textBlocks); btn.text = "译" }
                }.addOnFailureListener {
                    btn.text = "译"
                    Toast.makeText(this@FloatingTranslateService, "识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            btn.text = "译"
            Toast.makeText(this, "截屏异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun render(bmp: Bitmap, blocks: List<com.google.mlkit.vision.text.Text.TextBlock>) {
        if (blocks.isEmpty()) { Toast.makeText(this, "未检测到外语文字", Toast.LENGTH_SHORT).show(); return }
        val c = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT); setOnClickListener { clear() } }
        overlay = c
        for (b in blocks) {
            for (line in b.lines) {
                val r = line.boundingBox ?: continue
                if (r.width() <= 0 || r.height() <= 0) continue
                val txt = TranslateEngine.googleTranslate(line.text)
                val bg = TranslateEngine.extractBackgroundColor(bmp, r)
                val tc = TranslateEngine.getTextColor(bg)
                val tv = AppCompatTextView(this).apply {
                    text = txt; setTextColor(tc); setBackgroundColor(bg)
                    gravity = Gravity.CENTER; setSafeAutoSize(r.height())
                }
                c.addView(tv, FrameLayout.LayoutParams(r.width(), r.height()).apply { leftMargin = r.left; topMargin = r.top })
            }
        }
        wm.addView(c, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ))
    }

    private fun clear() { overlay?.let { try { wm.removeView(it) } catch (e: Exception) {}; overlay = null } }

    private fun imgToBmp(img: android.media.Image): Bitmap {
        val plane = img.planes[0]
        val bmp = Bitmap.createBitmap(img.width + (plane.rowStride - plane.pixelStride * img.width) / plane.pixelStride, img.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
    }

    override fun onDestroy() {
        super.onDestroy()
        clear(); try { wm.removeView(btn) } catch (e: Exception) {}; scope.cancel()
    }
}
