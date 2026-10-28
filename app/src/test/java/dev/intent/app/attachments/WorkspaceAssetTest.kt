package dev.intent.app.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceAssetTest {
    @Test
    fun decodesOnceAndIgnoresBackendHints() {
        assertEquals(
            WorkspaceAsset("linked-workspace", "chart #?.png"),
            noteMediaReference(
                "workspace-asset://linked-workspace/chart%20%23%3F.png?v=2&backend=other#hint",
                "Chart",
                true
            )?.asset
        )
        assertEquals(
            WorkspaceAsset("ws-1", "chart%2Epng"),
            noteMediaReference("WORKSPACE-ASSET://ws-1/chart%252Epng", "Chart", true)?.asset
        )
    }

    @Test
    fun rejectsMalformedAndRemoteAssets() {
        listOf(
            "workspace-asset:///picture.png", "workspace-asset://ws-1",
            "workspace-asset://ws-1/", "workspace-asset://ws-1//picture.png",
            "workspace-asset://ws-1/picture.png/", "workspace-asset://ws-1/folder/picture.png",
            "workspace-asset://ws-1/..", "workspace-asset://ws-1/%2E",
            "workspace-asset://ws-1/%2e%2e", "workspace-asset://ws-1/../picture.png",
            "workspace-asset://ws-1/folder%2Fpicture.png",
            "workspace-asset://ws-1/folder%5Cpicture.png",
            "workspace-asset://ws-1/%00.png", "workspace-asset://ws-1/%20",
            "workspace-asset://ws-1/%FF.png", "workspace-asset://ws-1/%0A.png",
            "workspace-asset://user@ws-1/picture.png", "workspace-asset://ws-1:80/picture.png",
            "https://example.com/picture.png", "file:///private/picture.png"
        ).forEach { assertNull(it, noteMediaReference(it, "Image", true)) }
    }
}
