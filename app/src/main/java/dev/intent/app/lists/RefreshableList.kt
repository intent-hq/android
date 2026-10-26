package dev.intent.app.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/** The child must provide a full-height scroll container, including empty and error states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshableList(
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val refresh = rememberPullToRefreshState()
    Box(
        modifier.fillMaxSize().testTag("refreshable-list").semantics {
            customActions = listOf(
                CustomAccessibilityAction("Refresh") {
                    if (!loading) onRefresh()
                    !loading
                }
            )
        }.pullToRefresh(
            isRefreshing = loading,
            state = refresh,
            enabled = !loading,
            onRefresh = onRefresh
        )
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = refresh,
            isRefreshing = loading,
            modifier = Modifier.align(Alignment.TopCenter).testTag("refresh-indicator")
        )
    }
}
