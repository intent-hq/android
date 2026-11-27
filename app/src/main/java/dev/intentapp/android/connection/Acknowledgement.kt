package dev.intentapp.android.connection

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.intent.tailcat.Tailcat
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen

internal data class Acknowledgement(
    val name: String,
    val licenseTitle: String,
    val url: String?,
    val licenseText: String
)

internal fun bundledAcknowledgements(context: Context): List<Acknowledgement> {
    fun raw(id: Int) = context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    return listOfNotNull(
        Acknowledgement(
            "Inter",
            "SIL Open Font License 1.1",
            "https://github.com/rsms/inter",
            raw(R.raw.inter_license)
        ),
        Acknowledgement(
            "Mermaid renderer",
            "Third-party notices",
            null,
            context.assets.open("mermaid/NOTICE.txt").bufferedReader().use { it.readText() }
        ),
        if (Tailcat.available) {
            Acknowledgement(
                "Tailcat",
                "BSD 3-Clause License",
                "https://github.com/tailscale/tailcat",
                raw(R.raw.tailcat_license)
            )
        } else {
            null
        }
    )
}

private const val WRAPPED_LINE_LENGTH = 60
private val ruleLine = Regex("""^[-=_*\s]+$""")
private val listItem = Regex("""^\s*(\d+[.)]|[-*•])\s""")

/** Joins lines hard-wrapped at a fixed column so text reflows to the screen width. */
internal fun reflowLicense(text: String, title: String): String {
    val lines = text.trimEnd().lines()
        .filterNot { ruleLine.matches(it) && it.isNotBlank() }
        .dropWhile { it.isBlank() || it.trim() == title }
    return buildString {
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)
            val continues = previous != null &&
                previous.length >= WRAPPED_LINE_LENGTH &&
                line.isNotBlank() &&
                !ruleLine.matches(previous) &&
                !ruleLine.matches(line) &&
                !listItem.containsMatchIn(line)
            if (index > 0) append(if (continues) " " else "\n")
            append(if (continues) line.trim() else line.trimEnd())
        }
    }
}

// iOS AcknowledgementsView: an eyebrow name over a bordered card of license details.
private val CardRadius = 12.dp
private val CardPadding = 16.dp
private val LicenseSpacing = 8.dp

@Composable
fun AcknowledgementsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = remember(context) {
        bundledAcknowledgements(context).map {
            it.copy(licenseText = reflowLicense(it.licenseText, it.licenseTitle))
        }
    }
    LazyColumn(
        modifier.testTag("settings.acknowledgements"),
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(SettingsSpacing)
    ) {
        items(entries, key = { it.name }) { AcknowledgementCard(it) }
    }
}

@Composable
private fun AcknowledgementCard(entry: Acknowledgement) {
    val shape = RoundedCornerShape(CardRadius)
    SettingsSection(entry.name) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IntentScreen.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(LicenseSpacing)
        ) {
            Text(
                entry.licenseTitle,
                style = MaterialTheme.typography.titleSmall,
                color = IntentScreen.text
            )
            entry.url?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
            }
            SelectionContainer {
                Text(
                    entry.licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
}
