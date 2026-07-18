package com.iliacompanion.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), RelayListener {

    private lateinit var prefs: PrefsStore
    private lateinit var relayUrlInput: EditText
    private lateinit var syncCodeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var permissionCard: LinearLayout
    private lateinit var toggleOverlayButton: Button
    private lateinit var levelText: TextView
    private lateinit var xpBar: ProgressBar
    private lateinit var xpText: TextView
    private lateinit var streakText: TextView
    private lateinit var activityText: TextView
    private lateinit var previewSprite: FrameLayout
    private lateinit var previewBody: ImageView
    private lateinit var previewCosmetic: ImageView
    private lateinit var themeChips: LinearLayout
    private lateinit var cosmeticChips: LinearLayout
    private lateinit var sizeSlider: SeekBar
    private lateinit var sizeValue: TextView

    private var boundService: OverlayService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OverlayService.LocalBinder
            boundService = binder.getService()
            boundService?.setStatsListener(this@MainActivity)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets()
        prefs = PrefsStore(this)

        relayUrlInput = findViewById(R.id.relayUrlInput)
        syncCodeInput = findViewById(R.id.syncCodeInput)
        statusText = findViewById(R.id.statusText)
        permissionCard = findViewById(R.id.permissionCard)
        toggleOverlayButton = findViewById(R.id.toggleOverlayButton)
        levelText = findViewById(R.id.levelText)
        xpBar = findViewById(R.id.xpBar)
        xpText = findViewById(R.id.xpText)
        streakText = findViewById(R.id.streakText)
        activityText = findViewById(R.id.activityText)

        relayUrlInput.setText(prefs.relayUrl)
        syncCodeInput.setText(prefs.syncCode.uppercase())

        setupCustomizer()
        findViewById<Button>(R.id.savePairingButton).setOnClickListener { savePairing() }
        findViewById<Button>(R.id.grantPermissionButton).setOnClickListener { requestOverlayPermission() }
        toggleOverlayButton.setOnClickListener { toggleOverlay() }

        requestNotificationPermissionIfNeeded()
    }

    // targetSdk 35 enforces edge-to-edge: without this, content draws under
    // the status/nav bars (the title was clipped by the status bar). Pads the
    // scrollable root by the system-bar + cutout insets; clipToPadding=false
    // in the layout lets content still scroll edge-to-edge underneath.
    private fun applyEdgeToEdgeInsets() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        val root = findViewById<android.view.View>(R.id.rootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // Android 13+ requires runtime permission to actually show the
    // foreground-service notification. Not fatal if denied -- the overlay
    // still runs, the notification (and its "keep this alive" priority
    // boost) just stays invisible to the user.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCard()
        refreshToggleButtonLabel()
        if (OverlayService.isRunning && !isBound) bindToService()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            boundService?.setStatsListener(null)
            unbindService(connection)
            isBound = false
        }
    }

    // ---------- dress-up stage: companion, accessory, size ----------

    private fun setupCustomizer() {
        previewSprite = findViewById(R.id.previewSprite)
        previewBody = findViewById(R.id.previewBody)
        previewCosmetic = findViewById(R.id.previewCosmetic)
        themeChips = findViewById(R.id.themeChips)
        cosmeticChips = findViewById(R.id.cosmeticChips)
        sizeSlider = findViewById(R.id.sizeSlider)
        sizeValue = findViewById(R.id.sizeValue)

        for (theme in PetThemes.ALL) {
            themeChips.addView(makeChip(
                image = PetThemes.setFor(theme).idle,
                label = getString(PetThemes.labelFor(theme)),
                selected = theme == prefs.petTheme
            ) {
                prefs.petTheme = theme
                selectChip(themeChips, it)
                refreshPreview()
                boundService?.applyTheme(theme)
            })
        }

        for (cosmetic in Cosmetics.ALL) {
            cosmeticChips.addView(makeChip(
                image = cosmetic.drawable,
                label = getString(cosmetic.label),
                selected = cosmetic.id == prefs.petCosmetic
            ) {
                prefs.petCosmetic = cosmetic.id
                selectChip(cosmeticChips, it)
                refreshPreview()
                boundService?.applyCosmetic(cosmetic.id)
            })
        }

        sizeSlider.progress = prefs.petSizeDp
        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                refreshPreview()
                if (fromUser) boundService?.applySize(value)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                prefs.petSizeDp = sizeSlider.progress
            }
        })

        refreshPreview()
    }

    /** Square chip with the art on top and its name underneath. */
    private fun makeChip(image: Int, label: String, selected: Boolean, onClick: (View) -> Unit): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(10), dp(10), dp(10), dp(8))
            isClickable = true
            isFocusable = true
            isSelected = selected
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            minimumWidth = dp(72)
        }
        val art = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            if (image != 0) {
                setImageResource(image)
                (drawable as? android.graphics.drawable.Animatable)?.start()
            }
        }
        val name = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        chip.addView(art)
        chip.addView(name)
        chip.contentDescription = label
        chip.setOnClickListener { onClick(chip) }
        return chip
    }

    private fun selectChip(row: LinearLayout, selected: View) {
        for (i in 0 until row.childCount) {
            row.getChildAt(i).isSelected = row.getChildAt(i) === selected
        }
    }

    // What you see is your pet: the preview uses the exact size, art and
    // cosmetic anchor the overlay will use.
    private fun refreshPreview() {
        val density = resources.displayMetrics.density
        val sizeDp = sizeSlider.progress
        val sizePx = (sizeDp * density).toInt()
        previewSprite.layoutParams = previewSprite.layoutParams.apply {
            width = sizePx; height = sizePx
        }
        sizeValue.text = getString(
            when {
                sizeDp < 64 -> R.string.size_small
                sizeDp < 100 -> R.string.size_medium
                sizeDp < 124 -> R.string.size_large
                else -> R.string.size_huge
            }
        )

        val theme = prefs.petTheme
        previewBody.setImageResource(PetThemes.setFor(theme).idle)
        (previewBody.drawable as? android.graphics.drawable.Animatable)?.start()

        val cosmetic = Cosmetics.byId(prefs.petCosmetic)
        if (cosmetic.id == Cosmetics.NONE) {
            previewCosmetic.visibility = View.GONE
        } else {
            val anchor = PetThemes.hatAnchorFor(theme)
            previewCosmetic.setImageResource(cosmetic.drawable)
            previewCosmetic.translationX = (anchor.x - 0.5f) * sizePx
            previewCosmetic.translationY = (anchor.y - 0.5f) * sizePx
            previewCosmetic.scaleX = anchor.scale
            previewCosmetic.scaleY = anchor.scale
            previewCosmetic.visibility = View.VISIBLE
        }
    }

    private fun savePairing() {
        val url = relayUrlInput.text.toString().trim()
        val code = syncCodeInput.text.toString().trim().uppercase()
        prefs.relayUrl = url
        prefs.syncCode = code
        Toast.makeText(
            this,
            getString(if (OverlayService.isRunning) R.string.pairing_saved_restart else R.string.pairing_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setStatus(textRes: Int, colorRes: Int) {
        statusText.setText(textRes)
        statusText.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun refreshPermissionCard() {
        val granted = canDrawOverlays()
        permissionCard.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun refreshToggleButtonLabel() {
        toggleOverlayButton.text = getString(
            if (OverlayService.isRunning) R.string.action_stop_overlay else R.string.action_start_overlay
        )
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            stopOverlay()
            return
        }
        if (!canDrawOverlays()) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        if (!prefs.isConfigured()) {
            Toast.makeText(this, R.string.pairing_missing, Toast.LENGTH_LONG).show()
            return
        }
        startOverlay()
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        bindToService()
        // isRunning flips asynchronously in the service's onCreate, so
        // refreshToggleButtonLabel() would still read "not running" here --
        // set the label for the state we just requested instead.
        toggleOverlayButton.text = getString(R.string.action_stop_overlay)
        setStatus(R.string.status_connecting, R.color.text_secondary)
    }

    private fun stopOverlay() {
        if (isBound) {
            boundService?.setStatsListener(null)
            unbindService(connection)
            isBound = false
        }
        stopService(Intent(this, OverlayService::class.java))
        setStatus(R.string.status_disconnected, R.color.text_secondary)
        // Like startOverlay: isRunning flips asynchronously in the service's
        // onDestroy, so set the label for the state we just requested.
        toggleOverlayButton.text = getString(R.string.action_start_overlay)
    }

    private fun bindToService() {
        bindService(Intent(this, OverlayService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    // ---------- RelayListener: live stats while this Activity is visible ----------

    override fun onState(state: CompanionState) {
        runOnUiThread {
            levelText.text = "${getString(R.string.label_level)} ${state.level}" +
                (state.levelTitle?.let { " — $it" } ?: "")
            val pct = if (state.xpToNext > 0) ((state.xp / state.xpToNext) * 1000).toInt().coerceIn(0, 1000) else 0
            xpBar.progress = pct
            xpText.text = "${state.xp.toInt()} / ${state.xpToNext.toInt()} XP"
            streakText.text = "${getString(R.string.label_streak)}: ${state.streak} day(s)"
            activityText.text = "${getString(R.string.label_activity)}: ${state.title ?: getString(R.string.activity_idle)}"
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        runOnUiThread {
            if (connected) setStatus(R.string.status_connected, R.color.status_connected)
            else setStatus(R.string.status_connecting, R.color.text_secondary)
        }
    }

    override fun onPresence(info: PresenceInfo) {
        runOnUiThread {
            if (!info.desktopOnline) setStatus(R.string.status_desktop_offline, R.color.accent_dark)
        }
    }
}
