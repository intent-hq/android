package dev.intentapp.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.intentapp.android.connection.ConnectionController
import dev.intentapp.android.connection.ConnectionRoot
import dev.intentapp.android.connection.ConnectionViewModel
import dev.intentapp.android.connection.LaunchSplash
import dev.intentapp.android.connection.LocalNetworkPermissionGate
import dev.intentapp.android.connection.PairingScannerActivity
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.settings.AppPreferences
import dev.intentapp.android.settings.Appearance
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ConnectionViewModel::class.java] }
    private var notificationWorkspace by mutableStateOf<Pair<String, String>?>(null)
    private val preferences by lazy { AppPreferences(applicationContext) }
    private var pendingIntent by mutableStateOf<Intent?>(null)
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(model.controller::prepareLink)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingIntent = intent
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
                Box {
                    Scaffold { padding ->
                        LocalNetworkPermissionGate(Modifier.fillMaxSize().padding(padding)) {
                            LaunchedEffect(pendingIntent) {
                                pendingIntent?.let(::acceptLink)
                                pendingIntent = null
                            }
                            ConnectionRoot(
                                model.controller,
                                preferences = preferences,
                                notificationWorkspace = notificationWorkspace,
                                onScan = {
                                    scanner.launch(
                                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setCaptureActivity(PairingScannerActivity::class.java)
                                            .setOrientationLocked(false)
                                            .setPrompt("Scan the Intent pairing code")
                                            .setBeepEnabled(false).setBarcodeImageEnabled(false)
                                    )
                                },
                                modifier = Modifier.fillMaxSize().padding(padding)
                            )
                        }
                    }
                    ColdStartSplash(model.controller, coldStart = savedInstanceState == null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent = intent
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

/** Like iOS LoadingView, a cold start keeps the splash for at least 1.2 s and until a saved connection connects. */
@Composable
private fun ColdStartSplash(controller: ConnectionController, coldStart: Boolean) {
    val state by controller.state.collectAsState()
    var held by remember { mutableStateOf(coldStart) }
    var visible by remember { mutableStateOf(coldStart) }
    LaunchedEffect(Unit) {
        delay(MINIMUM_SPLASH_MILLIS)
        held = false
    }
    val connecting =
        state.session == null && state.connections.isNotEmpty() && state.status == "Connecting"
    if (visible && !held && !connecting) visible = false
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = fadeOut()) {
        LaunchSplash(Modifier.fillMaxSize())
    }
}

private const val MINIMUM_SPLASH_MILLIS = 1_200L
