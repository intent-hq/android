package dev.intent.app.notes

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.markdown.MarkdownContent
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteMetadata
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.TaskMetadata
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteTaskUiTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun plainTaskAppearsOnceBetweenItsSurroundingProse() {
        val task = mutableStateOf(NoteTaskRow(3, "Check the emulator", "todo"))
        compose.setContent {
            IntentTheme {
                NoteReaderScreen(
                    NoteReaderState(
                        note = Note(
                            "spec", "Plan",
                            content = "Before\n\n- [ ] Check the emulator\n\nAfter"
                        ),
                        tasks = listOf(task.value),
                        loading = false
                    ),
                    {},
                    onTaskStatus = { row, status -> task.value = row.copy(status = status) }
                )
            }
        }
        compose.onAllNodesWithText("Check the emulator").assertCountEquals(1)
        val before = compose.onNodeWithText("Before").fetchSemanticsNode().boundsInRoot
        val checkbox = compose.onNode(isToggleable()).fetchSemanticsNode().boundsInRoot
        val after = compose.onNodeWithText("After").fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom <= checkbox.top)
        assertTrue(checkbox.bottom <= after.top)
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.runOnIdle { assertEquals("done", task.value.status) }
    }

    @Test
    fun linkedTaskUsesLiveStatusAndNavigatesWithoutDuplicatingTheCheckbox() {
        val dark = mutableStateOf(false)
        val linked = mutableStateOf(
            Note("task", "Live task title", metadata = NoteMetadata(TaskMetadata("waiting")))
        )
        var selected: String? = null
        var updated: Pair<String, String>? = null
        compose.setContent {
            IntentTheme(dark.value) {
                Surface {
                    NoteReaderScreen(
                        NoteReaderState(
                            note = Note(
                                "spec", "Task plan",
                                content = "Before\n\n- [ ] [Old title](intent://local/task/task)\n\nAfter",
                                tags = listOf("planning")
                            ),
                            tasks = listOf(NoteTaskRow(3, "Old title", "todo", taskNoteId = "task")),
                            loading = false,
                            relatedNotes = listOf(linked.value)
                        ),
                        {},
                        onNoteSelected = { selected = it },
                        onLinkedTaskStatus = { id, status -> updated = id to status }
                    )
                }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("Live task title").performClick()
        compose.runOnIdle { assertEquals("task", selected) }
        compose.onNodeWithText("Waiting").performClick()
        compose.onNodeWithText("Complete").performClick()
        compose.runOnIdle {
            assertEquals("task" to "complete", updated)
            linked.value = linked.value.copy(metadata = NoteMetadata(TaskMetadata("complete")))
        }
        compose.onNodeWithText("Complete").assertIsDisplayed()
        listOf(false, true).forEach { night ->
            compose.runOnIdle { dark.value = night }
            compose.waitForIdle()
            screenshot("note-inline-linked-${if (night) "dark" else "light"}")
        }
    }

    @Test
    fun taskMetadataShowsAssignmentAndNavigableRelations() {
        var selectedNote: String? = null
        var selectedAgent: String? = null
        val task = TaskMetadata(
            "blocked", assignedAgentIds = listOf("agent-one"),
            dependsOn = listOf("dependency"), conflictsWith = listOf("conflict"),
            unmetDependsOn = listOf("dependency")
        )
        val related = listOf(
            Note("dependency", "Dependency", metadata = NoteMetadata(TaskMetadata("waiting"))),
            Note("conflict", "Conflicting work", metadata = NoteMetadata(TaskMetadata("in_progress"))),
            Note(
                "dependent", "Follow-up",
                metadata = NoteMetadata(TaskMetadata("not_started", dependsOn = listOf("task")))
            )
        )
        compose.setContent {
            IntentTheme {
                NoteReaderScreen(
                    NoteReaderState(
                        note = Note("task", "Assigned task", metadata = NoteMetadata(task)),
                        loading = false, relatedNotes = related
                    ),
                    {},
                    onNoteSelected = { selectedNote = it },
                    onAgentSelected = { selectedAgent = it }
                )
            }
        }
        compose.onNodeWithText("agent-one").performClick()
        compose.runOnIdle { assertEquals("agent-one", selectedAgent) }
        compose.onNodeWithText("Dependency · Waiting · Unmet dependency")
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals("dependency", selectedNote) }
        compose.onNodeWithText("Follow-up · Not started").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Conflicting work · In progress").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("conflict", selectedNote) }
        screenshot("note-task-relations-light")
    }

    @Test
    fun transcriptMarkdownKeepsReadOnlyCheckboxes() {
        compose.setContent { IntentTheme { MarkdownContent("- [ ] Read only") } }
        compose.onNodeWithText("Read only").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File)
            ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
