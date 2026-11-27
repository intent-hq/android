package dev.intentapp.android.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing

/** iOS WorkspaceBrowserView.addressBar: back, forward, URL, loading and reload in one muted pill. */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // One compact toolbar.
@Composable
internal fun BrowserAddressBar(
    state: BrowserState,
    initialAddress: String,
    connected: Boolean,
    open: (String) -> Unit,
    back: () -> Unit,
    forward: () -> Unit,
    reload: () -> Unit
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var focused by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.requestedUrl, focused) {
        if (!focused) state.requestedUrl?.let { address = it }
    }
    val submit = {
        val url = normalizeBrowserUrl(address)
        if (url == null) {
            error = INVALID_BROWSER_URL
        } else {
            error = null
            focus.clearFocus()
            open(url)
        }
    }
    val pageReady = state.error == null && connected
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("browser.addressBar")
            .background(IntentScreen.canvas)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = IntentSpacing.content, end = IntentSpacing.content)
            .padding(top = IntentSpacing.row, bottom = IntentSpacing.row),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BAR_HEIGHT)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(BAR_RADIUS)
                )
                .padding(horizontal = IntentSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IntentSpacing.small)
        ) {
            NavigationButton("Back", R.drawable.ic_chevron_left, state.canGoBack && pageReady, back)
            NavigationButton(
                "Forward",
                R.drawable.ic_chevron_right,
                state.canGoForward && pageReady,
                forward
            )
            BasicTextField(
                value = address,
                onValueChange = {
                    address = it
                    error = null
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) address = state.requestedUrl ?: address
                    }
                    .semantics { contentDescription = "Browser URL" },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = IntentScreen.text),
                cursorBrush = SolidColor(IntentScreen.text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                decorationBox = { field ->
                    if (address.isEmpty()) {
                        Text(
                            "Enter a URL",
                            style = MaterialTheme.typography.bodyLarge,
                            color = IntentScreen.secondaryText
                        )
                    }
                    field()
                }
            )
            if (state.loading && connected) {
                CircularProgressIndicator(
                    Modifier.size(LOADER_SIZE).semantics { contentDescription = "Loading page" },
                    color = IntentScreen.secondaryText,
                    strokeWidth = 2.dp
                )
            }
            NavigationButton("Reload", R.drawable.ic_clockwise_arrow, connected) {
                if (focused && address != state.requestedUrl) submit() else reload()
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = IntentScreen.error)
        }
    }
}

@Composable
private fun NavigationButton(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(CONTROL_SIZE)) {
        Icon(
            painterResource(icon),
            label,
            Modifier.size(ICON_SIZE),
            tint = if (enabled) IntentScreen.text else IntentScreen.text.copy(alpha = DISABLED)
        )
    }
}

// iOS BrowserAppearance and Constants.WorkspaceSearch: 44dp controls in a 52dp, 20dp-radius pill.
private val CONTROL_SIZE = 44.dp
private val ICON_SIZE = 24.dp
private val BAR_HEIGHT = 52.dp
private val BAR_RADIUS = 20.dp
private val LOADER_SIZE = 18.dp
private const val DISABLED = 0.3f
