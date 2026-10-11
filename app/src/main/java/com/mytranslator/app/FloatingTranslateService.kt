package com.mytranslator.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.TypedValue
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
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenW = 0; private var screenH = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels; screenH = dm.heightPixels
        startForegroundNotification()
        initBall()
    }

    private fun startForegroundNotification() {
        val cid = "trans_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(cid, "翻译服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        val notif = NotificationCompat.Builder(this, cid)
            .setContentTitle("悬浮翻译已就绪")
            .setContentText("点击屏幕上的『译』悬浮球即可翻译")
            .setSmallIcon(android.R.drawable.ic_menu_search)
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
        if (code == Activity.RESULT_OK && data != null && proj == null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            proj = mgr.getMediaProjection(code, data)
            initCapture()
        }
        return START_NOT_STICKY
    }

    private fun initCapture() {
        val p = proj ?: return
        val dm = resources.displayMetrics
        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 3)
        virtualDisplay = p.createVirtualDisplay("PersistentCap", screenW, screenH, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null)
    }

    private fun initBall() {
        btn = AppCompatButton(this).apply {
            text = "译"; textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E6007AFF")) }
        }
        val p = WindowManager.LayoutParams(
            130, 130, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 500 }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                    p.x = sx + dx; p.y = sy + dy; wm.updateViewLayout(btn, p); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) capture(); true }
                else -> false
            }
        }
        wm.addView(btn, p)
    }

    private fun capture() {
        val reader = imageReader ?: run {
            Toast.makeText(this, "服务未准备好，请重新授权", Toast.LENGTH_SHORT).show(); return
        }
        clear()
        btn.text = "..."
        scope.launch {
            delay(150)
            val img = reader.acquireLatestImage()
            if (img == null) { btn.text = "译"; return@launch }
            val bmp = imgToBmp(img); img.close()
            recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { res ->
                scope.launch { render(bmp, res.textBlocks); btn.text = "译" }
            }.addOnFailureListener { btn.text = "译" }
        }
    }

    private suspend fun render(bmp: Bitmap, blocks: List<com.google.mlkit.vision.text.Text.TextBlock>) {
        if (blocks.isEmpty()) { Toast.makeText(this, "未检测到文字", Toast.LENGTH_SHORT).show(); return }
        val c = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT); setOnClickListener { clear() } }
        overlay = c
        for (b in blocks) {
            val r = b.boundingBox ?: continue
            if (r.width() <= 20 || r.height() <= 20) continue
            val txt = TranslateEngine.googleTranslate(b.text)
            if (txt.isEmpty()) continue
            val bg = TranslateEngine.extractBackgroundColor(bmp, r)
            val tc = TranslateEngine.getTextColor(bg)
            val tv = AppCompatTextView(this).apply {
                text = txt; setTextColor(tc); setPadding(10, 6, 10, 6); gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                background = GradientDrawable().apply { cornerRadius = 8f; setColor(bg) }
            }
            c.addView(tv, FrameLayout.LayoutParams(r.width(), r.height()).apply { leftMargin = r.left; topMargin = r.top })
        }
        wm.addView(c, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
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
        clear(); try { wm.removeView(btn) } catch (e: Exception) {}
        virtualDisplay?.release(); imageReader?.close(); scope.cancel()
    }
}
