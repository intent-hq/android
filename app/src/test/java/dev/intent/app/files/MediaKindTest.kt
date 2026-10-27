package dev.intent.app.files

import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.mediaPreviewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKindTest {
    @Test
    fun fileTypesAndLimitsMatchViewers() {
        mapOf(
            "REPORT.PDF" to WorkspacePreviewKind.PDF,
            "clip.mp4" to WorkspacePreviewKind.VIDEO,
            "clip.webm" to WorkspacePreviewKind.VIDEO,
            "clip.MOV" to WorkspacePreviewKind.VIDEO,
            "clip.m4v" to WorkspacePreviewKind.VIDEO,
            "index.HTML" to WorkspacePreviewKind.HTML,
            "index.htm" to WorkspacePreviewKind.HTML,
            "photo.png" to WorkspacePreviewKind.IMAGE,
            "readme.txt" to WorkspacePreviewKind.TEXT,
            "archive.zip" to WorkspacePreviewKind.UNSUPPORTED
        ).forEach { (path, kind) -> assertEquals(kind, workspacePreviewKind(path)) }
        assertEquals(200 * 1024 * 1024, previewByteLimit(WorkspacePreviewKind.VIDEO))
        assertEquals(200 * 1024 * 1024, previewByteLimit(WorkspacePreviewKind.PDF))
        assertEquals(5 * 1024 * 1024, previewByteLimit(WorkspacePreviewKind.HTML))
    }

    @Test
    fun attachmentMimeAndPathOverrideMarkdownImageSyntax() {
        assertEquals(
            WorkspacePreviewKind.PDF,
            mediaPreviewKind(MediaReference("Report", true, path = "a.pdf"))
        )
        assertEquals(
            WorkspacePreviewKind.VIDEO,
            mediaPreviewKind(MediaReference("Clip", true, path = "a.mp4"))
        )
        assertEquals(
            WorkspacePreviewKind.VIDEO,
            mediaPreviewKind(MediaReference("Opaque", false, mimeType = "video/mp4"))
        )
        assertEquals(
            WorkspacePreviewKind.HTML,
            mediaPreviewKind(MediaReference("Page", false, mimeType = "text/html"))
        )
        assertEquals(
            WorkspacePreviewKind.IMAGE,
            mediaPreviewKind(MediaReference("Inline image", true))
        )
    }
}
