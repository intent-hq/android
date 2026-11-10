package dev.intent.app.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.intent.app.design.IntentButton
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton
import dev.intent.app.design.intentPageInsets

internal fun hasLocalNetworkAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun LocalNetworkPermissionGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(hasLocalNetworkAccess(context)) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = hasLocalNetworkAccess(context)
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = hasLocalNetworkAccess(context)
    }
    if (allowed) {
        content()
    } else {
        Column(
            modifier.intentPageInsets(),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
        ) {
            Text("Connect to your computer")
            Text(
                "Allow local network access so Intent can connect to your computer on this network."
            )
            IntentButton(onClick = {
                permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }) { Text("Allow local network access") }
            IntentTextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }) { Text("Open Android settings") }
        }
    }
}
