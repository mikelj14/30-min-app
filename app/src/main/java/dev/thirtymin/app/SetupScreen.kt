package dev.thirtymin.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** One checklist row. [required]: the block cannot work without it. */
class Permission(
    @DrawableRes val icon: Int,
    val title: String,
    val description: String,
    val granted: Boolean,
    val required: Boolean,
    val allow: () -> Unit
)

/**
 * Design 1f. States are read by the activity on every resume, so returning
 * from a system settings page updates the rows. "Start" unlocks once the
 * required permissions are in; the others make blocks more reliable.
 */
@Composable
fun SetupScreen(perms: List<Permission>, samsung: Boolean, onDone: () -> Unit) {
    val ready = perms.count { it.granted }
    val canStart = perms.filter { it.required }.all { it.granted }
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Let's make mornings quiet", style = MaterialTheme.typography.headlineMedium)
            Text(
                "30-Min needs a few permissions to cover the screen at the right moment. " +
                    "Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { ready / perms.size.toFloat() },
                modifier = Modifier.weight(1f).height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {}
            )
            Text(
                "$ready of ${perms.size} ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(vertical = 4.dp)) { perms.forEach { Row(it) } }
        }
        if (samsung) SamsungCard()
        Button(
            onClick = onDone,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                )
        ) { Text("Start my mornings", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Row(p: Permission) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(p.icon), contentDescription = null, tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall)
            Text(
                p.description,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant
            )
        }
        if (p.granted) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = c.tertiaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = "Allowed",
                        tint = c.tertiary
                    )
                }
            }
        } else {
            FilledTonalButton(
                onClick = p.allow,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = c.primaryContainer,
                        contentColor = c.onPrimaryContainer
                    )
            ) { Text("Allow") }
        }
    }
}

/** No API can check these Samsung settings, so this only explains them. */
@Composable
private fun SamsungCard() {
    val c = MaterialTheme.colorScheme
    val strong = SpanStyle(color = c.onSurface, fontWeight = FontWeight.SemiBold)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = c.surfaceContainerLow,
        border = BorderStroke(1.dp, c.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_lightbulb),
                    contentDescription = null,
                    tint = c.primary
                )
                Text("On a Samsung Galaxy?", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                buildAnnotatedString {
                    append("Samsung can stop 30-Min overnight, and then no block starts. ")
                    append("In Settings › Battery › Background usage limits, add 30-Min to ")
                    withStyle(strong) { append("Never sleeping apps") }
                    append(". In Power saving, turn off ")
                    withStyle(strong) { append("Limit apps and Home screen") }
                    append(". If you use a Sleep mode, don't let it ")
                    withStyle(strong) { append("pause 30-Min") }
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant
            )
        }
    }
}
