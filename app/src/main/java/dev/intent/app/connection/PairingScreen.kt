package dev.intent.app.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField as OutlinedTextField
import dev.intent.app.design.intentPageInsets
import dev.intent.core.protocol.pairing.ManualPairingInput
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.tailcat.Tailcat

@Composable
fun PairingScreen(
    busy: Boolean,
    onScan: () -> Unit,
    onManual: (ManualPairingInput) -> Unit,
    modifier: Modifier = Modifier,
    tunnelAvailable: Boolean = Tailcat.available
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = manual && !busy) { manual = false }
    Column(
        modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
    ) {
        if (manual) {
            TextButton(onClick = {
                manual = false
            }, enabled = !busy) { Text("Back to pairing options") }
            ManualPairingForm(busy, onManual, tunnelAvailable)
        } else {
            Text("Connect to Intent", style = MaterialTheme.typography.headlineSmall)
            Text(
                "On your desktop, open Settings → Mobile to get the pairing code and connection details.",
                color = IntentScreen.secondaryText
            )
            Button(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Scan QR code")
            }
            TextButton(
                onClick = { manual = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enter connection details") }
            Text("The Android companion is under development.", color = IntentScreen.secondaryText)
        }
    }
}

@Composable
private fun ManualPairingForm(
    busy: Boolean,
    onManual: (ManualPairingInput) -> Unit,
    tunnelAvailable: Boolean
) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("5181") }
    var token by remember { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    val input = ManualPairingInput(host, port, token, fingerprint)
    val errors = manualPairingErrors(input, tunnelAvailable)
    IntentSection("Manual connection") {
        OutlinedTextField(
            host,
            { host = it },
            label = { Text("Host / Tailcat address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            isError = host.isNotEmpty() && errors.host != null,
            supportingText = {
                Text(
                    errors.host.takeIf {
                        host.isNotEmpty()
                    } ?: "Host name, IP, or Tailcat address"
                )
            }
        )
        if (!tunnelAvailable) {
            Text(TUNNEL_UNAVAILABLE_MESSAGE, color = IntentScreen.secondaryText)
        }
        OutlinedTextField(
            port, { port = it }, label = { Text("Port") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = port.isNotEmpty() && errors.port != null,
            supportingText = {
                Text(
                    errors.port.takeIf { port.isNotEmpty() }
                        ?: "Default 5181; use the port on your desktop"
                )
            }
        )
        PairingTokenField(token, { token = it }, busy, errors.token)
        FingerprintField(fingerprint, { fingerprint = it }, busy, errors.fingerprint)
        Button(
            onClick = { onManual(input) },
            enabled = !busy && errors.isValid,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Checking certificate…" else "Review connection") }
    }
}

@Composable
private fun PairingTokenField(
    value: String,
    onChange: (String) -> Unit,
    busy: Boolean,
    error: String?
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value,
        onChange,
        label = { Text("Pairing token") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = !busy) {
                Text(if (visible) "Hide token" else "Show token")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = value.isNotEmpty() && error != null,
        supportingText = { Text(error.takeIf { value.isNotEmpty() } ?: "Token from your desktop") }
    )
}

@Composable
private fun FingerprintField(
    value: String,
    onChange: (String) -> Unit,
    busy: Boolean,
    error: String?
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text("Certificate fingerprint (optional)") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        isError = error != null,
        supportingText = { Text(error ?: "SHA-256 fingerprint from your desktop") }
    )
    Text(
        "If the fingerprint is empty, compare the server certificate with your desktop before you confirm.",
        color = IntentScreen.secondaryText
    )
}

@Suppress("LongParameterList") // Trust review keeps each action and build availability explicit.
@Composable
fun PairingConfirmation(
    payload: PairingPayload,
    busy: Boolean,
    onProbe: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    tunnelAvailable: Boolean = Tailcat.available
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Trust and connect?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                payload.hosts.forEach { host ->
                    Text("Direct: ${connectionEndpoint(host, payload.port).removeSuffix("/ws")}")
                }
                payload.tcAddress?.let { address ->
                    Text("Tailcat: $address")
                    Text("Port: ${payload.port}")
                    if (!tunnelAvailable) {
                        Text(
                            "Tailcat is unavailable in this build. Only direct routes will be used."
                        )
                    }
                }
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
                    if (busy) {
                        "Checking certificate…"
                    } else if (payload.certFingerprint ==
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
