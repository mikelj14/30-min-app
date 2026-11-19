package dev.thirtymin.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    // Re-read on resume: user may come back from the settings page.
    private var canOverlay by mutableStateOf(false)
    private var canAlarm by mutableStateOf(false)
    private var schedule by mutableStateOf(SessionStore.DEFAULT_SCHEDULE)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            ThirtyMinTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { NavBar(tab) { tab = it } }
                ) { insets ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(insets)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (tab) {
                            Tab.TODAY -> Today()

                            Tab.HISTORY -> Placeholder(
                                "History",
                                "Your completed mornings will show here."
                            )

                            Tab.SETTINGS -> SettingsScreen(schedule, ::saveSchedule)
                        }
                    }
                }
            }
        }
    }

    /** ponytail: prototype controls until the Phase 5.6 home dashboard (design 1a). */
    @Composable
    private fun Today() {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("30-Min", style = MaterialTheme.typography.titleMedium)
            if (canOverlay) {
                Button(onClick = ::startBlock) { Text("Block for $PROTOTYPE_MINUTES min") }
            } else {
                Text("30-Min needs permission to draw over other apps.")
                Button(onClick = ::openOverlaySettings) { Text("Grant permission") }
            }
            if (!canAlarm) {
                Text("Allow alarms so the block can start each morning.")
                Button(onClick = ::openAlarmSettings) { Text("Allow alarms") }
            } else if (BuildConfig.DEBUG) {
                // Stripped from release: BuildConfig.DEBUG is a compile-time false there.
                Button(onClick = ::scheduleTestAlarm) { Text("Test: window starts in 1 min") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canOverlay = Settings.canDrawOverlays(this)
        schedule = SessionStore(this).schedule
        // Alarms vanish on force-stop and permission revoke: re-set on every open.
        canAlarm = Alarms.scheduleDaily(this)
    }

    /** Null when saved, else the message the settings screen shows. */
    private fun saveSchedule(start: LocalTime, end: LocalTime, minutes: Int): String? = try {
        val s = userSchedule(start, end, minutes)
        if (Alarms.updateSchedule(this, s)) {
            schedule = s
            null
        } else {
            "Settings are locked during a block."
        }
    } catch (e: IllegalArgumentException) {
        e.message.orEmpty().replaceFirstChar(Char::uppercase) + "."
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
        // ponytail: fixed duration for device tests; real blocks use the saved schedule.
        const val PROTOTYPE_MINUTES = 3
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_wb_twilight),
    HISTORY("History", R.drawable.ic_calendar_month),
    SETTINGS("Settings", R.drawable.ic_tune)
}

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = MaterialTheme.colorScheme
    NavigationBar(containerColor = c.surfaceContainerLow) {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == current,
                onClick = { onSelect(t) },
                icon = { Icon(painterResource(t.icon), contentDescription = null) },
                label = { Text(t.label) },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = c.primaryContainer,
                        selectedIconColor = c.onPrimaryContainer,
                        selectedTextColor = c.onSurface,
                        unselectedIconColor = c.onSurfaceVariant,
                        unselectedTextColor = c.onSurfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
