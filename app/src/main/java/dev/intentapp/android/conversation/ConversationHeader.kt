package dev.intentapp.android.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.ChromeActionsMenu

/** iOS WorkspaceConversationHeader: 48dp controls on the page inset around a centered title. */
@Composable
internal fun ConversationHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: List<ChromeAction>
) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = IntentSpacing.page,
            vertical = IntentSpacing.medium
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(CONTROL_SIZE).semantics { contentDescription = "Back" }
        ) {
            Icon(painterResource(R.drawable.ic_arrow_left), contentDescription = null)
        }
        Column(
            Modifier.weight(1f).padding(horizontal = IntentSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentScreen.secondaryText,
                    maxLines = 1
                )
            }
        }
        if (actions.isEmpty()) {
            Spacer(Modifier.size(CONTROL_SIZE))
        } else {
            ChromeActionsMenu(actions)
        }
    }
}

private val CONTROL_SIZE = 48.dp
