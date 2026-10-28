@file:Suppress("MatchingDeclarationName", "MagicNumber")

package dev.intent.app.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.connection.PairingScreen
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationSendState
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.Workspace

@Preview(name = "Light", showBackground = true, widthDp = 390, heightDp = 800)
@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class IntentLightDarkPreview

@IntentLightDarkPreview
@Composable
fun IntentAvatarPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
            Column(
                Modifier.padding(IntentSpacing.content),
                verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)
            ) {
                Text("Agent avatars", style = MaterialTheme.typography.headlineMedium)
                AgentAvatarState.entries.forEach { state ->
                    Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
                        AgentAvatar("agent-1", specialist = "implementor", state = state)
                        Text(state.label)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.small)) {
                    AgentAvatarDesign.entries.take(7).forEach { design ->
                        AgentAvatar("agent", icon = design.wireName)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.small)) {
                    listOf("", "AGENT-123", "agent-5", "agent-1", "agent-123", "a").forEach { id ->
                        AgentAvatar(id)
                    }
                }
            }
        }
    }
}

@IntentLightDarkPreview
@Composable
fun IntentWorkspacePreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
            WorkspaceListScreen(
                WorkspaceListState(
                    listOf(
                        Workspace(
                            "android",
                            "Android companion",
                            statusMessage = "Design review is ready.",
                            displayStatus = "pr_ready"
                        ),
                        Workspace(
                            "mobile",
                            "Refine the mobile workspace components and typography",
                            statusMessage = "Applying the new component styles across the app.",
                            displayStatus = "working"
                        ),
                        Workspace(
                            "question",
                            "Improve connection setup",
                            statusMessage = "Choose which desktop to connect.",
                            displayStatus = "question"
                        ),
                        Workspace(
                            "idle",
                            "Explore new ideas",
                            statusMessage = "Ready when you are."
                        )
                    ),
                    isLoading = false
                ),
                onRefresh = {},
                onWorkspaceSelected = {}
            )
        }
    }
}

@IntentLightDarkPreview
@Composable
fun IntentComponentsPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
            Column(
                Modifier.padding(IntentSpacing.page),
                verticalArrangement = Arrangement.spacedBy(IntentSpacing.content)
            ) {
                Text("Mobile components", style = MaterialTheme.typography.headlineMedium)
                Text("Inter for clear, focused work.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
                    IntentButton({}) { Text("Continue") }
                    IntentButton({}, enabled = false) { Text("Continue") }
                }
                IntentTextButton({}) { Text("Secondary action") }
                var tab by remember { mutableStateOf(0) }
                IntentTabs(listOf("Agents", "Context", "Changes"), tab, { tab = it })
                var text by remember { mutableStateOf("") }
                IntentTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Workspace name") },
                    singleLine = true
                )
                Row {
                    Checkbox(true, {})
                    Checkbox(false, {})
                    Checkbox(true, {}, enabled = false)
                }
                ConversationComposer(ConversationSendState(), { true }, {}, { _, _ -> true })
                Text(
                    "Metadata · 12sp",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
}

@IntentLightDarkPreview
@Composable
fun IntentPairingPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
            PairingScreen(false, {}, {})
        }
    }
}

@IntentLightDarkPreview
@Composable
fun IntentAgentListPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
            AgentListScreen(
                AgentListState(
                    listOf(Agent("agent-1", "Implementor", status = "active")),
                    isLoading = false
                ),
                onRefresh = {},
                onAgentSelected = {}
            )
        }
    }
}
