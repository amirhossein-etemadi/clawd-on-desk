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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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

        setupThemePicker()
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

    private fun setupThemePicker() {
        val group = findViewById<android.widget.RadioGroup>(R.id.themeGroup)
        val preview = findViewById<android.widget.ImageView>(R.id.themePreview)
        val idByTheme = mapOf(
            PetThemes.CLAWD to R.id.themeClawd,
            PetThemes.BOSS_CAT to R.id.themeBossCat,
            PetThemes.CALICO to R.id.themeCalico,
            PetThemes.CLOUDLING to R.id.themeCloudling
        )
        fun showPreview(theme: String) {
            preview.setImageResource(PetThemes.drawableFor(theme, null))
            (preview.drawable as? android.graphics.drawable.Animatable)?.start()
        }
        group.check(idByTheme[prefs.petTheme] ?: R.id.themeClawd)
        showPreview(prefs.petTheme)
        group.setOnCheckedChangeListener { _, checkedId ->
            val theme = idByTheme.entries.firstOrNull { it.value == checkedId }?.key
                ?: PetThemes.CLAWD
            prefs.petTheme = theme
            showPreview(theme)
            boundService?.applyTheme(theme)
        }
    }

    private fun savePairing() {
        val url = relayUrlInput.text.toString().trim()
        val code = syncCodeInput.text.toString().trim().uppercase()
        prefs.relayUrl = url
        prefs.syncCode = code
        Toast.makeText(this, "Saved. ${if (OverlayService.isRunning) "Restart the floating pet to apply." else ""}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Save a relay URL and pairing code first.", Toast.LENGTH_LONG).show()
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
        statusText.setText(R.string.status_connecting)
    }

    private fun stopOverlay() {
        if (isBound) {
            boundService?.setStatsListener(null)
            unbindService(connection)
            isBound = false
        }
        stopService(Intent(this, OverlayService::class.java))
        statusText.setText(R.string.status_disconnected)
        refreshToggleButtonLabel()
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
            statusText.setText(if (connected) R.string.status_connected else R.string.status_connecting)
        }
    }

    override fun onPresence(info: PresenceInfo) {
        runOnUiThread {
            if (!info.desktopOnline) statusText.setText(R.string.status_desktop_offline)
        }
    }
}
