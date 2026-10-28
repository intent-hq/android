package dev.intent.app.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen

@Suppress("LongParameterList", "LongMethod") // One compact browser toolbar.
@Composable
internal fun BrowserAddressBar(
    state: BrowserState,
    initialAddress: String,
    close: () -> Unit,
    open: (String) -> Unit,
    back: () -> Unit,
    forward: () -> Unit,
    reload: () -> Unit
) {
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    var focused by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.requestedUrl, focused) {
        if (!focused) state.requestedUrl?.let { address = it }
    }
    val submit = {
        val value = address.trim()
        if (value.isNotEmpty()) {
            open(if (value.contains("://")) value else "http://$value")
            focus.clearFocus()
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().background(IntentScreen.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = close) { Icon(Icons.Default.Close, "Close preview") }
            IconButton(onClick = back, enabled = state.canGoBack && state.error == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Page back")
            }
            IconButton(onClick = forward, enabled = state.canGoForward && state.error == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Page forward")
            }
            BasicTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f)
                    .background(IntentScreen.canvas, RoundedCornerShape(8.dp)).padding(8.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Browser URL" },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = IntentScreen.text),
                cursorBrush = SolidColor(IntentScreen.text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                decorationBox = { field ->
                    if (address.isEmpty()) Text("Enter a URL", color = IntentScreen.secondaryText)
                    field()
                }
            )
            IconButton(
                onClick = { if (focused || state.requestedUrl == null) submit() else reload() },
                enabled = address.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Refresh,
                    if (state.error ==
                        null
                    ) {
                        "Reload page"
                    } else {
                        "Retry preview"
                    }
                )
            }
        }
        if (state.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().semantics {
                    contentDescription =
                        "Loading preview"
                }
            )
        }
    }
}
