package dev.intent.app.git

import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.core.protocol.models.Workspace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

val LocalWorkspaceRepository = staticCompositionLocalOf<String?> { null }

internal fun repositoryLabel(workspace: Workspace?): String? {
    val name = workspace?.repositoryName?.takeIf { it.isNotBlank() }
    val owner = workspace?.repositoryOwner?.takeIf { it.isNotBlank() }
    if (name != null) return if (owner != null) "$owner/$name" else name
    return workspace?.repositoryPath?.trimEnd('/')?.substringAfterLast('/')?.takeIf {
        it.isNotBlank()
    }
}

internal fun GitState.isClean(): Boolean = !loading &&
    error == null &&
    status.files.isEmpty() &&
    !status.filesTruncated &&
    !status.hasUncommittedChanges &&
    !status.hasUntrackedFiles

internal fun commitDate(
    value: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String = try {
    Instant.parse(value).atZone(zone).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    )
} catch (_: DateTimeParseException) {
    "Date unavailable"
}
