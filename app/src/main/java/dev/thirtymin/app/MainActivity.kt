package dev.thirtymin.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    // Re-read on resume: user may come back from the settings page.
    private var canOverlay by mutableStateOf(false)
    private var canAlarm by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterVertically
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (canOverlay) {
                            Button(onClick = ::startBlock) {
                                Text("Block for $PROTOTYPE_MINUTES min")
                            }
                        } else {
                            Text("30-Min needs permission to draw over other apps.")
                            Button(onClick = ::openOverlaySettings) { Text("Grant permission") }
                        }
                        if (!canAlarm) {
                            Text("Allow alarms so the block can start each morning.")
                            Button(onClick = ::openAlarmSettings) { Text("Allow alarms") }
                        } else if (BuildConfig.DEBUG) {
                            // Stripped from release: BuildConfig.DEBUG is a compile-time false there.
                            Button(onClick = ::scheduleTestAlarm) {
                                Text("Test: window starts in 1 min")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canOverlay = Settings.canDrawOverlays(this)
        // Alarms vanish on force-stop and permission revoke: re-set on every open.
        canAlarm = Alarms.scheduleDaily(this)
    }

    private fun openAlarmSettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
        )
    }

    private fun scheduleTestAlarm() {
        val ok = Alarms.scheduleDebug(this)
        Toast.makeText(
            this,
            if (ok) "Test alarm in 1 min. Lock the phone." else "Alarm denied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun startBlock() {
        startForegroundService(
            Intent(
                this,
                BlockerService::class.java
            ).putExtra(BlockerService.EXTRA_MINUTES, PROTOTYPE_MINUTES)
        )
    }

    private companion object {
        // ponytail: fixed duration for device tests; settings UI is Phase 5.
        const val PROTOTYPE_MINUTES = 3
    }
}
