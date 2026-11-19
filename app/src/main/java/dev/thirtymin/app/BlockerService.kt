package dev.thirtymin.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Foreground service that covers the screen with a countdown overlay and
 * removes it when time is up. Fails open: any error removes the block.
 */
class BlockerService : Service() {
    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    // Phone pass state (overlay hidden while the user dials or talks).
    private var passSince = 0L // wall clock ms, for usage events
    private var passEndsAt = 0L // elapsedRealtime
    private var phoneApps = emptySet<String>()
    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // Set while armed: the window we are waiting in, and the screen/unlock listener.
    private var schedule: Schedule? = null
    private var wakeReceiver: BroadcastReceiver? = null

    // Window-end timeout. Listener alarm: lives only in this process, so no
    // PendingIntent and nothing left behind once the service is gone.
    private val windowEnd = AlarmManager.OnAlarmListener {
        Log.i(TAG, "window over, armed=${overlay == null}")
        if (overlay == null) stopSelf() // a running block finishes on its own timer
    }

    // "Display over other apps" revoked mid-session: the system hides the overlay
    // but keeps us alive, so end cleanly instead of lingering as an empty FGS.
    // Called on a binder thread; hop to main, where all service state lives.
    private val overlayPermissionWatcher = AppOpsManager.OnOpChangedListener { _, _ ->
        mainExecutor.execute {
            if (!Settings.canDrawOverlays(this)) {
                Log.i(TAG, "overlay permission revoked")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(AppOpsManager::class.java).startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            packageName,
            overlayPermissionWatcher
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlay != null) return START_NOT_STICKY // already blocking

        val arming = intent?.action == ACTION_ARM
        // Must run within seconds of startForegroundService, even if we stop right after.
        startForeground(
            NOTIFICATION_ID,
            notification(if (arming) "Waiting for you to wake up" else "Phone blocked"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        // No permission, no block: stop before anything marks today as spent.
        if (!Settings.canDrawOverlays(this)) {
            Log.i(TAG, "no overlay permission, not starting")
            return stop()
        }
        if (arming) {
            val s = intent.schedule() ?: return stop()
            schedule = s
            listenForWake()
            stopAtWindowEnd(s)
            evaluate() // already unlocked and in use -> block now
        } else {
            startBlocking(intent?.getIntExtra(EXTRA_MINUTES, 1) ?: 1)
        }
        // Process killed = block ends. Do not restart (fail open).
        return START_NOT_STICKY
    }

    private fun stop(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    /** Screen-on covers phones without a lock screen; USER_PRESENT fires after unlock. */
    private fun listenForWake() {
        if (wakeReceiver != null) return // second ARM (e.g. debug + daily): one listener is enough
        val r =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = evaluate(intent.action)
            }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // EXPORTED: USER_PRESENT comes from SystemUI (its own uid), which NOT_EXPORTED
        // silently drops. Safe: both actions are protected broadcasts only the system can
        // send, and evaluate() re-reads the real lock state anyway.
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
        wakeReceiver = r
    }

    /**
     * Explicit timeout so the service ends at window end even if no screen or
     * unlock broadcast ever arrives. Exact listener alarms need no PendingIntent;
     * we only get here after an exact alarm fired, so the permission is held.
     * ponytail: plain setExact is deferred while the phone is in Doze; then the
     * service ends at the next maintenance window or SCREEN_ON (evaluate -> STOP),
     * which nobody can see happen late because the screen is off.
     */
    private fun stopAtWindowEnd(s: Schedule) {
        val left = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(s.end))
        val am = getSystemService(AlarmManager::class.java)
        am.cancel(windowEnd) // a second ARM replaces the timeout
        try {
            am.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + left.toMillis().coerceAtLeast(0),
                "thirtymin:window-end",
                windowEnd,
                null
            )
        } catch (e: SecurityException) {
            // Permission revoked since the alarm fired: broadcasts still end us via evaluate().
            Log.w(TAG, "no window-end timeout", e)
        }
    }

    /**
     * Re-derive the phase from stored facts on every event, so a block that
     * already ran today (even in an earlier process) is never started twice,
     * and a window that has ended just stops the service.
     */
    private fun evaluate(event: String? = "arm") {
        if (overlay != null) return
        val s = schedule ?: return
        val store = SessionStore(this)
        val p =
            phase(
                LocalDateTime.now(),
                SystemClock.elapsedRealtime(),
                s,
                store.lastStart,
                store.activeUntil
            )
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val action = armAction(p, interactive, locked)
        Log.i(TAG, "event=$event phase=$p interactive=$interactive locked=$locked action=$action")
        when (action) {
            ArmAction.BLOCK -> startBlocking(s.minutes)
            ArmAction.WAIT -> Unit
            ArmAction.STOP -> stopSelf()
        }
    }

    private fun startBlocking(minutes: Int) {
        getSystemService(
            NotificationManager::class.java
        ).notify(NOTIFICATION_ID, notification("Phone blocked"))
        // Persist first, synchronously: once the overlay is up, today is already on
        // disk, so a crash or kill cannot lead to a second block today. If the
        // overlay then fails, today is spent with no block (fail open).
        SessionStore(this).startBlock(
            LocalDate.now(),
            SystemClock.elapsedRealtime() + minutes * 60_000L
        )
        try {
            showOverlay(minutes * 60_000L)
        } catch (e: RuntimeException) {
            // Permission revoked or window rejected: fail open, never trap the user.
            Log.e(TAG, "overlay failed", e)
            stopSelf()
        }
    }

    private fun showOverlay(durationMs: Long) {
        val countdown =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 64f
                gravity = Gravity.CENTER
            }
        // Emergency exit: tap ESCAPE_TAPS times -> "End block" -> confirm Yes/No.
        var taps = 0
        val confirm =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                addView(
                    TextView(context).apply {
                        text = "Are you sure you want to leave?"
                        setTextColor(Color.WHITE)
                        textSize = 20f
                    }
                )
                addView(
                    Button(context).apply {
                        text = "Yes"
                    }.also { it.setOnClickListener { stopSelf() } }
                )
                addView(
                    Button(context).apply { text = "No" }.also {
                        it.setOnClickListener {
                            visibility = View.GONE // hides the confirm box (outer apply receiver)
                            taps = 0
                        }
                    }
                )
            }
        val endButton =
            Button(this).apply {
                text = "End block"
                visibility = View.GONE
                setOnClickListener {
                    visibility = View.GONE
                    confirm.visibility = View.VISIBLE
                }
            }
        val center =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(countdown)
                addView(endButton)
                addView(confirm)
            }
        // Calls: small icon in the top-right corner, opened by a long press only,
        // so a stray tap cannot hide the block. Always reachable for emergencies.
        val dp = resources.displayMetrics.density
        val call =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                contentDescription = "Phone. Touch and hold to make a call."
                setPadding(
                    (12 * dp).toInt(),
                    (12 * dp).toInt(),
                    (12 * dp).toInt(),
                    (12 * dp).toInt()
                )
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_call)
                        setColorFilter(CALL_GREEN)
                    },
                    LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                )
                addView(
                    TextView(context).apply {
                        text = "Hold"
                        setTextColor(Color.GRAY)
                        textSize = 11f
                    }
                )
                setOnLongClickListener {
                    openDialer()
                    true
                }
            }
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(center, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(
                    call,
                    FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                        setMargins(0, (40 * dp).toInt(), (12 * dp).toInt(), 0)
                    }
                )
                setOnTouchListener { v, event ->
                    val asking = confirm.visibility == View.VISIBLE
                    if (event.action == MotionEvent.ACTION_DOWN && !asking &&
                        ++taps >= ESCAPE_TAPS
                    ) {
                        endButton.visibility = View.VISIBLE
                    }
                    v.performClick()
                    true // consume every touch
                }
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable + touchable (no NOT_* flags): swallows touches and back key.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )
        getSystemService(WindowManager::class.java).addView(root, params)
        overlay = root
        overlayParams = params

        timer =
            object : CountDownTimer(durationMs, 1_000) {
                override fun onTick(left: Long) {
                    val s = left / 1_000
                    countdown.text = "%d:%02d".format(s / 60, s % 60)
                }

                override fun onFinish() = stopSelf()
            }.start()
    }

    /**
     * Phone button: open the dialer without ending the block; the countdown keeps
     * running. Launch first, while our overlay is still visible: that visible
     * overlay is what exempts us from background activity start limits.
     */
    private fun openDialer() {
        val root = overlay ?: return
        // Before the launch: the dialer's "resumed" usage event must fall after this.
        passSince = System.currentTimeMillis()
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no dialer", e)
            return
        }
        getSystemService(WindowManager::class.java).removeView(root)
        Log.i(TAG, "phone pass started")
        passEndsAt = SystemClock.elapsedRealtime() + PHONE_PASS_MS
        val telecom = getSystemService(TelecomManager::class.java)
        phoneApps = setOfNotNull(telecom.defaultDialerPackage, telecom.systemDialerPackage)
        handler.postDelayed(passTick, PASS_POLL_MS)
    }

    // ponytail: unlimited passes; cap them if users abuse the call icon.
    private val passTick = Runnable { checkPhonePass() }

    /** Polled each second during a phone pass: bring the block back, or keep waiting. */
    private fun checkPhonePass() {
        val root = overlay ?: return
        val now = SystemClock.elapsedRealtime()
        // Calls (and VoIP) set the audio mode; reading it needs no permission.
        val inCall = getSystemService(AudioManager::class.java).mode in CALL_MODES
        if (inCall) passEndsAt = maxOf(passEndsAt, now + CALL_GRACE_MS)
        val front = foregroundApp()
        if (!passReturns(inCall, front, phoneApps, now, passEndsAt)) {
            handler.postDelayed(passTick, PASS_POLL_MS)
            return
        }
        try {
            getSystemService(WindowManager::class.java).addView(root, overlayParams)
            Log.i(TAG, "phone pass over, front=$front")
        } catch (e: RuntimeException) {
            Log.e(TAG, "overlay failed", e) // fail open
            stopSelf()
        }
    }

    /**
     * Package of the activity most recently brought to the front since the pass
     * began, or null if none yet. Without Usage Access the system returns no
     * events, so this stays null and the pass falls back to its timer.
     */
    private fun foregroundApp(): String? {
        val events =
            getSystemService(UsageStatsManager::class.java)
                .queryEvents(passSince, System.currentTimeMillis())
        val e = UsageEvents.Event()
        var front: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) front = e.packageName
        }
        return front
    }

    private fun notification(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active block", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        getSystemService(AppOpsManager::class.java).stopWatchingMode(overlayPermissionWatcher)
        timer?.cancel()
        wakeReceiver?.let(::unregisterReceiver)
        wakeReceiver = null
        getSystemService(AlarmManager::class.java).cancel(windowEnd)
        handler.removeCallbacksAndMessages(null)
        // Not attached during a phone pass; removeView would throw.
        overlay?.takeIf { it.isAttachedToWindow }?.let {
            getSystemService(WindowManager::class.java).removeView(it)
        }
        overlay = null
        SessionStore(this).endBlock()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"
        const val ACTION_ARM = "dev.thirtymin.app.ARM"
        private const val ESCAPE_TAPS = 5

        // Time to dial before the overlay returns; a live call keeps extending it.
        private const val PHONE_PASS_MS = 30_000L
        private val CALL_GREEN = Color.rgb(0x8D, 0xB5, 0x8A)
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        private const val PASS_POLL_MS = 1_000L

        // After a call ends the block returns within this, even inside the dialer.
        private const val CALL_GRACE_MS = 5_000L
        private val CALL_MODES =
            setOf(
                AudioManager.MODE_RINGTONE,
                AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION
            )
        private const val TAG = "BlockerService"
        private const val CHANNEL_ID = "block"
        private const val NOTIFICATION_ID = 1
    }
}
