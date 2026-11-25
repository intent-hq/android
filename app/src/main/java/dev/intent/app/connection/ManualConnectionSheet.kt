@file:Suppress("MagicNumber") // Matches iOS ConnectionSheet and ManualTokenEntry layout tokens.

package dev.intent.app.connection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intent.app.R
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.Inter
import dev.intent.app.design.LocalIntentDarkTheme
import dev.intent.core.protocol.pairing.ManualPairingInput

@Suppress("LongMethod")
@Composable
internal fun ManualConnectionOverlay(
    visible: Boolean,
    busy: Boolean,
    tunnelAvailable: Boolean,
    onManual: (ManualPairingInput) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible && !busy, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080808).copy(alpha = 0.72f))
                    .clickable(
                        enabled = !busy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            ManualConnectionSheet(
                busy = busy,
                tunnelAvailable = tunnelAvailable,
                onManual = onManual,
                onDismiss = onDismiss
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun ManualConnectionSheet(
    busy: Boolean,
    tunnelAvailable: Boolean,
    onManual: (ManualPairingInput) -> Unit,
    onDismiss: () -> Unit
) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("5181") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val canConnect = host.isNotBlank() && token.isNotBlank()

    fun submit() {
        val input = ManualPairingInput(host, port, token)
        val errors = manualPairingErrors(input, tunnelAvailable)
        val firstError = errors.host ?: errors.port ?: errors.token
        if (firstError != null) {
            errorMessage = firstError
        } else {
            errorMessage = null
            onManual(input)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).imePadding(),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        color = IntentScreen.canvas,
        contentColor = IntentScreen.text
    ) {
        Column(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp).size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.intent_close),
                    contentDescription = "Close",
                    modifier = Modifier.size(24.dp),
                    tint = if (busy) {
                        IntentScreen.text.copy(alpha = 0.3f)
                    } else {
                        IntentScreen.text
                    }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                ManualConnectionHeader()
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ManualTextField(
                            label = "Host / Tailcat Address",
                            value = host,
                            onValueChange = {
                                host = it
                                errorMessage = null
                            },
                            placeholder = "192.168.1.42",
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                        ManualTextField(
                            label = "Port",
                            value = port,
                            onValueChange = {
                                port = it
                                errorMessage = null
                            },
                            placeholder = "5181",
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                        ManualTokenField(
                            value = token,
                            onValueChange = {
                                token = it
                                errorMessage = null
                            },
                            visible = tokenVisible,
                            onToggleVisibility = { tokenVisible = !tokenVisible },
                            enabled = !busy,
                            onDone = { if (canConnect && !busy) submit() }
                        )
                    }
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = IntentScreen.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("manual.error")
                        )
                    }
                    ManualSubmitButton(
                        busy = busy,
                        enabled = canConnect && !busy,
                        onClick = ::submit
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualConnectionHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Manual Connection",
            style = PairingHeadingStyle,
            color = IntentScreen.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Enter IP, port, and token",
            style = PairingBodyStyle,
            color = IntentScreen.secondaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ManualFieldShell(
    label: String,
    verticalPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label.uppercase(),
            style = ManualEyebrowStyle,
            color = IntentScreen.secondaryText
        )
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(20.dp),
            color = IntentScreen.surface,
            contentColor = IntentScreen.text
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = verticalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                content()
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ManualTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions
) {
    val textColor = IntentScreen.text
    ManualFieldShell(label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = PairingOptionTitleStyle.copy(color = textColor),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = PairingOptionTitleStyle,
                            color = textColor.copy(alpha = 0.3f)
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ManualTokenField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    onDone: () -> Unit
) {
    val textColor = IntentScreen.text
    ManualFieldShell("API Token", verticalPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = PairingOptionTitleStyle.copy(color = textColor),
                cursorBrush = SolidColor(textColor),
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "API Token" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Paste token",
                                style = PairingOptionTitleStyle,
                                color = textColor.copy(alpha = 0.3f)
                            )
                        }
                        inner()
                    }
                }
            )
            TokenFieldActions(
                showPaste = value.isEmpty(),
                visible = visible,
                enabled = enabled,
                onPaste = onValueChange,
                onToggleVisibility = onToggleVisibility
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun TokenFieldActions(
    showPaste: Boolean,
    visible: Boolean,
    enabled: Boolean,
    onPaste: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    val textColor = IntentScreen.text
    val dark = LocalIntentDarkTheme.current
    val clipboard = LocalClipboardManager.current
    val iconTint = if (!enabled || dark) textColor.copy(alpha = 0.3f) else textColor
    if (showPaste) {
        IconButton(
            onClick = { clipboard.getText()?.text?.let(onPaste) },
            enabled = enabled,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_connection_clipboard),
                contentDescription = "Paste API token",
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
        }
    }
    IconButton(
        onClick = onToggleVisibility,
        enabled = enabled,
        modifier = Modifier.offset(x = 10.dp).size(44.dp)
    ) {
        Icon(
            painter = painterResource(
                if (visible) R.drawable.ic_eye_closed else R.drawable.ic_eye
            ),
            contentDescription = if (visible) "Hide API token" else "Show API token",
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
    }
}

@Composable
private fun ManualSubmitButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val dark = LocalIntentDarkTheme.current
    val active = busy || enabled
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else if (dark) {
        Color(0xFF525250)
    } else {
        Color(0xFFBABAB7)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else if (dark) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
    } else {
        Color(0xFFF0F0F0).copy(alpha = 0.55f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            }
            Text(
                text = if (busy) "Connecting…" else "Connect",
                style = PairingOptionTitleStyle,
                color = contentColor
            )
        }
    }
}

private val ManualEyebrowStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(450),
    fontSize = 12.sp,
    letterSpacing = 0.24.sp,
    lineHeight = 15.sp
)
