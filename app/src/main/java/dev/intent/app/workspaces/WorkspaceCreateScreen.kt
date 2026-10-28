package dev.intent.app.workspaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.design.IntentButton
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton
import dev.intent.app.design.intentPageInsets
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
    photos: PhotoAttachmentStore? = null
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
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).intentPageInsets(),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
        ) {
            Text("Workspace creation is under development.")
            if (draft.step != CreationStep.REPOSITORY && draft.submitted == null) {
                IntentTextButton(onClick = {
                    workflow.update(draft.back())
                }, enabled = !busy) { Text("Previous step") }
            }
            when (draft.step) {
                CreationStep.REPOSITORY -> CreationRepositories(workflow, !busy)
                CreationStep.BRANCH -> CreationBranches(workflow, !busy)
                CreationStep.NEW_REPOSITORY -> CreationNewRepository(workflow, !busy)
                CreationStep.AGENT -> CreationAgent(
                    workflow,
                    photos,
                    !busy && draft.submitted == null,
                    { createReady = it }
                )
            }
        }
        Column(Modifier.fillMaxWidth().intentPageInsets()) {
            error?.let { Text(it, color = IntentScreen.error) }
            if (draft.step == CreationStep.AGENT && draft.submitted == null) {
                IntentButton(
                    onClick = submit,
                    enabled = createReady && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create workspace")
                }
            }
            if (draft.submitted !=
                null
            ) {
                Text("Saved creation attempt. Retry recovers the same workspace.")
            }
            if (draft.step == CreationStep.AGENT && draft.submitted != null) {
                IntentButton(onClick = submit, enabled = !busy) {
                    Text(if (busy) "Creating…" else "Recover creation")
                }
            }
        }
    }
}
