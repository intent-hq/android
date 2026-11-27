package dev.intentapp.android.workspaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentColors
import dev.intentapp.android.design.IntentLightDarkPreview
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.IntentTabs
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.lists.RefreshableList
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.ChromeActionsMenu

@Suppress("LongParameterList", "LongMethod")
@Composable
fun WorkspaceListRoute(
    store: WorkspaceStore,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (String) -> Unit = onWorkspaceSelected,
    onAction: (Workspace, WorkspaceAction) -> Unit = { _, _ -> },
    updatingId: String? = null
) {
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    WorkspaceListScreen(
        state,
        store::refresh,
        onWorkspaceSelected,
        modifier,
        onOpenRecent,
        onAction,
        updatingId
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun WorkspaceListScreen(
    state: WorkspaceListState,
    onRefresh: () -> Unit,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (String) -> Unit = onWorkspaceSelected,
    onAction: (Workspace, WorkspaceAction) -> Unit = { _, _ -> },
    updatingId: String? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    var grouping by rememberSaveable { mutableStateOf(WorkspaceGrouping.Recent) }
    val groups = workspaceGroups(state.workspaces, query, showArchived, grouping)
    val listState = rememberLazyListState()
    val canvas = IntentScreen.canvas
    val drawerButtons = dev.intentapp.android.drawers.LocalDrawerButtons.current
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .then(dev.intentapp.android.drawers.LocalHeaderDrawerGesture.current)
                .padding(
                    start = IntentSpacing.page,
                    end = IntentSpacing.page,
                    top = TITLE_TOP_SPACING,
                    bottom = TITLE_BOTTOM_SPACING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Workspaces",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f).semantics {
                    heading()
                    drawerButtons?.let {
                        customActions = listOf(
                            CustomAccessibilityAction("Connections") {
                                it.connections()
                                true
                            },
                            CustomAccessibilityAction("Agent history") {
                                it.history()
                                true
                            }
                        )
                    }
                }
            )
            ChromeActionsMenu(
                listOf(
                    ChromeAction(if (showArchived) "Hide archived" else "Show archived") {
                        showArchived = !showArchived
                        revealedId = null
                    }
                )
            )
        }
        dev.intentapp.android.lists.SearchableList(
            query,
            {
                query = it
                revealedId = null
            },
            "Search workspaces",
            state.workspaces.isNotEmpty(),
            launcher = dev.intentapp.android.navigation.LocalLauncherActions.current,
            controls = { searching, toggleSearch ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = IntentSpacing.page),
                    horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IntentTabs(
                        WorkspaceGrouping.entries.map { it.label },
                        grouping.ordinal,
                        {
                            grouping = WorkspaceGrouping.entries[it]
                            revealedId = null
                        },
                        Modifier.weight(1f)
                    )
                    WorkspaceSearchButton(searching, state.workspaces.isNotEmpty(), toggleSearch)
                }
            }
        ) { contentPadding ->
            RefreshableList(state.isLoading, onRefresh) {
                LazyColumn(
                    Modifier.fillMaxSize().workspaceTopFade(listState, canvas),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    state.error?.let {
                        item { Text(it, PageInset, color = MaterialTheme.colorScheme.error) }
                    }
                    workspaceLoadingItem(state)
                    if (!state.isLoading && state.error == null && groups.isEmpty()) {
                        item {
                            Text(
                                if (query.isBlank()) {
                                    "No workspaces yet"
                                } else {
                                    "No matching workspaces"
                                },
                                PageInset
                            )
                        }
                    }
                    if (grouping == WorkspaceGrouping.Recent && groups.isNotEmpty()) {
                        item(key = "recent:spacing") { Spacer(Modifier.height(RECENT_TOP_SPACING)) }
                    }
                    groups.forEach { group ->
                        group.title?.let { title ->
                            item(key = "group:$title") {
                                Text(
                                    title.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IntentScreen.secondaryText,
                                    modifier =
                                    PageInset
                                        .padding(
                                            top = GROUP_TOP_SPACING,
                                            bottom = GROUP_BOTTOM_SPACING
                                        )
                                        .semantics { heading() }
                                )
                            }
                        }
                        items(group.workspaces, key = { "workspace:${it.id}" }) { workspace ->
                            WorkspaceSwipeRow(
                                workspace = workspace,
                                enabled = updatingId != workspace.id,
                                revealed = revealedId == workspace.id,
                                onReveal = { revealedId = if (it) workspace.id else null },
                                onClick = { onWorkspaceSelected(workspace.id) },
                                onOpenRecent = { onOpenRecent(workspace.id) },
                                onAction = { onAction(workspace, it) }
                            ) { WorkspaceRow(workspace) }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.workspaceTopFade(state: LazyListState, canvas: Color) = drawWithCache {
    val height = IntentSpacing.page.toPx()
    val brush = Brush.verticalGradient(listOf(canvas, canvas.copy(alpha = 0f)), endY = height)
    onDrawWithContent {
        drawContent()
        val opacity = if (state.firstVisibleItemIndex > 0) {
            1f
        } else {
            (state.firstVisibleItemScrollOffset / height).coerceIn(0f, 1f)
        }
        if (opacity > 0f) drawRect(brush, size = Size(size.width, height), alpha = opacity)
    }
}

@Composable
private fun WorkspaceRow(workspace: Workspace) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = IntentScreen.canvas
    ) {
        Column {
            Row(
                Modifier.padding(vertical = IntentSpacing.page),
                horizontalArrangement = Arrangement.spacedBy(ROW_ICON_SPACING)
            ) {
                Box(
                    Modifier
                        .padding(top = IntentSpacing.small)
                        .size(12.dp)
                        .background(
                            workspaceStatusColor(workspaceDisplayStatus(workspace)),
                            CircleShape
                        )
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ROW_TITLE_SPACING)
                ) {
                    WorkspaceRowTitle(workspace)
                    workspaceRepository(workspace)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentScreen.secondaryText
                        )
                    }
                    WorkspaceRowBadges(workspace)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRowTitle(workspace: Workspace) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.row)
    ) {
        Text(
            workspace.title,
            modifier = Modifier.weight(1f).alignBy(FirstBaseline),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        workspaceRelativeTime(workspace)?.let { relative ->
            Text(
                relative,
                modifier = Modifier
                    .alignBy(FirstBaseline)
                    .semantics { contentDescription = "Updated $relative" },
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun WorkspaceRowBadges(workspace: Workspace) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        if (!workspace.status.equals("Active", true)) WorkspaceBadge(workspace.status)
        WorkspaceBadge(workspaceDisplayStatus(workspace).label)
        workspacePrLabel(workspace)?.let { WorkspaceBadge(it) }
        workspaceSignals(workspace).forEach { WorkspaceBadge(it) }
    }
}

@Composable
private fun workspaceStatusColor(status: WorkspaceDisplayStatus): Color = when (status) {
    WorkspaceDisplayStatus.InProgress -> IntentColors.statusWorking
    WorkspaceDisplayStatus.Complete -> IntentColors.statusComplete
    WorkspaceDisplayStatus.Blocked, WorkspaceDisplayStatus.NeedsAttention ->
        IntentColors.statusAttention
    WorkspaceDisplayStatus.Failed -> MaterialTheme.colorScheme.error
    WorkspaceDisplayStatus.PrOpen, WorkspaceDisplayStatus.PrReady,
    WorkspaceDisplayStatus.PrQueued, WorkspaceDisplayStatus.PrMerged
    -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun WorkspaceBadge(label: String) {
    Surface(
        color = Color.Transparent,
        contentColor = IntentScreen.secondaryText,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            label,
            modifier =
            Modifier.padding(
                horizontal = IntentSpacing.medium,
                vertical = IntentSpacing.small
            ),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Suppress("UnusedPrivateMember")
@IntentLightDarkPreview
@Composable
private fun WorkspaceListPreview() {
    IntentTheme {
        WorkspaceListScreen(
            WorkspaceListState(
                listOf(
                    Workspace(
                        "active",
                        "Android companion",
                        statusMessage = "The workspace list is ready for review.",
                        displayStatus = "pr_ready"
                    ),
                    Workspace(
                        "archived",
                        "Previous work",
                        status = "Archived",
                        displayStatus = "complete"
                    ),
                    Workspace(
                        "idle",
                        "A workspace with a longer title that wraps onto another line",
                        displayStatus = "idle"
                    )
                ),
                isLoading = false
            ),
            onRefresh = {},
            onWorkspaceSelected = {}
        )
    }
}

@Composable
private fun WorkspaceSearchButton(
    searching: Boolean,
    hasWorkspaces: Boolean,
    onToggle: () -> Unit
) {
    if (!searching && !hasWorkspaces) return
    Box(
        Modifier
            .size(SEARCH_BUTTON_SIZE)
            .clip(RoundedCornerShape(SEARCH_BUTTON_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (searching) {
                painterResource(
                    R.drawable.intent_close
                )
            } else {
                painterResource(R.drawable.ic_search)
            },
            if (searching) "Close workspace search" else "Search workspaces",
            Modifier.size(24.dp)
        )
    }
}

// iOS Constants.WorkspaceList, WorkspaceSearch and spacious WorkspaceRow spacing.
// The title paddings subtract Inter's space above the cap height and below the baseline.
private val TITLE_TOP_SPACING = 56.dp
private val TITLE_BOTTOM_SPACING = 56.dp
internal val RECENT_TOP_SPACING = 12.dp
private val SEARCH_BUTTON_SIZE = 56.dp
private val SEARCH_BUTTON_RADIUS = 20.dp
private val GROUP_TOP_SPACING = 32.dp
private val GROUP_BOTTOM_SPACING = 12.dp
private val ROW_ICON_SPACING = 20.dp
private val ROW_TITLE_SPACING = 10.dp

// The list runs edge to edge so swiped rows reach the screen edge; other items keep the page inset.
internal val PageInset = Modifier.padding(horizontal = IntentSpacing.page)
