package dev.intentapp.android.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentButton
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing

/** iOS NewWorkspaceBrowserView presented as a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBrowserSheet(onDismiss: () -> Unit, onCreate: (String) -> Result<*>) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IntentScreen.canvas,
        contentColor = IntentScreen.text,
        dragHandle = null
    ) {
        NewBrowserForm(onDismiss, onCreate)
    }
}

@Suppress("LongMethod") // One toolbar and one form section.
@Composable
fun NewBrowserForm(onCancel: () -> Unit, onCreate: (String) -> Result<*>) {
    var address by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val create = {
        error = onCreate(address).exceptionOrNull()?.let { it.message ?: INVALID_BROWSER_URL }
    }
    Column(Modifier.fillMaxWidth().imePadding().padding(bottom = IntentSpacing.page)) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = IntentSpacing.content,
                vertical = IntentSpacing.medium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.intent_close), "Cancel")
            }
            Text(
                "New browser",
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            IntentButton(onClick = create, enabled = address.isNotBlank()) { Text("Open") }
        }
        Column(
            Modifier.padding(horizontal = IntentSpacing.page, vertical = IntentSpacing.content),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Text(
                "Address",
                Modifier.padding(horizontal = IntentSpacing.content),
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText
            )
            Surface(
                shape = RoundedCornerShape(FIELD_RADIUS),
                color = IntentScreen.surface,
                contentColor = IntentScreen.text
            ) {
                BasicTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = IntentSpacing.content, vertical = 14.dp)
                        .focusRequester(focus)
                        .semantics { contentDescription = "Browser address" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = IntentScreen.text),
                    cursorBrush = SolidColor(IntentScreen.text),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { create() }),
                    decorationBox = { field ->
                        if (address.isEmpty()) {
                            Text(
                                "localhost:3000",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IntentScreen.secondaryText
                            )
                        }
                        field()
                    }
                )
            }
            Text(
                "Use localhost and the server port to open a server running on your connected machine.",
                Modifier.padding(horizontal = IntentSpacing.content),
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText
            )
            error?.let {
                Text(
                    it,
                    Modifier.padding(
                        horizontal = IntentSpacing.content,
                        vertical = IntentSpacing.medium
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight(BODY_WEIGHT)
                    ),
                    color = IntentScreen.error
                )
            }
        }
    }
}

/** iOS IntentButtonStyle(variant: .secondary): card fill, 20dp radius, 44dp minimum height. */
@Composable
internal fun BrowserSecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(SECONDARY_RADIUS),
        color = if (enabled) {
            IntentScreen.surface
        } else {
            IntentScreen.text.copy(
                alpha = DISABLED_FILL
            )
        },
        contentColor = if (enabled) {
            IntentScreen.text
        } else {
            IntentScreen.text.copy(
                alpha = DISABLED_TEXT
            )
        }
    ) {
        Text(
            label,
            Modifier.heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private val FIELD_RADIUS = 20.dp
private val SECONDARY_RADIUS = 20.dp
private const val DISABLED_FILL = 0.08f
private const val DISABLED_TEXT = 0.3f
private const val BODY_WEIGHT = 450
