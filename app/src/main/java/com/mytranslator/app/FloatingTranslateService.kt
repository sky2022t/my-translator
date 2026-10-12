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
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*

class FloatingTranslateService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var sunBtn: SunFloatingView
    private var overlay: FrameLayout? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    private var proj: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var sw = 0; private var sh = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        sw = dm.widthPixels; sh = dm.heightPixels
        startForegroundNotification()
        initSunButton()
    }

    private fun startForegroundNotification() {
        val cid = "sun_trans"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(cid, "小太阳翻译官", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        val notif = NotificationCompat.Builder(this, cid)
            .setContentTitle("小太阳翻译官正在值班 ☀️")
            .setContentText("点击屏幕上的小太阳即可一键翻译")
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
        reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 3)
        vd = p.createVirtualDisplay("SunCap", sw, sh, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null)
    }

    private fun initSunButton() {
        sunBtn = SunFloatingView(this)
        val p = WindowManager.LayoutParams(
            150, 150, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 600 }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        sunBtn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    p.x = sx + dx; p.y = sy + dy; wm.updateViewLayout(sunBtn, p); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) capture(); true }
                else -> false
            }
        }
        wm.addView(sunBtn, p)
    }

    private fun capture() {
        val r = reader ?: run { Toast.makeText(this, "服务未准备好，请重新授权", Toast.LENGTH_SHORT).show(); return }
        clear()
        sunBtn.isTranslating = true
        scope.launch {
            delay(150)
            val img = r.acquireLatestImage()
            if (img == null) { sunBtn.isTranslating = false; return@launch }
            val bmp = imgToBmp(img); img.close()
            recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { res ->
                scope.launch { render(bmp, res.textBlocks); sunBtn.isTranslating = false }
            }.addOnFailureListener {
                sunBtn.isTranslating = false
                Toast.makeText(this@FloatingTranslateService, "OCR 识别失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun render(bmp: Bitmap, blocks: List<com.google.mlkit.vision.text.Text.TextBlock>) {
        if (blocks.isEmpty()) { Toast.makeText(this, "未发现可翻译的文字", Toast.LENGTH_SHORT).show(); return }
        val c = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT); setOnClickListener { clear() } }
        overlay = c

        for (b in blocks) {
            val rawRect = b.boundingBox ?: continue
            // 过滤无用杂质及底部地址栏
            if (TranslateEngine.isGarbageNoise(b.text, rawRect, bmp.height)) continue

            var txt = TranslateEngine.googleTranslate(b.text)
            if (txt.isEmpty()) continue

            val isShortUi = txt.length <= 15 && !txt.contains("\n")
            txt = TranslateEngine.cleanUiPunctuation(txt, isShortUi)

            // 1. 结构外扩：水平扩展 4~8px，顶部扩展 2~5px，底部额外增加 +4px 留出文字下潜安全区！
            val expX = (rawRect.height() * 0.12f).toInt().coerceIn(4, 8)
            val expY = (rawRect.height() * 0.08f).toInt().coerceIn(2, 5)
            val renderLeft = (rawRect.left - expX).coerceAtLeast(0)
            val renderTop = (rawRect.top - expY).coerceAtLeast(0)
            val renderRight = (rawRect.right + expX).coerceAtMost(bmp.width)
            // 底部多扩展 4px，彻底解决像“成”、“题”这类底部笔画被切掉的 BUG
            val renderBottom = (rawRect.bottom + expY + 4).coerceAtMost(bmp.height)
            val finalWidth = renderRight - renderLeft
            val finalHeight = renderBottom - renderTop
            if (finalWidth <= 10 || finalHeight <= 8) continue

            val bg = TranslateEngine.extractSolidBackgroundColor(bmp, rawRect)
            val tc = TranslateEngine.getHighContrastTextColor(bg)

            // 2. 传入原文本行数，计算规整自适应字号
            val originalLines = b.lines.size
            val typo = TranslateEngine.calculateAdaptiveTypography(txt, finalWidth, finalHeight, originalLines)

            // 3. 智能居中与对齐判定
            val isButtonOrCard = (finalWidth.toFloat() / finalHeight < 3.8f) || txt.length <= 8
            val textGravity = if (isButtonOrCard) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)

            val tv = AppCompatTextView(this).apply {
                text = txt
                setTextColor(tc)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, typo.textSize)
                includeFontPadding = false
                paintFlags = paintFlags or Paint.SUBPIXEL_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
                gravity = textGravity
                // 内衬：单行标题不设垂直 padding，让文字在中心自然伸展；多行段落设轻微内衬
                if (typo.isSingleLine) {
                    maxLines = 1
                    setPadding(4, 0, 4, 0)
                } else {
                    setLineSpacing(0f, 1.05f)
                    setPadding(6, 2, 6, 2)
                }
                background = GradientDrawable().apply {
                    cornerRadius = (finalHeight * 0.14f).coerceIn(3f, 8f)
                    setColor(bg)
                }
            }

            c.addView(tv, FrameLayout.LayoutParams(finalWidth, finalHeight).apply {
                leftMargin = renderLeft
                topMargin = renderTop
            })
        }

        val op = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(c, op)
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
        clear(); try { wm.removeView(sunBtn) } catch (e: Exception) {}
        vd?.release(); reader?.close(); scope.cancel()
    }
}
