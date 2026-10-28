package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.attachments.PhotoAttachmentSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.CreationDraftStore
import dev.intent.app.workspaces.CreationStep
import dev.intent.app.workspaces.CreationWorkflow
import dev.intent.app.workspaces.WorkspaceCreateScreen
import dev.intent.app.workspaces.WorkspaceDraft
import dev.intent.app.workspaces.WorkspaceOperations
import java.io.Closeable
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCreateTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun repositoryBranchAndAgentStepsCreateWithReasoning() {
        val fake = CreateChoices()
        val workflow = CreationWorkflow(fake)
        var created = ""
        compose.setContent { IntentTheme { WorkspaceCreateScreen(workflow, {}, { created = it }) } }
        waitText("intent-hq/android")
        screenshot("creation-repositories")
        compose.onNodeWithText("intent-hq/android").performClick()
        tap("Choose branch")
        waitText("main")
        screenshot("creation-branches")
        tap("Configure agent")
        waitText("Provider A")
        tap("Model A")
        tap("high")
        compose.onNodeWithText(
            "Initial prompt"
        ).performScrollTo().performTextInput("Build a feature")
        tap("Create workspace")
        compose.waitUntil { created == "created" }
        assertEquals(
            "high",
            fake.created!!.getValue("initialAgent").let {
                it as JsonObject
            }["reasoningEffort"]?.let { it.toString().trim('"') }
        )
    }

    @Test fun newRepositoryRejectsExistingFolderThenRecovers() {
        val fake = CreateChoices().apply { exists = true }
        val workflow = CreationWorkflow(fake)
        compose.setContent { IntentTheme { WorkspaceCreateScreen(workflow, {}, {}) } }
        tap("New repository")
        compose.onNodeWithText("Repository name").performTextInput("new-project")
        tap("Configure agent")
        waitText("This folder exists or could not be checked. Choose another name or retry.")
        screenshot("creation-new-repository-error")
        fake.exists = false
        tap("Configure agent")
        waitText("Provider A")
        tap("Create workspace")
        compose.waitUntil { fake.created != null }
        assertEquals("true", fake.created!!["isNewRepo"].toString())
    }

    @Test fun providerFailureRetryAndDurableDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistence = CreationDraftStore(context, UUID.randomUUID().toString())
        val initial = WorkspaceDraft(step = CreationStep.AGENT, prompt = "Keep my draft")
        persistence.save(initial)
        val fake = CreateChoices().apply { failProviders = true }
        val workflow = CreationWorkflow(fake, persistence.load(), persistence::save)
        compose.setContent { IntentTheme { WorkspaceCreateScreen(workflow, {}, {}) } }
        waitText("Agent choices could not load. Check provider sign-in on the host, then retry.")
        compose.onNodeWithText("Create workspace").assertIsNotEnabled()
        fake.failProviders = false
        tap("Retry agent choices")
        waitText("Provider A")
        compose.waitUntil { workflow.draft.value.provider == "a" }
        compose.onNodeWithText("Create workspace").assertIsEnabled()
        screenshot("creation-provider-recovery")
        assertEquals("Keep my draft", persistence.load().prompt)
        assertEquals("a", persistence.load().provider)
    }

    @Test fun mediaPromptStagesBeforeCreateAndSendsAfterPlacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "creation-test.txt").apply { writeText("test") }
        val photos = PhotoAttachmentStore(object : PhotoAttachmentSource {
            override fun prepare(uri: String) =
                java.util.concurrent.CompletableFuture.completedFuture(file)
            override fun describe(file: File) = dev.intent.app.attachments.AttachmentDescription(
                "creation-test.txt",
                "text/plain",
                false
            )
            override fun upload(
                file: File,
                key: String,
                progress: (dev.intent.core.protocol.attachments.TransferProgress) -> Unit
            ) = java.util.concurrent.CompletableFuture.completedFuture(
                dev.intent.core.protocol.attachments.PlacedAttachment(
                    key,
                    "creation-test.txt",
                    file.path,
                    file.length(),
                    "text/plain"
                )
            )
        })
        val fake = CreateChoices()
        val workflow =
            CreationWorkflow(
                fake,
                WorkspaceDraft(
                    step = CreationStep.AGENT,
                    prompt = "Read this"
                ),
                upload = { workspace ->
                    dev.intent.core.protocol.attachments.AttachmentUploadService({ method, params ->
                        assertEquals("created", workspace)
                        assertEquals("file.placeAttachment", method)
                        java.util.concurrent.CompletableFuture.completedFuture(
                            Json.parseToJsonElement(
                                """
{"ok":true,
"attachmentId":"placed",
"fileName":"creation-test.txt",
"path":".intent/attachments/test.txt",
"size":4,
"mimeType":"text/plain"}
"""
                            ) as JsonObject
                        )
                    }, workspace)
                }
            )
        var created = ""
        compose.setContent {
            IntentTheme { WorkspaceCreateScreen(workflow, {}, { created = it }, photos) }
        }
        waitText("Provider A")
        compose.runOnIdle { photos.addFiles(listOf("fixture")) }
        waitText("creation-test.txt")
        compose.waitUntil { workflow.draft.value.media.size == 1 }
        compose.onNodeWithText("creation-test.txt").performScrollTo()
        screenshot("creation-media-prompt")
        tap("Create workspace")
        compose.waitUntil(10_000) { created == "created" }
        assertEquals(null, (fake.created!!["initialAgent"] as JsonObject)["prompt"])
        assertEquals("Read this", fake.sent!!["content"].toString().trim('"'))
        assertEquals(0, workflow.draft.value.media.size)
    }

    private fun tap(text: String) {
        val node = compose.onNodeWithText(text)
        if (text != "Create workspace") node.performScrollTo()
        node.performClick()
    }
    private fun waitText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        directory.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
    try {
        block(this)
    } finally {
        recycle()
    }
}

private class CreateChoices : WorkspaceOperations {
    @Volatile var exists = false

    @Volatile var failProviders = false

    @Volatile var created: JsonObject? = null

    @Volatile var sent: JsonObject? = null
    override fun request(method: String, params: JsonObject): JsonObject {
        if (method == "providers.catalog" && failProviders) error("Disconnected")
        if (method == "workspace.create") created = params
        if (method == "agent.sendMessage") sent = params
        return Json.parseToJsonElement(
            when (method) {
                "agent.sendMessage" -> """{"success":true,"messageId":"message"}"""
                "repo.list" -> """{"repos":[]}"""
                "github.repos.list", "github.repos.search" ->
                    """
{"repos":[{"owner":"intent-hq",
"name":"android",
"defaultBranch":"main"}]}
"""
                "github.branches.list" -> """{"branches":["main","feature"]}"""
                "host.directoryStatus" -> """{"exists":$exists}"""
                "providers.catalog" ->
                    """
{"providers":[{"id":"a",
"name":"Provider A",
"visible":true,
"canBeDisabled":false}]}
"""
                "settings.get" -> """{"path":"providers.enabled","value":null}"""
                "host.providerAuthStatus" -> """{"providers":[{"id":"a","authenticated":true}]}"""
                "specialist.list" -> """{"specialists":[{"id":"b","name":"Specialist B"}]}"""
                "models.list" ->
                    """
{"models":[{"id":"model",
"name":"Model A",
"provider":"a",
"effortLevels":["high",
"low"]}]}
"""
                "workspace.create" ->
                    """
{"workspace":{"id":"created"},
"initialAgent":{"id":"agent"}}
"""
                else -> error("Unexpected request: $method")
            }
        ) as JsonObject
    }
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable =
        Closeable {}
}
