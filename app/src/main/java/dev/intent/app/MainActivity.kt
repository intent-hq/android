package dev.intent.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.ConnectionViewModel
import dev.intent.app.design.IntentTheme
import dev.intent.app.settings.AppPreferences
import dev.intent.app.settings.Appearance

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ConnectionViewModel::class.java] }
    private var notificationWorkspace by mutableStateOf<Pair<String, String>?>(null)
    private val preferences by lazy { AppPreferences(applicationContext) }
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(model.controller::prepareLink)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) acceptLink(intent)
        setContent {
            val appearance by preferences.appearance.collectAsState()
            val darkTheme = when (appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            SideEffect {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            IntentTheme(darkTheme = darkTheme) {
                Scaffold { padding ->
                    ConnectionRoot(
                        model.controller,
                        preferences = preferences,
                        notificationWorkspace = notificationWorkspace,
                        onScan = {
                            scanner.launch(
                                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Scan the Intent pairing code")
                                    .setBeepEnabled(false).setBarcodeImageEnabled(false)
                            )
                        },
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptLink(intent)
    }

    private fun acceptLink(intent: Intent) {
        if (intent.action == "dev.intent.OPEN_NOTIFICATION") {
            val connection = intent.getStringExtra("connectionId")
            val workspace = intent.getStringExtra("workspaceId")
            if (connection != null && workspace != null) {
                notificationWorkspace = connection to workspace
                // Stay on the selected backend; never interpret a workspace on another host.
            }
        }
        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let(model.controller::prepareLink)
            intent.data = null
        }
    }
}
