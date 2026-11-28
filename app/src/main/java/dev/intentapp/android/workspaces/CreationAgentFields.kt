package dev.intentapp.android.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen

/** iOS CreateWorkspacePrompt: a card with a multiline editor over the attachment row. */
@Composable
internal fun CreationPromptCard(
    text: String,
    onText: (String) -> Unit,
    enabled: Boolean,
    footer: @Composable ColumnScope.() -> Unit
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = IntentScreen.text)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FIELD_RADIUS))
            .background(IntentScreen.surface)
            .padding(top = CARD_PADDING, start = CARD_PADDING, end = CARD_PADDING, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        BasicTextField(
            text,
            onText,
            Modifier
                .fillMaxWidth()
                .heightIn(min = PROMPT_MIN_HEIGHT)
                .semantics { contentDescription = "Initial prompt" },
            enabled = enabled,
            textStyle = style,
            cursorBrush = SolidColor(IntentScreen.text),
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            "What should we work on?",
                            style = style,
                            color = IntentScreen.secondaryText
                        )
                    }
                    field()
                }
            }
        )
        footer()
    }
}

/** iOS CreateWorkspaceSourceCard: folder, repository and branch; tapping goes back to edit. */
@Composable
internal fun CreationSourceCard(
    draft: WorkspaceDraft,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit
) {
    val name = if (draft.newRepository) {
        draft.repositoryName.ifBlank { draft.repositoryPath }
    } else {
        draft.repository?.fullName.orEmpty()
    }
    val branch = draft.baseRef.ifBlank { draft.repository?.defaultBranch.orEmpty() }
    val secondary = IntentScreen.secondaryText
    val foreground = IntentScreen.text
    val detail = if (draft.newRepository || branch.isBlank()) {
        buildAnnotatedString {
            append(if (draft.newRepository) NEW_REPOSITORY_DETAIL else "Default branch")
        }
    } else {
        buildAnnotatedString {
            append("off branch ")
            withStyle(SpanStyle(color = foreground)) { append(branch) }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(FIELD_RADIUS))
            .background(IntentScreen.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onEdit)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(CARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(20.dp), tint = secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = foreground,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.titleSmall,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            Modifier.size(16.dp),
            tint = foreground
        )
    }
}

/** iOS AgentOptionField: a 106dp label beside a select menu. */
@Composable
internal fun CreationOptionField(
    label: String,
    choices: List<CreationMenuChoice>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.width(FIELD_LABEL_WIDTH).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyLarge
        )
        CreationSelectMenu(label, choices, selected, enabled, onSelect, Modifier.weight(1f))
    }
}

@Suppress("LongParameterList") // Menu label, choices, selection and placement.
@Composable
private fun CreationSelectMenu(
    label: String,
    choices: List<CreationMenuChoice>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val title =
        choices.firstOrNull { it.id == selected }?.label ?: choices.firstOrNull()?.label.orEmpty()
    val active = enabled && choices.isNotEmpty()
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CONTROL_HEIGHT)
                .clip(RoundedCornerShape(FIELD_RADIUS))
                .background(IntentScreen.surface)
                .clickable(enabled = active, role = Role.DropdownList) { expanded = true }
                .semantics {
                    contentDescription = label
                    stateDescription = title
                }
                .padding(horizontal = CONTROL_PADDING, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(ROW_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = if (active) IntentScreen.text else IntentScreen.secondaryText
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                null,
                Modifier.size(16.dp),
                tint = color
            )
        }
        CreationMenuItems(choices, selected, expanded && active, { expanded = false }, onSelect)
    }
}

@Composable
private fun CreationMenuItems(
    choices: List<CreationMenuChoice>,
    selected: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    DropdownMenu(
        expanded,
        onDismiss,
        shape = RoundedCornerShape(FIELD_RADIUS),
        containerColor = IntentScreen.surface
    ) {
        choices.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label, style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    onDismiss()
                    onSelect(choice.id)
                },
                enabled = choice.enabled,
                trailingIcon = if (choice.id == selected) {
                    { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

private val CARD_PADDING = 24.dp
private val CONTROL_PADDING = 20.dp
private val CONTROL_HEIGHT = 48.dp
private val FIELD_RADIUS = 20.dp
private val ROW_SPACING = 8.dp
private val FIELD_LABEL_WIDTH = 106.dp
private val PROMPT_MIN_HEIGHT = 80.dp
private const val DISABLED_ALPHA = 0.3f

private const val NEW_REPOSITORY_DETAIL = "New repository on your connected computer"
