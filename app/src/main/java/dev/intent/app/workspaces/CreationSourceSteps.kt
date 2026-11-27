@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // Show safe transport guidance.

package dev.intent.app.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.design.IntentButton
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod") // Repository search and Recent/More sections share one selection state.
@Composable
internal fun CreationRepositories(
    workflow: CreationWorkflow,
    enabled: Boolean,
    setNext: (CreationNext?) -> Unit
) {
    val draft by workflow.draft.collectAsState()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf(emptyList<CreationRepository>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query, retry) {
        loading = true
        error = null
        delay(CREATION_SEARCH_DELAY)
        try {
            rows = withContext(Dispatchers.IO) { workflow.repositories.repositories(query) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Repositories could not load. Check GitHub sign-in on the host, then retry."
        } finally {
            loading = false
        }
    }
    SideEffect {
        setNext(
            CreationNext("Next", enabled && draft.repository != null) {
                workflow.update(draft.copy(step = CreationStep.BRANCH))
            }
        )
    }
    CreationHeading("Select Project") {
        CreationIconAction(R.drawable.ic_add, "New repository", enabled) {
            workflow.update(draft.copy(step = CreationStep.NEW_REPOSITORY, newRepository = true))
        }
        CreationIconAction(R.drawable.ic_search, "Search repositories") { searching = !searching }
    }
    if (searching || query.isNotEmpty()) {
        IntentTextField(query, {
            query = it
        }, label = { Text("Search repositories") }, modifier = Modifier.fillMaxWidth())
    }
    if (loading) Text("Loading repositories…", color = IntentScreen.secondaryText)
    error?.let {
        Text(it, color = IntentScreen.error)
        IntentButton(onClick = { retry++ }) { Text("Retry repositories") }
    }
    if (!loading && error == null && rows.isEmpty()) {
        Text(
            if (query.isBlank()) {
                "No repositories available. Create one with + or check GitHub sign-in on the host."
            } else {
                "No repositories match your search. Try another name or owner/repository."
            },
            color = IntentScreen.secondaryText
        )
    }
    if (!loading && error == null) {
        RepositorySections(rows, draft.repository?.fullName, enabled) {
            workflow.update(draft.select(it))
        }
    }
}

/** iOS GitHubRepoPickerView: Recent and More sections of repository rows. */
@Composable
private fun RepositorySections(
    rows: List<CreationRepository>,
    selectedName: String?,
    enabled: Boolean,
    onSelect: (CreationRepository) -> Unit
) {
    listOf("Recent" to rows.filter { it.recent }, "More" to rows.filterNot { it.recent })
        .filter { it.second.isNotEmpty() }
        .forEach { (title, repositories) ->
            Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.page)) {
                CreationSectionLabel(title)
                Column {
                    repositories.forEachIndexed { index, repo ->
                        val selected = selectedName == repo.fullName
                        val next = repositories.getOrNull(index + 1)
                        CreationChoiceRow(
                            title = repo.name,
                            subtitle = repo.fullName,
                            selected = selected,
                            showsDivider =
                            next != null && !selected && selectedName != next.fullName,
                            enabled = enabled,
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_github),
                                    null,
                                    Modifier.size(24.dp),
                                    tint = IntentScreen.secondaryText
                                )
                            }
                        ) { onSelect(repo) }
                    }
                }
            }
        }
}

