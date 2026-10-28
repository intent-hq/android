package dev.intent.app.workspaces

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
enum class CreationStep { REPOSITORY, BRANCH, NEW_REPOSITORY, AGENT }

@Serializable
data class CreationRepository(val owner: String, val name: String, val defaultBranch: String = "") {
    val fullName: String get() = "$owner/$name"
}

@Serializable
data class WorkspaceDraft(
    val title: String = "",
    val repositoryPath: String = "",
    val baseRef: String = "",
    val prompt: String = "",
    val provider: String = "",
    val model: String = "",
    val specialist: String = "",
    val reasoning: String = "",
    val step: CreationStep = CreationStep.REPOSITORY,
    val repository: CreationRepository? = null,
    val newRepository: Boolean = false,
    val repositoryName: String = "",
    val parentPath: String = "~/Developer",
    val requestKey: String = UUID.randomUUID().toString(),
    val submitted: JsonObject? = null,
    val workspaceId: String = "",
    val agentId: String = "",
    val messageId: String = "",
    val media: List<CreationMedia> = emptyList()
) {
    fun select(repository: CreationRepository): WorkspaceDraft = copy(
        repository = repository,
        baseRef = "",
        newRepository = false,
        repositoryPath = "",
        requestKey = UUID.randomUUID().toString()
    )

    fun back(): WorkspaceDraft = copy(
        step = when (step) {
            CreationStep.AGENT ->
                if (newRepository) CreationStep.NEW_REPOSITORY else CreationStep.BRANCH
            else -> CreationStep.REPOSITORY
        }
    )

    // Optional wire fields are independent; validate source and agent separately.
    @Suppress("CyclomaticComplexMethod", "ComplexCondition")
    fun params(deferPrompt: Boolean = false): JsonObject = buildJsonObject {
        require(model.isBlank() || provider.isNotBlank()) { "Choose a provider for the model" }
        if (title.isNotBlank()) put("title", title.trim())
        repository?.takeUnless { newRepository }?.let {
            require(baseRef.isNotBlank()) { "Choose a branch" }
            put("githubUrl", "https://github.com/${it.fullName}")
        }
        if (repositoryPath.isNotBlank()) put("repositoryPath", repositoryPath.trim())
        if (newRepository) {
            require(repositoryPath == newRepositoryPath(repositoryName, parentPath))
            put("isNewRepo", true)
        }
        if (baseRef.isNotBlank() && !newRepository) put("baseRef", baseRef.trim())
        put("idempotencyKey", requestKey)
        if (prompt.isNotBlank() ||
            provider.isNotBlank() ||
            specialist.isNotBlank() ||
            deferPrompt
        ) {
            put(
                "initialAgent",
                buildJsonObject {
                    if (!deferPrompt && prompt.isNotBlank()) put("prompt", prompt.trim())
                    if (provider.isNotBlank()) put("provider", provider)
                    if (model.isNotBlank()) put("model", model)
                    if (specialist.isNotBlank()) put("specialist", specialist)
                    if (reasoning.isNotBlank()) put("reasoningEffort", reasoning)
                }
            )
        }
    }
}

// Mirror the host-path validation used by iOS before checking the destination.
@Suppress("ComplexCondition", "ReturnCount")
fun newRepositoryPath(name: String, parent: String): String? {
    val cleanName = name.trim()
    val cleanParent = parent.trim()
    val invalid = "/\\<>:\"|?*"
    if (cleanName.isEmpty() ||
        cleanName.all { it == '.' } ||
        cleanName.any { it in invalid || it.isISOControl() } ||
        cleanName.toByteArray().size > MAX_REPOSITORY_NAME_BYTES
    ) {
        return null
    }
    if (!(cleanParent == "~" || cleanParent.startsWith("~/") || cleanParent.startsWith('/')) ||
        cleanParent.any { it == '\\' || it.isISOControl() } ||
        cleanParent.split('/').any { it == "." || it == ".." }
    ) {
        return null
    }
    val prefix = if (cleanParent.startsWith('/')) "/" else ""
    return prefix +
        (cleanParent.split('/').filter { it.isNotEmpty() } + cleanName).joinToString("/")
}

private const val MAX_REPOSITORY_NAME_BYTES = 255
