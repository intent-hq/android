package dev.intent.app.connection

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.intent.app.BuildConfig
import dev.intent.app.R
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.intentPageInsets
import dev.intent.app.notifications.NotificationPermissionFlow
import dev.intent.app.settings.AppPreferences
import dev.intent.app.settings.Appearance

@Suppress("LongParameterList", "LongMethod") // Settings sections and connection callbacks.
@Composable
fun ConnectionSettings(
    diagnostics: ConnectionDiagnostics,
    modifier: Modifier = Modifier,
    preferences: AppPreferences,
    state: ConnectionState = ConnectionState(),
    onDisconnect: (() -> Unit)? = null,
    onPair: (() -> Unit)? = null,
    onConnections: () -> Unit
) {
    val context = LocalContext.current
    val appearance by preferences.appearance.collectAsState()
    val notifications by preferences.notifications.collectAsState()
    val permissionFlow = remember(context, preferences) {
        NotificationPermissionFlow(context, preferences)
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionFlow.onPermissionResult(it)
        }
    var acknowledgements by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
    ) {
        IntentSection("Appearance") {
            AppearanceSelector(appearance, preferences::setAppearance)
            SettingsDescription("System follows the appearance of your device.")
        }
        IntentSection("Notifications") {
            Text("Local notifications: ${if (notifications) "On" else "Off"}")
            SettingsDescription(
                "Alerts require a connected session. Android can suspend the app in the background."
            )
            TextButton(onClick = {
                permissionFlow.toggle { permission.launch(it) }
            }) { Text(if (notifications) "Disable notifications" else "Enable notifications") }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text("Android notification settings") }
        }
        ConnectionsSection(state, onConnections)
        onPair?.let { pair ->
            IntentSection("Pair new computer") {
                SettingsDescription(
                    "Scan a QR code or enter connection details from Intent on your computer."
                )
                TextButton(onClick = pair) { Text("Pair computer") }
            }
        }
        IntentSection("Diagnostics") {
            SettingsDescription("Connection diagnostics — under development")
            TextButton(onClick = { report = diagnostics.report() }) { Text("View diagnostics") }
            report?.let { preview ->
                Text(preview, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, preview)
                    }
                    context.startActivity(Intent.createChooser(share, "Export diagnostics"))
                }) { Text("Export reviewed report") }
                TextButton(onClick = {
                    diagnostics.clear()
                    report = null
                }) { Text("Clear diagnostics") }
            }
        }
        AboutSection()
        TextButton(onClick = { acknowledgements = true }) { Text("Acknowledgements") }
        if (state.activeId != null && onDisconnect != null) {
            SettingsDescription("Disconnect keeps this computer saved so you can reconnect later.")
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
    if (acknowledgements) AcknowledgementsDialog { acknowledgements = false }
}

@Composable
internal fun AppearanceSelector(appearance: Appearance, onSelect: (Appearance) -> Unit) {
    dev.intent.app.design.IntentTabs(
        titles = Appearance.entries.map { it.name },
        selectedIndex = Appearance.entries.indexOf(appearance),
        onSelect = { onSelect(Appearance.entries[it]) }
    )
}

@Composable
private fun SettingsDescription(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = IntentScreen.secondaryText)
}

@Composable
private fun AboutSection() {
    IntentSection("About") {
        Text("Intent for Android", style = MaterialTheme.typography.bodyLarge)
        SettingsDescription(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        )
        SettingsDescription("Android companion app — under development")
    }
}

@Composable
private fun ConnectionsSection(state: ConnectionState, onConnections: () -> Unit) {
    IntentSection("Connections") {
        state.connections.firstOrNull { it.id == state.activeId }?.let {
            Text(it.displayName)
            ConnectionPathBadge(state, it.id)
        }
        SettingsDescription("Connect to a saved computer or add a connection.")
        TextButton(onClick = onConnections) { Text("Manage connections") }
    }
}

@Composable
private fun AcknowledgementsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenses = remember(context) {
        listOf(
            "Inter" to context.resources.openRawResource(R.raw.inter_license)
                .bufferedReader().use { it.readText() },
            "Mermaid renderer" to context.assets.open("mermaid/NOTICE.txt")
                .bufferedReader().use { it.readText() }
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Acknowledgements",
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onDismiss) { Text("Close acknowledgements") }
                LazyColumn(
                    Modifier.fillMaxSize().testTag("settings.acknowledgements"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsDescription("License notices bundled with this app.")
                    }
                    licenses.forEach { (name, license) ->
                        item {
                            Text(
                                name,
                                Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(license, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
