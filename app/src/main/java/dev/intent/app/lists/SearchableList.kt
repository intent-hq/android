package dev.intent.app.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@OptIn(
    ExperimentalMaterial3Api::class,
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
    launcher: dev.intent.app.navigation.LauncherActions? = null,
    content: @Composable () -> Unit
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val close: () -> Unit = {
        onQuery("")
        onCurrentOnly(false)
        searching = false
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(searching, onBack = close)
    Box(Modifier.fillMaxSize()) {
        Column {
            if (searching) {
                SearchBar(
                    query = query,
                    onQueryChange = onQuery,
                    onSearch = {
                        focus.clearFocus()
                        keyboard?.hide()
                    },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text(label) },
                    leadingIcon = {
                        IconButton(onClick = close) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = close) { Icon(Icons.Default.Close, "Clear search") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {}
                if (currentOnly != null) {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(currentOnly, {
                            onCurrentOnly(true)
                        }, label = { Text("Current") })
                        FilterChip(!currentOnly, { onCurrentOnly(false) }, label = { Text("All") })
                    }
                }
            }
            Box(Modifier.weight(1f)) { content() }
        }
        if (!searching && hasData && !keyboardVisible) {
            FloatingActionButton(
                onClick = { searching = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(
                    end = 16.dp,
                    bottom = if (launcher == null) 16.dp else 88.dp
                )
            ) { Icon(Icons.Default.Search, label) }
        }
        if (!searching && launcher != null && !keyboardVisible) {
            dev.intent.app.navigation.WorkspaceActionLauncher(
                launcher,
                Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}
