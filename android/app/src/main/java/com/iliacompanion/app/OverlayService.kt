package com.iliacompanion.app

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the floating overlay pet alive and owns the
 * one WebSocket connection to the relay (see RelayClient / cloud/README.md).
 * Uses the same "draw over other apps" permission class as Messenger's chat
 * heads (TYPE_APPLICATION_OVERLAY) -- draggable, tap-to-react, and mirrors
 * whatever companion-watcher.js is doing on your paired desktop in real
 * time. Entirely opt-in: only runs once you start it from MainActivity
 * after granting the overlay permission and saving a pairing code.
 */
class OverlayService : Service(), RelayListener {

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    private val binder = LocalBinder()
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var relayClient: RelayClient? = null
    private var statsListener: RelayListener? = null
    private var petTheme: String = PetThemes.CLAWD
    private var currentArtRes: Int = 0
    private var breatheX: ObjectAnimator? = null
    private var breatheY: ObjectAnimator? = null
    private var lastConnected: Boolean = false
    private var breatheEnabled: Boolean = false

    override fun onBind(intent: Intent?): IBinder = binder

    fun setStatsListener(listener: RelayListener?) {
        statsListener = listener
        // Replay current state so a re-opened MainActivity shows live info
        // immediately instead of stale "Not connected" text.
        listener?.onConnectionChanged(lastConnected)
        relayClient?.lastState?.let { listener?.onState(it) }
    }

    fun currentState(): CompanionState? = relayClient?.lastState

    /** Called from MainActivity when the user picks a different pet theme. */
    fun applyTheme(theme: String) {
        petTheme = theme
        setPetArt(relayClient?.lastState?.activity)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (overlayView == null) addOverlayView()
        if (relayClient == null) startRelay()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        removeOverlayView()
        relayClient?.stop()
        relayClient = null
        super.onDestroy()
    }

    // ---------- relay ----------

    private fun startRelay() {
        val prefs = PrefsStore(this)
        if (!prefs.isConfigured()) return
        val client = RelayClient(prefs.relayUrl, prefs.syncCode)
        client.listener = this
        client.start()
        relayClient = client
    }

    override fun onState(state: CompanionState) {
        updatePetVisuals(state)
        statsListener?.onState(state)
    }

    override fun onPresence(info: PresenceInfo) {
        statsListener?.onPresence(info)
    }

    // Desktop event mirrored to the phone (level-up, streak milestone,
    // achievement, break reminder) -- raise a real system notification on
    // the alerts channel (default importance, unlike the silent ongoing
    // foreground-service one).
    override fun onNotify(event: String, title: String) {
        val label = when (event) {
            "CompanionLevelUp" -> getString(R.string.notify_level_up)
            "CompanionStreak" -> getString(R.string.notify_streak)
            "CompanionAchievement" -> getString(R.string.notify_achievement)
            "CompanionBreak" -> getString(R.string.notify_break)
            else -> getString(R.string.app_name)
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 1, openIntent, flags)
        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setContentTitle(label)
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(nextAlertId++, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied -- the alert is simply not shown
        }
        statsListener?.onNotify(event, title)
    }

    override fun onConnectionChanged(connected: Boolean) {
        lastConnected = connected
        statsListener?.onConnectionChanged(connected)
    }

    // ---------- overlay window ----------

    private fun addOverlayView() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        petTheme = PrefsStore(this).petTheme
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_pet, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 200

        attachDragAndTap(view, params, wm)
        wm.addView(view, params)
        overlayView = view
        setPetArt(null)
    }

    // Gentle breathe loop for raster themes (clawd's AVDs animate
    // themselves) -- one pair of reusable animators on the ImageView, so no
    // per-frame decodes or allocations while idling on screen all day.
    private fun startBreatheAnimation(body: ImageView) {
        body.post {
            if (!breatheEnabled || breatheX != null) return@post
            body.pivotX = body.width / 2f
            body.pivotY = body.height.toFloat()
            breatheX = ObjectAnimator.ofFloat(body, "scaleX", 1f, 1.02f).apply {
                duration = 1600
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            breatheY = ObjectAnimator.ofFloat(body, "scaleY", 1f, 0.98f).apply {
                duration = 1600
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }
    }

    private fun removeOverlayView() {
        breatheX?.cancel(); breatheX = null
        breatheY?.cancel(); breatheY = null
        val wm = windowManager ?: return
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) { /* already removed */ }
        }
        overlayView = null
        windowManager = null
    }

    private fun attachDragAndTap(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var downAtMs = 0L
        var dragged = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downAtMs = System.currentTimeMillis()
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (Math.abs(dx) > TAP_SLOP_PX || Math.abs(dy) > TAP_SLOP_PX) dragged = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try { wm.updateViewLayout(view, params) } catch (_: Exception) { /* view detached mid-drag */ }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downAtMs
                    if (!dragged && elapsed < TAP_MAX_MS) onPetTapped(v)
                    true
                }
                else -> false
            }
        }
    }

    private fun onPetTapped(view: View) {
        val body = view.findViewById<ImageView>(R.id.petBody)
        val bounce = ObjectAnimator.ofFloat(body, "scaleX", 1f, 0.85f, 1.1f, 1f)
        val bounceY = ObjectAnimator.ofFloat(body, "scaleY", 1f, 0.85f, 1.1f, 1f)
        bounce.duration = 260
        bounceY.duration = 260
        bounce.start()
        bounceY.start()
        relayClient?.sendReaction("poke")
    }

    private fun updatePetVisuals(state: CompanionState) {
        val view = overlayView ?: return
        setPetArt(state.activity)
        view.findViewById<TextView>(R.id.petLevelBadge).text = "Lv${state.level}"
    }

    // Themed per-activity art (same mapping as the desktop's
    // displayHintMap). Resource id is cached so repeated state pushes with
    // the same activity don't re-inflate the drawable. Clawd art is an
    // AnimatedVectorDrawable (runs on RenderThread, cheap for an always-on
    // overlay) and needs start(); raster themes keep the view-level breathe.
    private fun setPetArt(activity: String?) {
        val view = overlayView ?: return
        val res = PetThemes.drawableFor(petTheme, activity)
        if (res == currentArtRes) return
        currentArtRes = res
        val body = view.findViewById<ImageView>(R.id.petBody)
        body.setImageResource(res)
        (body.drawable as? android.graphics.drawable.Animatable)?.start()
        setBreatheEnabled(body, !PetThemes.hasBuiltInAnimation(petTheme))
    }

    private fun setBreatheEnabled(body: ImageView, enabled: Boolean) {
        breatheEnabled = enabled
        if (enabled) {
            if (breatheX == null) startBreatheAnimation(body)
        } else {
            breatheX?.cancel(); breatheX = null
            breatheY?.cancel(); breatheY = null
            body.scaleX = 1f
            body.scaleY = 1f
        }
    }

    // ---------- notification ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
            val alerts = NotificationChannel(
                ALERTS_CHANNEL_ID,
                getString(R.string.notif_alerts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(alerts)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ilia_companion_overlay"
        private const val ALERTS_CHANNEL_ID = "ilia_companion_alerts"
        private const val NOTIFICATION_ID = 1001
        private var nextAlertId = 2001
        private const val TAP_SLOP_PX = 16
        private const val TAP_MAX_MS = 250L

        @JvmStatic
        var isRunning: Boolean = false
            private set
    }
}
