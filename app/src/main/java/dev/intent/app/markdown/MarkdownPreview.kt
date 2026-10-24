package dev.intent.app.markdown

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal val markdownFixture = """
    # Markdown fixture
    **Bold**, *emphasis*, `inline code`, and [website](https://intentapp.dev).

    > A quotation with **formatted text**.
    > - A nested item

    - [x] Completed task
    - [ ] Pending task

    3. Third item
    4. Fourth item

    | Feature | State |
    | :--- | ---: |
    | **Tables** | Ready |
    | Escaped pipe | a\|b |

    ```kotlin
    val answer = 42 // A highlighted comment
    val label = "Markdown"
    ```

    [Spec](intent://local/note/spec)
    ![Workspace image](intent://local/file/.intent/attachments/fixture.png)
    [File](intent://local/file/report.pdf)

    ---
    An unfinished streaming fence follows:
    ~~~mermaid
    graph TD
      A --> B
""".trimIndent()

@Suppress("UnusedPrivateMember") // Android Studio preview entry point.
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun MarkdownFixturePreview() {
    MaterialTheme {
        MarkdownContent(
            markdownFixture,
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp)
        )
    }
}
