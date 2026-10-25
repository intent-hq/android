package dev.intent.app.connection

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.intentPageInsets
import dev.intent.app.notifications.NotificationPermissionFlow
import dev.intent.app.settings.AppPreferences
import dev.intent.app.settings.Appearance

@Composable
fun ConnectionSettings(
    diagnostics: ConnectionDiagnostics,
    modifier: Modifier = Modifier,
    preferences: AppPreferences,
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
        ConnectionsSection(onConnections)
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
    }
}

@Composable
internal fun AppearanceSelector(appearance: Appearance, onSelect: (Appearance) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Appearance.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = appearance == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size)
            ) { Text(value.name) }
        }
    }
}

@Composable
private fun SettingsDescription(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = IntentScreen.secondaryText)
}

@Composable
private fun AboutSection() {
    IntentSection("About") {
        Text("Intent for Android", style = MaterialTheme.typography.bodyLarge)
        SettingsDescription("Android companion app — under development")
    }
}

@Composable
private fun ConnectionsSection(onConnections: () -> Unit) {
    IntentSection("Connections") {
        SettingsDescription("Connect to a saved computer or add a connection.")
        TextButton(onClick = onConnections) { Text("Manage connections") }
    }
}
