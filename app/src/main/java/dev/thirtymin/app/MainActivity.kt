package dev.thirtymin.app

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    // Permission states: re-read on every resume, the user may come back from Settings.
    private var canOverlay by mutableStateOf(false)
    private var canAlarm by mutableStateOf(false)
    private var canNotify by mutableStateOf(false)
    private var batteryFree by mutableStateOf(false)
    private var canSeeUsage by mutableStateOf(false)
    private var schedule by mutableStateOf(SessionStore.DEFAULT_SCHEDULE)
    private var showSetup by mutableStateOf(false)
    private var showTutorial by mutableStateOf(false)

    // Sticky for this visit: the alarm is fixed on resume, the warning stays readable.
    private var alarmWasLost by mutableStateOf(false)

    // Denied before (or "don't ask again"): the dialog no longer shows, so open Settings.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            canNotify = granted
            if (!granted) openAppSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readPermissions()
        // First run, or a required permission was lost: start on the checklist.
        showSetup = !(canOverlay && canAlarm)
        // Once, right after setup (or on the first open after an update that added it).
        showTutorial = !SessionStore(this).tutorialSeen
        setContent {
            ThirtyMinTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                // Room runs the query off the main thread; the Flow re-emits on every write.
                val history = remember { HistoryDb.get(this@MainActivity).sessions().safeAll() }
                val rows by history.collectAsState(emptyList())
                BackHandler(enabled = showSetup) { showSetup = false }
                BackHandler(enabled = showTutorial && !showSetup, onBack = ::closeTutorial)
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { if (!showSetup && !showTutorial) NavBar(tab) { tab = it } }
                ) { insets ->
                    // Pager pages fill the screen: outside the scrolling column.
                    if (showTutorial && !showSetup) {
                        Box(Modifier.fillMaxSize().padding(insets)) {
                            TutorialScreen(schedule) {
                                closeTutorial()
                                tab = Tab.TODAY
                            }
                        }
                        return@Scaffold
                    }
                    // key: each destination gets its own scroll position, starting at the top.
                    key(showSetup, tab) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(insets)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when {
                                showSetup -> SetupScreen(permissions(), isSamsung()) {
                                    showSetup = false
                                    tab = Tab.TODAY
                                }

                                tab == Tab.TODAY -> Today(rows) { tab = Tab.HISTORY }

                                // ponytail: "today" read on recomposition; a screen left open
                                // past midnight shows yesterday as today until it redraws.
                                tab == Tab.HISTORY -> HistoryScreen(
                                    rows.outcomeMap(),
                                    LocalDate.now()
                                )

                                else -> SettingsScreen(
                                    schedule,
                                    ::saveSchedule,
                                    setupSummary(),
                                    onOpenSetup = { showSetup = true },
                                    onOpenTutorial = { showTutorial = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readPermissions()
        val store = SessionStore(this)
        schedule = store.schedule
        // Alarms vanish on force-stop and permission revoke: re-set on every open.
        Alarms.scheduleDaily(this)
        if (store.takeAlarmLost()) alarmWasLost = true
    }

    private fun closeTutorial() {
        SessionStore(this).tutorialSeen = true
        showTutorial = false
    }

    private fun readPermissions() {
        canOverlay = Settings.canDrawOverlays(this)
        canAlarm = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        // Runtime permission only since Android 13; before that notifications are on by default.
        canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        batteryFree =
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        canSeeUsage =
            getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
    }

    private fun permissions() = listOf(
        Permission(
            R.drawable.ic_layers,
            "Display over other apps",
            "So the morning screen can appear.",
            canOverlay,
            required = true
        ) { openAppSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) },
        Permission(
            R.drawable.ic_alarm,
            "Exact alarms",
            "To know when your wake window opens.",
            canAlarm,
            required = true
        ) { openAppSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
        Permission(
            R.drawable.ic_notifications,
            "Notifications",
            "Shows a quiet notice while a block runs.",
            canNotify,
            required = false
        ) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        Permission(
            R.drawable.ic_battery_full,
            "Unrestricted battery",
            "Keeps 30-Min awake overnight. Battery › Unrestricted.",
            batteryFree,
            required = false
            // App info page, not the direct exemption request: Play restricts that one.
        ) { openAppSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
        Permission(
            R.drawable.ic_call,
            "Usage access",
            "Brings the block back as soon as you leave the phone app.",
            canSeeUsage,
            required = false
        ) { openAppSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
    )

    private fun setupSummary(): String {
        val missing = permissions().count { !it.granted }
        return when (missing) {
            0 -> "All permissions ready"
            1 -> "1 permission needs attention"
            else -> "$missing permissions need attention"
        }
    }

    private fun isSamsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** App-specific page when the action supports it, else the general one. */
    private fun openAppSettings(action: String) {
        val specific =
            Intent(action, Uri.parse("package:$packageName"))
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(specific)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(action))
            } catch (e: ActivityNotFoundException) {
                // Some vendor builds remove settings pages: tell, do not crash.
                Toast.makeText(
                    this,
                    "Open this in your phone's Settings app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Composable
    private fun Today(rows: List<SessionRecord>, openHistory: () -> Unit) =
        HomeScreen(rows, LocalDateTime.now(), schedule, openHistory) {
            if (alarmWasLost) LostAlarmCard { showSetup = true }
            // Test tools, not mornings: debug builds only (BuildConfig.DEBUG is a
            // compile-time false in release, so these are stripped there).
            if (BuildConfig.DEBUG) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canOverlay) {
                        OutlinedButton(onClick = ::startBlock) {
                            Text("Block $PROTOTYPE_MINUTES min")
                        }
                    }
                    if (canAlarm) {
                        OutlinedButton(onClick = ::scheduleTestAlarm) {
                            Text("Test alarm")
                        }
                    }
                }
            }
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

    private fun scheduleTestAlarm() {
        val ok = Alarms.scheduleDebug(this)
        Toast.makeText(
            this,
            if (ok) "Test alarm in 1 min. Lock the phone." else "Alarm denied",
            Toast.LENGTH_SHORT
        ).show()
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

/** The phone force-stopped us: say so, since that silently skips mornings. */
@Composable
private fun LostAlarmCard(onOpenSetup: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = c.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Your phone stopped 30-Min",
                style = MaterialTheme.typography.titleMedium,
                color = c.onPrimaryContainer
            )
            Text(
                "Tomorrow's block was missing, so it has been set again. " +
                    "To keep it from happening, check Phone setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onPrimaryContainer
            )
            Button(onClick = onOpenSetup) { Text("Open Phone setup") }
        }
    }
}
