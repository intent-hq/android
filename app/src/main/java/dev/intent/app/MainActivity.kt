package dev.intent.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.ConnectionViewModel
import dev.intent.app.design.IntentTheme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ConnectionViewModel::class.java] }
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(model.controller::prepareLink)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) acceptLink(intent)
        setContent {
            IntentTheme {
                Scaffold { padding ->
                    ConnectionRoot(
                        model.controller,
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
        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let(model.controller::prepareLink)
            intent.data = null
        }
    }
}
