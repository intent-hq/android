@file:Suppress("MatchingDeclarationName", "MagicNumber")

package dev.intent.app.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.Workspace

@Preview(name = "Light", showBackground = true, widthDp = 390, heightDp = 640)
@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class IntentLightDarkPreview

@IntentLightDarkPreview
@Composable
fun IntentAvatarPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize()) {
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
        Surface(Modifier.fillMaxSize()) {
            WorkspaceListScreen(
                WorkspaceListState(
                    listOf(
                        Workspace(
                            "android",
                            "Android companion",
                            statusMessage = "Design review is ready."
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
fun IntentAgentListPreview() {
    IntentTheme {
        Surface(Modifier.fillMaxSize()) {
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