// Branch search, default badge, and selection share one load.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun CreationBranches(
    workflow: CreationWorkflow,
    enabled: Boolean,
    setNext: (CreationNext?) -> Unit
) {
    val draft by workflow.draft.collectAsState()
    val repository = draft.repository ?: return
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var defaultBranch by remember(repository) { mutableStateOf(repository.defaultBranch) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(repository, query, retry) {
        loading = true
        error = null
        delay(CREATION_SEARCH_DELAY)
        try {
            val choices = withContext(Dispatchers.IO) {
                workflow.repositories.branchChoices(repository, query)
            }
            rows = choices.branches
            defaultBranch = choices.defaultBranch
            if (query.isBlank() &&
                workflow.draft.value.baseRef !in rows
            ) {
                workflow.update(
                    workflow.draft.value.copy(baseRef = rows.firstOrNull().orEmpty())
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Branches could not load. Retry."
        } finally {
            loading =
                false
        }
    }
    SideEffect {
        setNext(
            CreationNext(
                "Next",
                enabled && !loading && error == null && draft.baseRef.isNotBlank()
            ) {
                workflow.update(draft.copy(step = CreationStep.AGENT))
            }
        )
    }
    CreationHeading("Select Branch") {
        CreationIconAction(R.drawable.ic_search, "Search branches") { searching = !searching }
    }
    CreationSourceSummary(draft)
    if (searching || query.isNotEmpty()) {
        IntentTextField(query, {
            query = it
        }, label = { Text("Search branches") }, modifier = Modifier.fillMaxWidth())
    }
    if (loading) Text("Loading branches…", color = IntentScreen.secondaryText)
    error?.let {
        Text(it, color = IntentScreen.error)
        IntentButton(onClick = { retry++ }) { Text("Retry branches") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.page)) {
        CreationSectionLabel("Branches")
        if (!loading && error == null && rows.isEmpty()) {
            Text(
                if (query.isBlank()) {
                    "This repository has no branches. Push an initial commit on the host, then reopen this step."
                } else {
                    "No branches match your search. Try a different branch name."
                },
                color = IntentScreen.secondaryText
            )
        }
        if (!loading && error == null) {
            Column {
                rows.forEachIndexed { index, branch ->
                    val selected = draft.baseRef == branch
                    val next = rows.getOrNull(index + 1)
                    CreationChoiceRow(
                        title = branch,
                        selected = selected,
                        showsDivider = next != null && !selected && draft.baseRef != next,
                        enabled = enabled,
                        trailing = if (branch == defaultBranch) {
                            { DefaultBranchBadge() }
                        } else {
                            null
                        }
                    ) { workflow.update(draft.copy(baseRef = branch)) }
                }
            }
        }
    }
}

@Composable
private fun DefaultBranchBadge() {
    Text(
        "Default",
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = IntentSpacing.medium, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = IntentScreen.secondaryText
    )
}

@Suppress("LongMethod") // Name, folder, preview and the async destination check.
@Composable
internal fun CreationNewRepository(
    workflow: CreationWorkflow,
    enabled: Boolean,
    setNext: (CreationNext?) -> Unit
) {
    val draft by workflow.draft.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val path = newRepositoryPath(draft.repositoryName, draft.parentPath)
    CreationHeading("New Repository")
    Text(
        "Create a Git repository on your connected computer. You can publish it to GitHub later.",
        color = IntentScreen.secondaryText
    )
    IntentTextField(draft.repositoryName, {
        workflow.update(draft.copy(repositoryName = it))
        error =
            null
    }, label = { Text("Repository name") }, enabled = enabled && !checking)
    IntentTextField(
        draft.parentPath,
        {
            workflow.update(draft.copy(parentPath = it))
            error = null
        },
        label = { Text("Parent folder") },
        enabled =
        enabled && !checking
    )
    Text(path ?: "Enter a valid name and a folder starting with / or ~/.")
    error?.let { Text(it) }
    SideEffect {
        setNext(
            CreationNext(
                if (checking) "Checking…" else "Next",
                enabled && !checking && path != null
            ) {
                checking = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            workflow.repositories.checkDestination(requireNotNull(path))
                        }
                        workflow.update(
                            draft.copy(
                                repositoryPath = requireNotNull(path),
                                baseRef = "",
                                step = CreationStep.AGENT
                            )
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error =
                            failure.message ?: "Could not check folder. Retry."
                    } finally {
                        checking = false
                    }
                }
            }
        )
    }
}

private const val CREATION_SEARCH_DELAY = 250L

@Composable
internal fun CreationSourceSummary(draft: WorkspaceDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.page)) {
        CreationSectionLabel("Source")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(IntentScreen.surface)
                .padding(IntentSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(IntentSpacing.page)
        ) {
            Text(if (draft.newRepository) "Folder" else "Repository", Modifier.weight(1f))
            Text(
                if (draft.newRepository) {
                    draft.repositoryPath
                } else {
                    draft.repository?.fullName.orEmpty()
                },
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis
            )
        }
    }
}
