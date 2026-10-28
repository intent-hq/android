@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // Show safe transport guidance.

package dev.intent.app.workspaces

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.agents.AgentModelChoice
import dev.intent.app.agents.NewAgentOptions
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.attachments.PhotoAttachments
import dev.intent.app.attachments.PhotoPicker
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton
import dev.intent.app.design.IntentTextField
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun CreationAgent(
    workflow: CreationWorkflow,
    photos: PhotoAttachmentStore?,
    enabled: Boolean,
    onReady: (Boolean) -> Unit
) {
    val draft by workflow.draft.collectAsState()
    var options by remember { mutableStateOf<NewAgentOptions?>(null) }
    var models by remember { mutableStateOf(emptyList<AgentModelChoice>()) }
    var retry by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var modelsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(retry) {
        loading = true
        error = null
        try {
            val loaded = withContext(Dispatchers.IO) { workflow.options.load() }
            options = loaded
            if (draft.submitted == null) {
                val current = workflow.draft.value
                val provider =
                    current.provider.takeIf { id ->
                        loaded.providers.any {
                            it.id == id &&
                                it.selectable
                        }
                    }
                        ?: loaded.defaultProvider?.id.orEmpty()
                workflow.update(
                    current.copy(
                        provider = provider,
                        model = current.model.takeIf { provider == current.provider }.orEmpty(),
                        reasoning = current.reasoning.takeIf {
                            provider == current.provider
                        }.orEmpty(),
                        specialist =
                        current.specialist.takeIf { id -> loaded.specialists.any { it.id == id } }
                            ?: loaded.defaultSpecialist?.id.orEmpty()
                    )
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error =
                "Agent choices could not load. Check provider sign-in on the host, then retry."
        } finally {
            loading =
                false
        }
    }
    LaunchedEffect(draft.provider, retry) {
        models = emptyList()
        modelsLoading = true
        try {
            if (draft.provider.isNotBlank()) {
                models =
                    withContext(Dispatchers.IO) { workflow.options.models(draft.provider) }
            }
            val current = workflow.draft.value
            if (current.submitted == null &&
                current.model.isNotBlank() &&
                models.none { it.id == current.model }
            ) {
                workflow.update(current.copy(model = "", reasoning = ""))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Models could not load. Retry to use this provider."
        } finally {
            modelsLoading =
                false
        }
    }
    val additions = photos?.state?.collectAsState()?.value
    val newMedia = additions?.photos.orEmpty().filter { it.placed != null }.map { photo ->
        val description = requireNotNull(photo.description)
        CreationMedia(
            photo.key,
            requireNotNull(photo.file).path,
            description.name,
            description.mimeType,
            description.image
        )
    }
    val repositoryLabel =
        draft.repository?.fullName.takeUnless { draft.newRepository } ?: draft.repositoryPath
    if (repositoryLabel.isNotBlank()) Text(repositoryLabel)
    if (!draft.newRepository && draft.baseRef.isNotBlank()) Text(draft.baseRef)
    IntentTextField(draft.title, {
        workflow.update(draft.copy(title = it))
    }, label = { Text("Title (optional)") }, enabled = enabled)
    IntentTextField(draft.prompt, {
        workflow.update(draft.copy(prompt = it))
    }, label = { Text("Initial prompt") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
    draft.media.filter { saved -> newMedia.none { it.key == saved.key } }.forEach { media ->
        Text(media.name)
        if (enabled) {
            IntentTextButton(onClick = {
                workflow.update(draft.copy(media = draft.media.filterNot { it.key == media.key }))
                File(media.path).delete()
            }) { Text("Remove ${media.name}") }
        }
    }
    if (photos != null && enabled) {
        PhotoPicker(photos)
        PhotoAttachments(photos)
    }
    var liveKeys by remember(photos) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(additions) {
        if (workflow.draft.value.submitted == null) {
            val activeKeys = additions?.photos.orEmpty().map { it.key }.toSet()
            val removed = liveKeys - activeKeys
            val current = workflow.draft.value
            val persisted = (
                current.media.filterNot {
                    it.key in removed
                } + newMedia
                ).distinctBy { it.key }
            if (persisted != current.media) workflow.update(current.copy(media = persisted))
            liveKeys = activeKeys
        }
    }
    if (loading || modelsLoading) Text("Loading agent choices…")
    error?.let { Text(it) }
    IntentTextButton(onClick = {
        retry++
    }, enabled = enabled && !loading && !modelsLoading) { Text("Retry agent choices") }
    IntentSection("Specialist") {
        options?.specialists.orEmpty().forEach { choice ->
            FilterChip(draft.specialist == choice.id, {
                workflow.update(draft.copy(specialist = choice.id))
            }, label = { Text(choice.name) }, enabled = enabled)
        }
    }
    IntentSection("Provider") {
        options?.providers.orEmpty().forEach { choice ->
            FilterChip(
                draft.provider == choice.id,
                {
                    workflow.update(draft.copy(provider = choice.id, model = "", reasoning = ""))
                },
                label = { Text(choice.name) },
                enabled =
                enabled && choice.selectable
            )
        }
    }
    IntentSection("Model") {
        FilterChip(draft.model.isEmpty(), {
            workflow.update(draft.copy(model = "", reasoning = ""))
        }, label = { Text("Host default") }, enabled = enabled)
        models.forEach { model ->
            FilterChip(draft.model == model.id, {
                workflow.update(draft.copy(model = model.id, reasoning = ""))
            }, label = { Text(model.name) }, enabled = enabled)
        }
    }
    val efforts = models.firstOrNull { it.id == draft.model }?.effortLevels.orEmpty()
    if (efforts.isNotEmpty()) {
        IntentSection("Reasoning") {
            (listOf("") + efforts).forEach { effort ->
                FilterChip(draft.reasoning == effort, {
                    workflow.update(draft.copy(reasoning = effort))
                }, label = { Text(effort.ifBlank { "Default reasoning" }) }, enabled = enabled)
            }
        }
    }
    val ready =
        !loading &&
            !modelsLoading &&
            error == null &&
            options?.providers.orEmpty().any { it.id == draft.provider && it.selectable }
    androidx.compose.runtime.SideEffect { onReady(enabled && ready && additions?.ready != false) }
}
