package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Reverse layout anchors the latest row at the bottom, including rows taller than the viewport. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "LongParameterList") // Shared viewport and content state.
@Composable
internal fun ConversationTranscript(
    state: ConversationState,
    onLoadOlder: () -> Unit,
    onHydrate: (String, String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onSuggestedPrompt: (String) -> Unit = {}
) {
    val list = rememberLazyListState()
    val grouping = remember { TranscriptRowGrouping() }
    val rows = remember(state.messages) { grouping.rows(state.messages) }
    val currentRows by rememberUpdatedState(rows)
    var policy by remember { mutableStateOf(TranscriptScrollPolicy()) }
    val currentState by rememberUpdatedState(state)
    val loadOlder by rememberUpdatedState(onLoadOlder)
    val scope = rememberCoroutineScope()
    val gestures = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    policy = policy.pause()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow {
            val value = currentState
            TranscriptViewport(
                atBottom = !list.canScrollBackward,
                atTop = list.layoutInfo.visibleItemsInfo.any {
                    it.index == currentRows.lastIndex
                },
                scrolling = list.isScrollInProgress,
                nextToken = value.nextToken,
                loading = value.isLoading || value.loadingOlder
            )
        }.collect { viewport ->
            policy = policy.observe(viewport)
            if (policy.shouldLoad(viewport)) {
                policy = policy.copy(requestedToken = viewport.nextToken)
                loadOlder()
            }
        }
    }
    LaunchedEffect(state.messages.lastOrNull()) {
        policy = policy.contentChanged(state.messages.lastOrNull())
        if (policy.following && state.messages.isNotEmpty()) list.scrollToItem(0)
    }
    val refresh = rememberPullToRefreshState()
    // Downward overscroll at a page boundary means read older, never discard loaded history.
    val canRefresh = state.nextToken == null && !state.loadingOlder
    Box(
        modifier = modifier.nestedScroll(gestures).pullToRefresh(
            isRefreshing = state.isLoading,
            state = refresh,
            enabled = canRefresh,
            onRefresh = onRefresh
        )
    ) {
        LazyColumn(
            state = list,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().testTag("transcript"),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
        ) {
            items(rows.asReversed(), key = { it.id }) { row ->
                TranscriptMessage(row, state.hydrating, onHydrate, onSuggestedPrompt)
            }
        }
        if (canRefresh) {
            PullToRefreshDefaults.Indicator(
                state = refresh,
                isRefreshing = state.isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        if (state.loadingOlder) {
            CircularProgressIndicator(Modifier.align(Alignment.TopCenter).size(24.dp))
        }
        if (!state.isLoading && state.messages.isEmpty()) {
            Text("No messages yet", Modifier.align(Alignment.Center))
        }
        state.error?.let {
            Text(it, Modifier.align(Alignment.TopCenter), color = MaterialTheme.colorScheme.error)
        }
        if (!policy.following && policy.hasNewMessages) {
            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                FilledTonalButton(onClick = {
                    scope.launch {
                        list.scrollToItem(0)
                        policy = policy.resume()
                    }
                }) { Text("New messages ↓") }
            }
        }
    }
}
