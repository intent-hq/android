@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // Show safe transport guidance.

package dev.intentapp.android.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.agents.AgentModelChoice
import dev.intentapp.android.agents.NewAgentOptions
import dev.intentapp.android.attachments.PhotoAttachmentStore
import dev.intentapp.android.attachments.PhotoAttachments
import dev.intentapp.android.attachments.PhotoPicker
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentTextButton
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
    var modelError by remember { mutableStateOf<String?>(null) }
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
                            .orEmpty()
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
        modelError = null
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
            modelError = "Models could not load. Retry to use this provider."
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
    val efforts = models.firstOrNull { it.id == draft.model }?.effortLevels.orEmpty()
    val providers = options?.providers.orEmpty()
    val saved = draft.media.filter { item -> newMedia.none { it.key == item.key } }
    Column {
        CreationPromptCard(draft.prompt, { workflow.update(draft.copy(prompt = it)) }, enabled) {
            saved.forEach { media ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        media.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                    if (enabled) {
                        IconButton(onClick = {
                            workflow.update(
                                draft.copy(media = draft.media.filterNot { it.key == media.key })
                            )
                            File(media.path).delete()
                        }) {
                            Icon(
                                painterResource(R.drawable.intent_close),
                                "Remove ${media.name}",
                                Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (photos != null && enabled) {
                PhotoAttachments(photos)
                PhotoPicker(photos, Modifier.offset(x = (-12).dp))
            }
        }
        CreationSourceCard(draft, enabled, Modifier.padding(top = 8.dp)) {
            workflow.update(draft.back())
        }
        Column(
            Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.padding(bottom = 12.dp)) { CreationSectionLabel("Agent") }
            CreationOptionField(
                "Specialist",
                listOf(CreationMenuChoice("", "None")) +
                    options?.specialists.orEmpty().map { CreationMenuChoice(it.id, it.name) },
                draft.specialist,
                enabled && !loading
            ) { workflow.update(draft.copy(specialist = it)) }
            if (!loading && error == null && providers.none { it.selectable }) {
                Text("No providers are signed in", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Set up a provider in the Intent desktop app, then come back and try again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (providers.isNotEmpty()) {
                CreationOptionField(
                    "Provider",
                    providers.map { CreationMenuChoice(it.id, it.name, it.selectable) },
                    draft.provider,
                    enabled
                ) { workflow.update(draft.copy(provider = it, model = "", reasoning = "")) }
                CreationOptionField(
                    "Model",
                    listOf(CreationMenuChoice("", "Default")) +
                        models.map { CreationMenuChoice(it.id, it.name) },
                    draft.model,
                    enabled && !modelsLoading
                ) { workflow.update(draft.copy(model = it, reasoning = "")) }
                CreationOptionField(
                    "Reasoning",
                    listOf(CreationMenuChoice("", "Auto")) +
                        efforts.map { CreationMenuChoice(it, it) },
                    draft.reasoning,
                    enabled && !modelsLoading
                ) { workflow.update(draft.copy(reasoning = it)) }
                if (efforts.isEmpty()) {
                    Text(
                        "Choose a model with reasoning options to override Auto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText
                    )
                }
            }
            if (loading || modelsLoading) {
                Text(
                    "Loading agent choices…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentScreen.secondaryText
                )
            }
            (error ?: modelError)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                IntentTextButton(onClick = {
                    retry++
                }, enabled = enabled && !loading && !modelsLoading) { Text("Retry") }
            }
        }
    }
    val ready =
        !loading &&
            !modelsLoading &&
            error == null &&
            modelError == null &&
            options?.providers.orEmpty().any { it.id == draft.provider && it.selectable }
    androidx.compose.runtime.SideEffect { onReady(enabled && ready && additions?.ready != false) }
}
