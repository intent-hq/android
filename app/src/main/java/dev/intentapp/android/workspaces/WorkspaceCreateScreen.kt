package dev.intentapp.android.workspaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intentapp.android.attachments.PhotoAttachmentStore
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.intentPageInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Route state, independent creation gates, and transport failure presentation.
@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
fun WorkspaceCreateScreen(
    workflow: CreationWorkflow,
    onBusy: (Boolean) -> Unit,
    onCreated: (String) -> Unit,
    photos: PhotoAttachmentStore? = null,
    onClose: () -> Unit = {}
) {
    val draft by workflow.draft.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var createReady by remember(draft.step) { mutableStateOf(false) }
    val submit: () -> Unit = {
        error = null
        if (draft.submitted == null) {
            val media = photos?.state?.value?.photos.orEmpty().mapNotNull { photo ->
                val description = photo.description ?: return@mapNotNull null
                val file = photo.file ?: return@mapNotNull null
                if (photo.placed == null) return@mapNotNull null
                CreationMedia(
                    photo.key,
                    file.path,
                    description.name,
                    description.mimeType,
                    description.image
                )
            }
            workflow.update(draft.copy(media = (draft.media + media).distinctBy { it.key }))
        }
        busy = true
        onBusy(true)
        scope.launch {
            try {
                val id =
                    withContext(NonCancellable + Dispatchers.IO) { workflow.create() }
                photos?.consume()
                onCreated(id)
            } catch (failure: Exception) {
                error = failure.message ?: "Creation is unconfirmed. Retry to recover."
            } finally {
                busy = false
                onBusy(false)
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(photos) {
        onDispose {
            photos?.state?.value?.photos?.filter { it.placed == null }?.forEach {
                photos.remove(it.key)
            }
        }
    }
    BackHandler(draft.step != CreationStep.REPOSITORY || busy) {
        if (!busy && draft.submitted == null) workflow.update(draft.back())
    }
    var next by remember { mutableStateOf<CreationNext?>(null) }
    val repositoryStep = draft.step == CreationStep.REPOSITORY
    // iOS CreateWorkspaceView: a titled page per step over Close/Back and Next/Create controls.
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).intentPageInsets(),
            verticalArrangement = Arrangement.spacedBy(CREATION_SECTION_SPACING)
        ) {
            when (draft.step) {
                CreationStep.REPOSITORY -> CreationRepositories(workflow, !busy) { next = it }
                CreationStep.BRANCH -> CreationBranches(workflow, !busy) { next = it }
                CreationStep.NEW_REPOSITORY -> CreationNewRepository(workflow, !busy) { next = it }
                CreationStep.AGENT -> {
                    CreationHeading("New Workspace")
                    CreationAgent(
                        workflow,
                        photos,
                        !busy && draft.submitted == null,
                        { createReady = it }
                    )
                }
            }
            error?.let { Text(it, color = IntentScreen.error) }
            if (draft.submitted != null) {
                Text("Saved creation attempt. Retry recovers the same workspace.")
            }
        }
        val action = when {
            draft.step != CreationStep.AGENT -> next
            draft.submitted == null -> CreationNext("Create", createReady && !busy, submit)
            else -> CreationNext(if (busy) "Creating…" else "Recover creation", !busy, submit)
        }
        CreationBottomBar(
            back = if (repositoryStep) {
                CreationNext("Close", !busy, onClose)
            } else {
                CreationNext("Back", !busy && draft.submitted == null) {
                    workflow.update(draft.back())
                }
            },
            backIsClose = repositoryStep,
            next = action
        )
    }
}

private val CREATION_SECTION_SPACING = 32.dp
