@file:Suppress("MagicNumber") // Matches iOS PairingView spacing and typography tokens.

package dev.intent.app.connection

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intent.app.R
import dev.intent.app.design.IntentLightDarkPreview
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTheme
import dev.intent.app.design.Inter
import dev.intent.core.protocol.pairing.ManualPairingInput
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.tailcat.Tailcat

@Suppress("LongParameterList")
@Composable
fun PairingScreen(
    busy: Boolean,
    onScan: () -> Unit,
    onManual: (ManualPairingInput) -> Unit,
    modifier: Modifier = Modifier,
    tunnelAvailable: Boolean = Tailcat.available,
    error: String? = null,
    onDismissError: () -> Unit = {}
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(IntentScreen.canvas)) {
        PairingLanding(
            busy = busy,
            onScan = onScan,
            onOpenManual = {
                onDismissError()
                manual = true
            }
        )
        ManualConnectionOverlay(
            visible = manual,
            busy = busy,
            tunnelAvailable = tunnelAvailable,
            externalError = error,
            onManual = onManual,
            onDismiss = {
                onDismissError()
                manual = false
            }
        )
    }
}

@Composable
private fun PairingLanding(busy: Boolean, onScan: () -> Unit, onOpenManual: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PairingHeader()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    PairingOptionButton(
                        iconRes = R.drawable.ic_connect_qr,
                        title = "Scan QR Code",
                        subtitle = "Go to Settings > Mobile in the desktop app",
                        prominent = true,
                        enabled = !busy,
                        onClick = onScan
                    )
                    PairingOptionButton(
                        iconRes = R.drawable.ic_connect_manual,
                        title = "Manual Connection",
                        subtitle = "Enter IP, port, and token",
                        prominent = false,
                        enabled = !busy,
                        onClick = onOpenManual
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Connect to Intent",
            style = PairingHeadingStyle,
            color = IntentScreen.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Pair with your desktop app to manage workspaces and chat with agents.",
            style = PairingBodyStyle,
            color = IntentScreen.secondaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun PairingOptionButton(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    prominent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = if (prominent) IntentScreen.surface else Color.Transparent,
        contentColor = IntentScreen.text
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (prominent) 24.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (prominent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            IntentScreen.surface
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (prominent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        IntentScreen.text
                    }
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = PairingOptionTitleStyle,
                    color = IntentScreen.text
                )
                Text(
                    text = subtitle,
                    style = PairingBodyStyle,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
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
    val fingerprint = payload.certFingerprint
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Verify Server Identity") },
        text = { PairingConfirmationBody(payload, tunnelAvailable) },
        confirmButton = {
            TextButton(
                onClick = if (fingerprint == null) onProbe else onConfirm,
                enabled = !busy
            ) {
                Text(
                    if (busy) {
                        "Checking certificate…"
                    } else if (fingerprint == null) {
                        "Fetch certificate"
                    } else {
                        "Trust & Connect"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun PairingConfirmationBody(payload: PairingPayload, tunnelAvailable: Boolean) {
    val fingerprint = payload.certFingerprint
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        payload.hosts.forEach { host ->
            Text("Direct: ${connectionEndpoint(host, payload.port).removeSuffix("/ws")}")
        }
        payload.tcAddress?.let { address ->
            Text("Tailcat: $address")
            Text("Port: ${payload.port}")
        }
        if (fingerprint != null) {
            Text(
                "The server\u2019s TLS certificate fingerprint is:\n\n" +
                    "$fingerprint\n\n" +
                    "Check that it matches the fingerprint shown on your desktop " +
                    "before you trust this server."
            )
        } else {
            Text("Fetch the server\u2019s TLS certificate before trusting this connection.")
        }
        if (payload.tcAddress != null && !tunnelAvailable) {
            Text("Tailcat is unavailable in this build. Only direct routes will be used.")
        }
    }
}

internal val PairingHeadingStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(600),
    fontSize = 28.sp,
    letterSpacing = (-0.56).sp,
    lineHeight = 34.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

internal val PairingOptionTitleStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(550),
    fontSize = 16.sp,
    letterSpacing = (-0.32).sp,
    lineHeight = 20.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

internal val PairingBodyStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(450),
    fontSize = 16.sp,
    letterSpacing = (-0.16).sp,
    lineHeight = 21.12.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Suppress("UnusedPrivateMember")
@IntentLightDarkPreview
@Composable
private fun PairingPreview() {
    IntentTheme { PairingScreen(false, {}, {}) }
}
