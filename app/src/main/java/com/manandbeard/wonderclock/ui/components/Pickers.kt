package com.manandbeard.wonderclock.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Picks a time zone, or "device" to follow the phone. */
@Composable
fun ZonePickerDialog(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val now = remember { Instant.now() }
    val filtered = remember(query, zones) {
        if (query.isBlank()) zones
        else zones.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    item {
                        ZoneRow(
                            title = "Device time zone",
                            detail = ZoneId.systemDefault().id,
                            selected = current == null,
                            onClick = { onPick(null); onDismiss() },
                        )
                    }
                    items(filtered) { id ->
                        ZoneRow(
                            title = id,
                            detail = offsetLabel(id, now),
                            selected = current == id,
                            onClick = { onPick(id); onDismiss() },
                        )
                    }
                }
            }
        },
    )
}

private fun offsetLabel(zoneId: String, now: Instant): String = runCatching {
    val offset = ZoneId.of(zoneId).rules.getOffset(now).id
    if (offset == "Z") "UTC" else "UTC$offset"
}.getOrDefault("")

@Composable
private fun ZoneRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

data class LaunchableApp(val packageName: String, val label: String)

/** Picks an installed app for the "open an app" tap action. */
@Composable
fun AppPickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (LaunchableApp) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { launchableApps(context) }
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps.isEmpty()) {
                    EmptyHint("Looking for installed apps…")
                }
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(filtered) { app ->
                        ZoneRow(
                            title = app.label,
                            detail = if (app.packageName == current) "current" else "",
                            selected = app.packageName == current,
                            onClick = { onPick(app); onDismiss() },
                        )
                    }
                }
            }
        },
    )
}

private fun launchableApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                LaunchableApp(
                    packageName = activity.packageName,
                    label = resolved.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}

/** Single-field prompt, used for naming a preset and for pasting a config. */
@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmLabel: String = "Save",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
