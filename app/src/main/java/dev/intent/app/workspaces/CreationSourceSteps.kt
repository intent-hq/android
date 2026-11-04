@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // Show safe transport guidance.

package dev.intent.app.workspaces

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentButton
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod") // Repository search and owner groups share one selection state.
@Composable
internal fun CreationRepositories(workflow: CreationWorkflow, enabled: Boolean) {
    val draft by workflow.draft.collectAsState()
    var query by remember { mutableStateOf("") }
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
    Text("Choose a repository", style = MaterialTheme.typography.titleMedium)
    IntentButton(onClick = {
        workflow.update(draft.copy(step = CreationStep.NEW_REPOSITORY, newRepository = true))
    }, enabled = enabled) { Text("New repository") }
    IntentTextField(query, {
        query = it
    }, label = { Text("Search repositories") }, modifier = Modifier.fillMaxWidth())
    if (loading) Text("Loading repositories…")
    error?.let {
        Text(it, color = IntentScreen.error)
        IntentButton(onClick = { retry++ }) { Text("Retry repositories") }
    }
    if (!loading && error == null && rows.isEmpty()) {
        Text(
            if (query.isBlank()) {
                "No repositories available. Create one above or check GitHub sign-in on the host."
            } else {
                "No repositories match your search. Try another name or owner/repository."
            },
            color = IntentScreen.secondaryText
        )
    }
    if (!loading && error == null) {
        rows.groupBy { it.owner }.forEach { (owner, repositories) ->
            IntentSection(owner) {
                repositories.forEach { repo ->
                    FilterChip(selected = draft.repository?.fullName == repo.fullName, onClick = {
                        workflow.update(draft.select(repo))
                    }, label = { Text(repo.name) }, enabled = enabled)
                }
            }
        }
    }
    IntentButton(
        onClick = { workflow.update(draft.copy(step = CreationStep.BRANCH)) },
        enabled =
        enabled && draft.repository != null
    ) { Text("Choose branch") }
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // Branch search, default badge, and selection share one load.
@Composable
internal fun CreationBranches(workflow: CreationWorkflow, enabled: Boolean) {
    val draft by workflow.draft.collectAsState()
    val repository = draft.repository ?: return
    var query by remember { mutableStateOf("") }
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
    CreationSourceSummary(draft)
    IntentTextField(query, {
        query = it
    }, label = { Text("Search branches") }, modifier = Modifier.fillMaxWidth())
    if (loading) Text("Loading branches…")
    error?.let {
        Text(it, color = IntentScreen.error)
        IntentButton(onClick = { retry++ }) { Text("Retry branches") }
    }
    IntentSection("Branches") {
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
            rows.forEach { branch ->
                FilterChip(
                    selected = draft.baseRef == branch,
                    onClick = { workflow.update(draft.copy(baseRef = branch)) },
                    label = { Text(branch) },
                    trailingIcon = if (branch == defaultBranch) {
                        {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = IntentScreen.secondaryText
                            ) { Text("Default") }
                        }
                    } else {
                        null
                    },
                    enabled = enabled
                )
            }
        }
    }
    IntentButton(
        onClick = { workflow.update(draft.copy(step = CreationStep.AGENT)) },
        enabled =
        enabled && !loading && error == null && draft.baseRef.isNotBlank()
    ) {
        Text("Configure agent")
    }
}

@Composable
internal fun CreationNewRepository(workflow: CreationWorkflow, enabled: Boolean) {
    val draft by workflow.draft.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val path = newRepositoryPath(draft.repositoryName, draft.parentPath)
    Text("Create a Git repository on your connected computer. Publish it to GitHub later.")
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
    IntentButton(onClick = {
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
    }, enabled = enabled && !checking && path != null) {
        Text(if (checking) "Checking folder…" else "Configure agent")
    }
}

private const val CREATION_SEARCH_DELAY = 250L

@Composable
internal fun CreationSourceSummary(draft: WorkspaceDraft) {
    IntentSection("Source") {
        Text(
            if (draft.newRepository) draft.repositoryPath else draft.repository?.fullName.orEmpty(),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            if (draft.newRepository) {
                "New repository on this host"
            } else {
                "Branch: ${draft.baseRef.ifBlank { "Choose a branch" }}"
            },
            color = IntentScreen.secondaryText
        )
    }
}
