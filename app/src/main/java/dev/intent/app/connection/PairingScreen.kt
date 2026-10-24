package dev.intent.app.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.pairing.ManualPairingInput
import dev.intent.core.protocol.pairing.PairingPayload

@Composable
fun PairingScreen(
    busy: Boolean,
    onScan: () -> Unit,
    onManual: (ManualPairingInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("") }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pair with Intent", style = MaterialTheme.typography.headlineSmall)
        Text("Open pairing on your desktop. Scan its QR code or enter the connection details.")
        Text("The Android companion is under development. A direct network connection is required.")
        Button(onClick = onScan, enabled = !busy) { Text("Scan QR code") }
        OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true)
        OutlinedTextField(
            port,
            { port = it },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            token,
            { token = it },
            label = { Text("Pairing token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            fingerprint,
            {
                fingerprint = it
            },
            label = { Text("Certificate fingerprint (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "If the fingerprint is empty, compare the server certificate with your desktop before you confirm."
        )
        Button(
            onClick = { onManual(ManualPairingInput(host, port, token, fingerprint)) },
            enabled = !busy && host.isNotBlank() && port.isNotBlank() && token.isNotBlank()
        ) { Text(if (busy) "Checking certificate…" else "Review connection") }
    }
}

@Composable
fun PairingConfirmation(
    payload: PairingPayload,
    busy: Boolean,
    onProbe: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Trust and connect?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Server: ${payload.hosts.joinToString()}:${payload.port}")
                Text("Compare this certificate fingerprint with the fingerprint on your desktop.")
                Text(
                    payload.certFingerprint ?: "Fetch the certificate before you trust this server."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (payload.certFingerprint == null) onProbe else onConfirm,
                enabled = !busy
            ) {
                Text(
                    if (payload.certFingerprint ==
                        null
                    ) {
                        "Fetch certificate"
                    } else {
                        "Trust and connect"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Suppress("UnusedPrivateMember", "MagicNumber")
@Preview(showBackground = true)
@Composable
private fun PairingPreview() {
    MaterialTheme { PairingScreen(false, {}, {}) }
}
