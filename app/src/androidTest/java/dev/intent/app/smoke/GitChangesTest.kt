package dev.intent.app.smoke

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.git.WorkspaceGitRoute
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitChangesTest {
    @get:Rule val compose = createIntentComposeRule()
    private lateinit var server: ChangesServer

    @Test
    fun rootSwitchClearsOldRowsAndScopesReads() = connected {
        waitFor("M  primary.kt")
        compose.onNodeWithContentDescription("Repository").performClick()
        compose.onNodeWithText("Library").performClick()
        waitFor("M  secondary.kt")
        compose.onNodeWithText("M  primary.kt").assertDoesNotExist()
        compose.onNodeWithText("Library").assertIsDisplayed()
        val status = server.requests.last { it.method == "git.status" }
        assertEquals(JsonPrimitive("library"), status.params!!.jsonObject["gitRootId"])
        assertEquals(JsonPrimitive(true), status.params!!.jsonObject["forceRefresh"])
        assertTrue(server.requests.any { it.method == "git.commits" })
        compose.onNodeWithContentDescription("Repository").performClick()
        compose.onNodeWithText("Main repository").performClick()
        waitFor("M  primary.kt")
        assertTrue(
            server.requests.last { it.method == "git.status" }.params!!.jsonObject["gitRootId"] ==
                null
        )
    }

    @Test
    fun repeatedRepositoryNamesShowPathsAndSelectTheIntendedRoot() = connected {
        waitFor("M  primary.kt")
        compose.onNodeWithContentDescription("Repository").performClick()
        compose.onNodeWithText("/checkouts/one/android").assertIsDisplayed()
        compose.onNodeWithText("/checkouts/two/android").assertIsDisplayed()
        screenshot("changes-distinct-repositories")
        compose.onNodeWithText("/checkouts/two/android").performClick()
        waitFor("M  secondary.kt")
        compose.onNodeWithText("/checkouts/two/android").assertIsDisplayed()
        assertEquals(
            JsonPrimitive("android-two"),
            server.requests.last { it.method == "git.status" }.params!!.jsonObject["gitRootId"]
        )
    }

    @Test
    fun foldersCollapseIndependentlyAndFileBackKeepsTree() = connected {
        waitFor("M  primary.kt")
        screenshot("changes-tree")
        val folder = "changes.folder.unstaged.src"
        compose.onNodeWithTag(folder).performClick()
        compose.onNodeWithText("M  primary.kt").assertDoesNotExist()
        compose.onNodeWithText("M  staged.kt").assertIsDisplayed()
        compose.onNodeWithTag(folder).performClick()
        compose.onNodeWithText("M  primary.kt").performClick()
        waitFor("Unstaged diff")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("M  primary.kt")
        compose.onNodeWithTag(folder).assertIsDisplayed()
    }

    @Test
    fun commitOpensFilesAndHunksAndBackReturnsToChanges() = connected {
        waitFor("M  primary.kt")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Primary commit"))
        compose.onNodeWithText("Primary commit").performClick()
        waitFor("Commit changes")
        waitFor("+new line")
        compose.onNodeWithText("File (2)").assertIsDisplayed()
        screenshot("changes-commit-diff")
        compose.onNodeWithText("src/primary.kt").performClick()
        compose.onNodeWithText("other.kt (+1 −0)").performClick()
        waitFor("+other content")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Changes")
        compose.onNodeWithText("Commit changes").assertDoesNotExist()
        assertTrue(server.requests.any { it.method == "git.commitDetails" })
        assertTrue(server.requests.any { it.method == "git.branchDiff" })
    }

    @Test
    fun secondaryCommitUsesScopedContents() = connected {
        waitFor("M  primary.kt")
        compose.onNodeWithContentDescription("Repository").performClick()
        compose.onNodeWithText("Library").performClick()
        waitFor("M  secondary.kt")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Secondary commit"))
        compose.onNodeWithText("Secondary commit").performClick()
        waitFor("+root content")
        val reads = server.requests.filter {
            it.method == "git.showFile" ||
                it.method == "git.commitDetails"
        }
        assertTrue(reads.isNotEmpty())
        assertTrue(reads.all { it.params!!.jsonObject["gitRootId"] == JsonPrimitive("library") })
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Changes")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        directory.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun connected(test: () -> Unit) {
        RpcSession({
            ChangesServer(it).also { fixture -> server = fixture }
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            compose.setContent {
                IntentTheme(darkTheme = true) { Surface { WorkspaceGitRoute(rpc, "workspace") {} } }
            }
            test()
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(
            hasText(text)
        ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

private class ChangesServer(private val listener: WssTransport.Listener) : SessionTransport {
    val requests = CopyOnWriteArrayList<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun close() = Unit
    override fun send(message: JsonRpcMessage): Boolean {
        val request = message as JsonRpcMessage.Request
        requests.add(request)
        val root = request.params?.jsonObject?.get("gitRootId") != null
        val result = when (request.method) {
            "client.hello", "events.unsubscribe" -> "{}"
            "events.subscribe" -> """{"subscriptionId":"changes"}"""
            "gitRoot.list" ->
                """{"gitRoots":[
                {"id":"library","path":"/library","repoName":"Library"},
                {"id":"android-one","path":"/checkouts/one/android","repoName":"android"},
                {"id":"android-two","path":"/checkouts/two/android","repoName":"android"}]}"""
            "file-tracking.getChanges" -> """{"changes":[],"truncated":false}"""
            "git.status" -> """{"branch":"main","files":[
                {"path":"src/${if (root) "secondary" else "primary"}.kt","status":"M","staged":false},
                {"path":"src/staged.kt","status":"M","staged":true}]}"""
            "file-tracking.loadCommits" ->
                """{"commits":[
                {"hash":"primary","message":"Primary commit"}]}"""
            "git.commits" -> """{"items":[{"hash":"secondary","message":"Secondary commit"}]}"""
            "git.diffs" -> "[]"
            "git.commitDetails" ->
                """{"date":"","fileDetails":[
                {"path":"src/primary.kt","additions":1,"deletions":1},
                {"path":"other.kt","additions":1,"deletions":0}]}"""
            "git.branchDiff" ->
                """[
                {"file":"src/primary.kt","oldContent":"context\nold line\n","newContent":"context\nnew line\n"},
                {"file":"other.kt","oldContent":"","newContent":"other content\n"}]
            """
            "git.showFile" -> """{"content":"root content\n"}"""
            else -> error("Unexpected request: ${request.method}")
        }
        listener.onMessage(JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(result)))
        return true
    }
}
