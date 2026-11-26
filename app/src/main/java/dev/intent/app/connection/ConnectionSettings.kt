package dev.intent.app.connection

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.intent.app.BuildConfig
import dev.intent.app.R
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
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
    // iOS SettingsView: uppercase section labels over rounded cards of icon rows.
    Column(
        modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(SettingsSpacing)
    ) {
        SettingsGroup("Connection") {
            state.connections.firstOrNull { it.id == state.activeId }?.let {
                SettingsRow(R.drawable.ic_connection_laptop, it.displayName) {
                    ConnectionPathBadge(state, it.id)
                }
                SettingsDivider()
            }
            SettingsRow(R.drawable.ic_connection, "Manage connections", onClick = onConnections)
            if (state.activeId != null && onDisconnect != null) {
                SettingsDivider()
                SettingsRow(
                    R.drawable.ic_logout,
                    "Disconnect",
                    destructive = true,
                    onClick = onDisconnect
                )
            }
        }
        onPair?.let { pair ->
            SettingsGroup("New connection") {
                SettingsRow(R.drawable.ic_qr_code, "Pair computer", onClick = pair)
            }
        }
        SettingsSection("Appearance") { AppearanceSelector(appearance, preferences::setAppearance) }
        SettingsGroup("Notifications") {
            SettingsRow(
                R.drawable.ic_bell,
                "Local notifications",
                onClick = { permissionFlow.toggle { permission.launch(it) } },
                trailing = {
                    Switch(
                        checked = notifications,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }
            )
            SettingsDivider()
            SettingsRow(R.drawable.ic_settings, "Android notification settings", onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            })
        }
        SettingsGroup("Support") {
            SettingsRow(R.drawable.ic_download_file, "View diagnostics", onClick = {
                report = diagnostics.report()
            })
            report?.let { preview ->
                Text(
                    preview,
                    Modifier.padding(
                        horizontal = IntentSpacing.page,
                        vertical = IntentSpacing.medium
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                SettingsDivider()
                SettingsRow(R.drawable.ic_arrow_right, "Export reviewed report", onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, preview)
                    }
                    context.startActivity(Intent.createChooser(share, "Export diagnostics"))
                })
                SettingsDivider()
                SettingsRow(R.drawable.ic_delete, "Clear diagnostics", onClick = {
                    diagnostics.clear()
                    report = null
                })
            }
            SettingsDivider()
            SettingsRow(R.drawable.ic_file, "Acknowledgements", onClick = {
                acknowledgements = true
            })
        }
        Text(
            "Intent for Android · Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
    }
    if (acknowledgements) AcknowledgementsDialog { acknowledgements = false }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsHeaderSpacing)) {
        Text(
            title.uppercase(),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelSmall,
            color = IntentScreen.secondaryText
        )
        content()
    }
}

@Composable
private fun SettingsGroup(title: String, rows: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsCardRadius))
                .background(IntentScreen.surface),
            content = rows
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Suppress("LongParameterList") // Icon, title, optional detail, trailing control and action.
@Composable
private fun SettingsRow(
    icon: Int,
    title: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null
) {
    val color = if (destructive) IntentScreen.error else IntentScreen.text
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowHeight)
            .then(
                if (onClick !=
                    null
                ) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = IntentSpacing.page, vertical = IntentSpacing.content),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.page),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = color)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            detail?.invoke()
        }
        trailing?.invoke()
    }
}

// iOS SettingsStyle geometry.
private val SettingsSpacing = 24.dp
private val SettingsHeaderSpacing = 12.dp
private val SettingsCardRadius = 20.dp
private val SettingsRowHeight = 56.dp

@Composable
internal fun AppearanceSelector(appearance: Appearance, onSelect: (Appearance) -> Unit) {
    dev.intent.app.design.IntentTabs(
        titles = Appearance.entries.map { it.name },
        selectedIndex = Appearance.entries.indexOf(appearance),
        onSelect = { onSelect(Appearance.entries[it]) }
    )
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
                        Text(
                            "License notices bundled with this app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentScreen.secondaryText
                        )
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
