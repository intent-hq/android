package dev.intent.app.connection

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun ConnectionSettings(diagnostics: ConnectionDiagnostics, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("Settings")
        Text("Connection diagnostics — under development")
        TextButton(onClick = { report = diagnostics.report() }) { Text("View diagnostics") }
        report?.let { preview ->
            Text(preview)
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
}
