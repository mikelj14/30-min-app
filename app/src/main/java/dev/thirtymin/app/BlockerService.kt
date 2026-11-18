package dev.thirtymin.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Foreground service that covers the screen with a countdown overlay and
 * removes it when time is up. Fails open: any error removes the block.
 */
class BlockerService : Service() {
    private var overlay: View? = null
    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlay != null) return START_NOT_STICKY // already blocking

        startForeground(
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 1) ?: 1
        try {
            showOverlay(minutes * 60_000L)
        } catch (e: RuntimeException) {
            // Permission revoked or window rejected: fail open, never trap the user.
            Log.e(TAG, "overlay failed", e)
            stopSelf()
        }
        // Process killed = block ends. Do not restart (fail open).
        return START_NOT_STICKY
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
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                addView(countdown)
                addView(endButton)
                addView(confirm)
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

        timer =
            object : CountDownTimer(durationMs, 1_000) {
                override fun onTick(left: Long) {
                    val s = left / 1_000
                    countdown.text = "%d:%02d".format(s / 60, s % 60)
                }

                override fun onFinish() = stopSelf()
            }.start()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active block", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Phone blocked")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        timer?.cancel()
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"
        private const val ESCAPE_TAPS = 5
        private const val TAG = "BlockerService"
        private const val CHANNEL_ID = "block"
        private const val NOTIFICATION_ID = 1
    }
}
