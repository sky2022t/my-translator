package com.mytranslator.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private val REQ_OVERLAY = 1001
    private val REQ_CAPTURE = 1002
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var permStatusBadge: TextView
    private lateinit var serviceStatusPill: TextView
    private lateinit var wakeBtnTitle: TextView
    private lateinit var wakeToggleBadge: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var orbDrawable: SolarOrbDrawable
    private var isOrbActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 沉浸式深空状态栏与导航栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#0B0F19")
            window.navigationBarColor = Color.parseColor("#0B0F19")
        }

        val rootScroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#0B0F19"),
                    Color.parseColor("#131B2E"),
                    Color.parseColor("#1E1638")
                )
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }

        // ==========================================
        // 顶部：3D 动态太阳星体徽章 + 标题 + 极光状态胶囊
        // ==========================================
        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        orbDrawable = SolarOrbDrawable()
        val orbHeaderView = ImageView(this).apply {
            setImageDrawable(orbDrawable)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(10)
            }
        }

        val titleTv = TextView(this).apply {
            text = "晴天小太阳翻译官"
            setTextColor(Color.parseColor("#FFF8E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(12f, 0f, 4f, Color.parseColor("#88FF8F00"))
        }
        titleRow.addView(orbHeaderView)
        titleRow.addView(titleTv)

        val subTitleTv = TextView(this).apply {
            text = "极速悬浮 · 智能OCR · 游戏无痕汉化"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }

        serviceStatusPill = TextView(this).apply {
            text = "● AI 悬浮交互 · 待唤醒"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#1A38BDF8"))
                setStroke(dp(1), Color.parseColor("#4D38BDF8"))
            }
        }

        headerBox.addView(titleRow)
        headerBox.addView(subTitleTv)
        headerBox.addView(serviceStatusPill)
        content.addView(headerBox)

        // ==========================================
        // 卡片 1：悬浮窗权限状态栏（毛玻璃质感）
        // ==========================================
        val permCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = glassCardBg("#1E293B", "#33FFFFFF", 20)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(22)
            }
            setOnClickListener { requestOverlayPermission() }
        }

        val permTitle = TextView(this).apply {
            text = "悬浮窗全局权限"
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        permStatusBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }

        permCard.addView(permTitle)
        permCard.addView(permStatusBadge)
        content.addView(permCard)

        // ==========================================
        // 卡片 2：3D 鎏金胶囊「唤醒小太阳悬浮球」主控开关
        // ==========================================
        val wakeBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#F59E0B"),
                    Color.parseColor("#FB923C"),
                    Color.parseColor("#FBBF24")
                )
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(2), Color.parseColor("#FFFDE7"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
            setOnClickListener {
                orbDrawable.triggerPulseBoost()
                launchFloatingOrb()
            }
        }

        wakeBtnTitle = TextView(this).apply {
            text = "☀️ 唤醒小太阳悬浮球"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 2f, Color.parseColor("#66000000"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        wakeToggleBadge = TextView(this).apply {
            text = "GO"
            setTextColor(Color.parseColor("#B45309"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.WHITE)
            }
        }

        wakeBtn.addView(wakeBtnTitle)
        wakeBtn.addView(wakeToggleBadge)
        content.addView(wakeBtn)

        // ==========================================
        // 核心功能矩阵（6大交互模块）
        // ==========================================
        val sectionTitle1 = createSectionHeader("核心功能矩阵")
        content.addView(sectionTitle1)

        val gridCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = glassCardBg("#172033", "#26FFFFFF", 22)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, dp(14), 0, 0)
        }

        // 功能 1：源语言/目标语言切换
        lateinit var langSubTv: TextView
        val item1 = createFeatureTile("🌐", "语种切换", "自动 → 中文", "#38BDF8") {
            showLanguageDialog { label -> langSubTv.text = label }
        }
        langSubTv = item1.tag as TextView

        // 功能 2：游戏专区/场景模式切换
        lateinit var sceneSubTv: TextView
        val item2 = createFeatureTile("🎮", "游戏汉化模式", "标准全屏", "#F43F5E") {
            TranslateEngine.sceneMode = (TranslateEngine.sceneMode + 1) % 3
            val labels = arrayOf("标准全屏", "游戏UI强降噪", "长文精排")
            sceneSubTv.text = labels[TranslateEngine.sceneMode]
            Toast.makeText(this, "已切换为：${labels[TranslateEngine.sceneMode]}", Toast.LENGTH_SHORT).show()
        }
        sceneSubTv = item2.tag as TextView

        // 功能 3：覆盖视觉主题切换
        lateinit var themeSubTv: TextView
        val item3 = createFeatureTile("🎨", "译文底色风格", "智能无痕", "#A855F7") {
            TranslateEngine.overlayTheme = (TranslateEngine.overlayTheme + 1) % 3
            val labels = arrayOf("智能无痕", "暗黑磨砂", "护眼暖光")
            themeSubTv.text = labels[TranslateEngine.overlayTheme]
            Toast.makeText(this, "译文底色已切换为：${labels[TranslateEngine.overlayTheme]}", Toast.LENGTH_SHORT).show()
        }
        themeSubTv = item3.tag as TextView

        // 功能 4：悬浮球动效与尺寸定制
        lateinit var orbSubTv: TextView
        val item4 = createFeatureTile("✨", "悬浮球定制", "尺寸 100%", "#F59E0B") {
            showOrbCustomizeDialog { desc -> orbSubTv.text = desc }
        }
        orbSubTv = item4.tag as TextView

        // 功能 5：剪贴板一键闪译
        val item5 = createFeatureTile("⚡", "剪贴板闪译", "一键粘贴即译", "#10B981") {
            translateFromClipboard()
        }

        // 功能 6：清空/管理历史
        val item6 = createFeatureTile("🧹", "清空历史", "释放缓存记录", "#64748B") {
            TranslateEngine.historyList.clear()
            refreshHistoryUI()
            Toast.makeText(this, "已清空翻译历史记录", Toast.LENGTH_SHORT).show()
        }

        row1.addView(item1); row1.addView(item2); row1.addView(item3)
        row2.addView(item4); row2.addView(item5); row2.addView(item6)
        gridCard.addView(row1)
        gridCard.addView(row2)
        content.addView(gridCard)

        // ==========================================
        // 模块 3：应用内极速文本翻译台
        // ==========================================
        content.addView(createSectionHeader("极速文本翻译台"))

        val quickBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glassCardBg("#172033", "#26FFFFFF", 20)
        }

        val inputEt = EditText(this).apply {
            hint = "在此输入或粘贴外文，体验毫秒级AI翻译..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minLines = 2
            maxLines = 4
            background = glassCardBg("#0F172A", "#1AFFFFFF", 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val resultTv = TextView(this).apply {
            text = "译文将显示在这里（点击译文可直接复制）"
            setTextColor(Color.parseColor("#FBBF24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(12), dp(8), dp(6))
            setOnClickListener {
                val txt = text.toString()
                if (txt.isNotEmpty() && !txt.startsWith("译文将显示")) {
                    copyToClipboard(txt)
                }
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }

        val clearBtn = TextView(this).apply {
            text = "清空"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                inputEt.setText("")
                resultTv.text = "译文将显示在这里（点击译文可直接复制）"
            }
        }

        val transNowBtn = TextView(this).apply {
            text = "立即翻译"
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                val raw = inputEt.text.toString().trim()
                if (raw.isEmpty()) {
                    Toast.makeText(this@MainActivity, "请先输入要翻译的内容", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                resultTv.text = "⏳ 正在星轨通道翻译中..."
                orbDrawable.triggerPulseBoost()
                mainScope.launch {
                    val res = TranslateEngine.googleTranslate(raw)
                    resultTv.text = res
                    refreshHistoryUI()
                }
            }
        }

        actionRow.addView(clearBtn)
        actionRow.addView(transNowBtn)
        quickBox.addView(inputEt)
        quickBox.addView(resultTv)
        quickBox.addView(actionRow)
        content.addView(quickBox)

        // ==========================================
        // 模块 4：实时翻译历史记录本（自动同步悬浮球与内置翻译）
        // ==========================================
        content.addView(createSectionHeader("最近翻译历史（点击条目复制）"))

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassCardBg("#172033", "#26FFFFFF", 20)
        }
        content.addView(historyContainer)

        rootScroll.addView(content)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusUI()
        refreshHistoryUI()
    }

    private fun updatePermissionStatusUI() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (granted) {
            permStatusBadge.text = "✓ 已就绪"
            permStatusBadge.setTextColor(Color.parseColor("#10B981"))
            permStatusBadge.background = glassCardBg("#1A10B981", "#4D10B981", 14)
        } else {
            permStatusBadge.text = "去开启 >"
            permStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
            permStatusBadge.background = glassCardBg("#1AF59E0B", "#4DF59E0B", 14)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQ_OVERLAY)
        } else {
            Toast.makeText(this, "悬浮窗权限已处于开启状态", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchFloatingOrb() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    private fun markOrbRunning() {
        isOrbActive = true
        serviceStatusPill.text = "● AI 星轨悬浮球 · 运行中"
        serviceStatusPill.setTextColor(Color.parseColor("#10B981"))
        wakeBtnTitle.text = "☀️ 悬浮球已激活（点击重刷）"
        wakeToggleBadge.text = "ON"
        Toast.makeText(this, "3D小太阳悬浮球已唤醒！", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            updatePermissionStatusUI()
        } else if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            val sIntent = Intent(this, FloatingTranslateService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(sIntent)
            } else {
                startService(sIntent)
            }
            markOrbRunning()
        }
    }

    private fun showLanguageDialog(onUpdate: (String) -> Unit) {
        val options = arrayOf(
            "自动识别 → 简体中文 (Auto → ZH)",
            "英语 → 简体中文 (EN → ZH)",
            "日语 → 简体中文 (JA → ZH)",
            "韩语 → 简体中文 (KO → ZH)",
            "中文 → 英语 (ZH → EN)"
        )
        val codes = arrayOf(
            Pair("auto", "zh-CN"),
            Pair("en", "zh-CN"),
            Pair("ja", "zh-CN"),
            Pair("ko", "zh-CN"),
            Pair("zh-CN", "en")
        )
        val shortLabels = arrayOf("自动 → 中文", "英语 → 中文", "日语 → 中文", "韩语 → 中文", "中文 → 英语")

        AlertDialog.Builder(this)
            .setTitle("选择翻译语种通道")
            .setItems(options) { _, which ->
                TranslateEngine.sourceLang = codes[which].first
                TranslateEngine.targetLang = codes[which].second
                onUpdate(shortLabels[which])
                Toast.makeText(this, "已切换：${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showOrbCustomizeDialog(onUpdate: (String) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(10))
        }
        val scaleLabel = TextView(this).apply {
            text = "悬浮球大小缩放：${(TranslateEngine.orbScale * 100).toInt()}%"
        }
        val scaleBar = SeekBar(this).apply {
            max = 70 // 70% ~ 140%
            progress = ((TranslateEngine.orbScale - 0.7f) * 100).toInt().coerceIn(0, 70)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val sc = 0.7f + p / 100f
                    TranslateEngine.orbScale = sc
                    scaleLabel.text = "悬浮球大小缩放：${(sc * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val alphaLabel = TextView(this).apply {
            text = "闲置贴边不透明度：${(TranslateEngine.orbIdleAlpha * 100).toInt()}%"
            setPadding(0, dp(14), 0, 0)
        }
        val alphaBar = SeekBar(this).apply {
            max = 65 // 35% ~ 100%
            progress = ((TranslateEngine.orbIdleAlpha - 0.35f) * 100).toInt().coerceIn(0, 65)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val al = 0.35f + p / 100f
                    TranslateEngine.orbIdleAlpha = al
                    alphaLabel.text = "闲置贴边不透明度：${(al * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        box.addView(scaleLabel); box.addView(scaleBar)
        box.addView(alphaLabel); box.addView(alphaBar)

        AlertDialog.Builder(this)
            .setTitle("悬浮球外观与动效调节")
            .setView(box)
            .setPositiveButton("确定") { _, _ ->
                onUpdate("尺寸 ${(TranslateEngine.orbScale * 100).toInt()}%")
            }
            .show()
    }

    private fun translateFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val txt = clip.getItemAt(0).coerceToText(this).toString().trim()
            if (txt.isNotEmpty()) {
                Toast.makeText(this, "正在翻译剪贴板内容...", Toast.LENGTH_SHORT).show()
                mainScope.launch {
                    val res = TranslateEngine.googleTranslate(txt)
                    refreshHistoryUI()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("⚡ 剪贴板闪译结果")
                        .setMessage("【原文】\n$txt\n\n【译文】\n$res")
                        .setPositiveButton("复制译文") { _, _ -> copyToClipboard(res) }
                        .setNegativeButton("关闭", null)
                        .show()
                }
                return
            }
        }
        Toast.makeText(this, "剪贴板为空，请先复制一段外文", Toast.LENGTH_SHORT).show()
    }

    private fun refreshHistoryUI() {
        historyContainer.removeAllViews()
        val list = TranslateEngine.historyList
        if (list.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "暂无历史记录。使用悬浮球截屏翻译或上方翻译台后，译文会自动归档于此。"
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }
            historyContainer.addView(emptyTv)
            return
        }

        for (item in list.take(12)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = glassCardBg("#0F172A", "#1AFFFFFF", 12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener { copyToClipboard(item.translated) }
            }
            val topLine = TextView(this).apply {
                text = "[${item.time}] ${item.original}"
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val botLine = TextView(this).apply {
                text = item.translated
                setTextColor(Color.parseColor("#F8FAFC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(3), 0, 0)
            }
            row.addView(topLine)
            row.addView(botLine)
            historyContainer.addView(row)
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
        Toast.makeText(this, "已复制译文到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(22), dp(4), dp(10))
        }
    }

    private fun createFeatureTile(icon: String, title: String, sub: String, accentHex: String, onClick: () -> Unit): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = glassCardBg("#1E293B", "#22FFFFFF", 16)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
            setOnClickListener { onClick() }
        }

        val iconBadge = TextView(this).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(accentHex.replace("#", "#26")))
                setStroke(dp(1), Color.parseColor(accentHex.replace("#", "#66")))
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }

        val subTv = TextView(this).apply {
            text = sub
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
        }

        tile.addView(iconBadge)
        tile.addView(titleTv)
        tile.addView(subTv)
        tile.tag = subTv
        return tile
    }

    private fun glassCardBg(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
