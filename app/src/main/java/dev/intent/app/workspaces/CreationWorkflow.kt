package dev.intent.app.workspaces

import dev.intent.app.agents.NewAgentOptionsStore
import dev.intent.core.protocol.attachments.AttachmentUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CreationWorkflow(
    private val operations: WorkspaceOperations,
    initial: WorkspaceDraft = WorkspaceDraft(),
    private val persist: (WorkspaceDraft) -> Unit = {},
    private val upload: (
        String
    ) -> AttachmentUploadService = { error("Media upload is unavailable") }
) {
    private val mutableDraft = MutableStateFlow(initial)
    val draft = mutableDraft.asStateFlow()
    val repositories = CreationRepositoryStore(operations)
    val options = NewAgentOptionsStore(operations)
    fun update(value: WorkspaceDraft) {
        persist(value)
        mutableDraft.value = value
    }
    fun create(): String {
        if (draft.value.submitted == null) {
            val current = draft.value
            val catalog = options.load()
            require(catalog.providers.any { it.id == current.provider && it.selectable }) {
                "Choose a signed-in provider. Refresh agent choices."
            }
            require(catalog.specialists.any { it.id == current.specialist }) {
                "Choose a specialist. Refresh agent choices."
            }
            val models = options.models(current.provider)
            val selected = models.firstOrNull { it.id == current.model }
            require(current.model.isBlank() || selected != null) { "Choose an available model." }
            require(
                current.reasoning.isBlank() || current.reasoning in selected?.effortLevels.orEmpty()
            ) {
                "Choose an available reasoning level."
            }
        }
        val id = CreationSubmission(operations, upload, ::update).submit(draft.value)
        draft.value.media.forEach { java.io.File(it.path).delete() }
        update(WorkspaceDraft())
        return id
    }
}
