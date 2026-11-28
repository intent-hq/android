package dev.intentapp.android.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun SearchableList(
    query: String,
    onQuery: (String) -> Unit,
    label: String,
    hasData: Boolean,
    currentOnly: Boolean? = null,
    onCurrentOnly: (Boolean) -> Unit = {},
    launcher: dev.intentapp.android.navigation.LauncherActions? = null,
    controls: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val keyboardInsets = WindowInsets.ime.exclude(WindowInsets.navigationBars)
    val keyboardInset = with(density) { keyboardInsets.getBottom(density).toDp() }
    val showFloatingControls = !searching && !keyboardVisible
    val floatingSearch = controls == null && hasData
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) searchFocus.requestFocus()
    }
    val close: () -> Unit = {
        onQuery("")
        searching = false
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(searching, onBack = close)
    Box(Modifier.fillMaxSize()) {
        Column {
            controls?.invoke(searching) {
                if (searching) close() else searching = true
            }
            CurrentListFilter(currentOnly, onCurrentOnly)
            Box(Modifier.weight(1f)) {
                content(
                    PaddingValues(
                        start = dev.intentapp.android.design.IntentSpacing.page,
                        end = dev.intentapp.android.design.IntentSpacing.page,
                        top = 4.dp,
                        bottom = keyboardInset + listBottomInset(
                            searching,
                            keyboardVisible,
                            launcher != null,
                            floatingSearch
                        )
                    )
                )
            }
        }
        if (searching) {
            SearchInput(
                query,
                onQuery,
                label,
                onClose = close,
                focusRequester = searchFocus,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(keyboardInsets)
            )
        }
        if (showFloatingControls && floatingSearch) {
            FloatingActionButton(
                onClick = { searching = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(
                    end = 16.dp,
                    bottom = if (launcher == null) 16.dp else 112.dp
                )
            ) { Icon(painterResource(R.drawable.ic_search), label) }
        }
        if (showFloatingControls && launcher != null) {
            dev.intentapp.android.navigation.WorkspaceActionLauncher(
                launcher,
                Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

@Composable
private fun CurrentListFilter(currentOnly: Boolean?, onCurrentOnly: (Boolean) -> Unit) {
    if (currentOnly != null) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(currentOnly, { onCurrentOnly(true) }, label = { Text("Current") })
            FilterChip(!currentOnly, { onCurrentOnly(false) }, label = { Text("All") })
        }
    }
}

/** iOS WorkspaceSearchInput: a bottom search field and close button that ride above the keyboard. */
@Suppress("LongParameterList") // Query state, label, close action, focus and placement.
@Composable
private fun SearchInput(
    query: String,
    onQuery: (String) -> Unit,
    label: String,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier.fillMaxWidth().padding(SearchSpacing),
        horizontalArrangement = Arrangement.spacedBy(SearchSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = SearchHeight)
                .clip(RoundedCornerShape(SearchRadius))
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                query,
                onQuery,
                Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        field()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        painterResource(R.drawable.intent_close),
                        "Clear search",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SearchCloseButton(onClose)
    }
}

@Composable
private fun SearchCloseButton(onClose: () -> Unit) {
    Box(
        Modifier.size(SearchHeight)
            .clip(RoundedCornerShape(SearchRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Close search", onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.intent_close), "Close search", Modifier.size(24.dp))
    }
}

private val SearchHeight = 56.dp
private val SearchRadius = 20.dp
private val SearchSpacing = 8.dp

private fun listBottomInset(
    searching: Boolean,
    keyboardVisible: Boolean,
    launcher: Boolean,
    hasData: Boolean
) = when {
    searching -> SearchHeight + SearchSpacing * 2 + 16.dp
    keyboardVisible -> 16.dp
    launcher && hasData -> 184.dp
    launcher -> 112.dp
    hasData -> 88.dp
    else -> 16.dp
}
