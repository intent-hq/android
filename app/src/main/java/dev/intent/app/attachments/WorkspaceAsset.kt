package dev.intent.app.attachments

import java.net.URI

/** The URL selects an asset workspace, never a backend or an HTTP transport. */
data class WorkspaceAsset(val workspaceId: String, val assetId: String) {
    fun isSafe(): Boolean = safeAssetIdentifier(workspaceId) && safeAssetIdentifier(assetId)
}

private fun safeAssetIdentifier(value: String): Boolean = value.isNotBlank() &&
    value != "." &&
    value != ".." &&
    value.none { it == '/' || it == '\\' || it.isISOControl() || it == '\uFFFD' }

fun noteMediaReference(url: String, label: String, image: Boolean): MediaReference? = runCatching {
    val uri = URI(url)
    if (uri.scheme.equals("workspace-asset", ignoreCase = true)) {
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val workspace = uri.host ?: return null
        val asset = WorkspaceAsset(workspace, uri.path.removePrefix("/"))
        if (!uri.path.startsWith('/') || !asset.isSafe()) return null
        // Query/fragment hints are not identity and must not switch the admitted session.
        return MediaReference(label, image, asset = asset)
    }
    val localFile = uri.scheme == "intent" && uri.host == "local"
    if (!localFile || uri.rawQuery != null || uri.rawFragment != null) return null
    val path = uri.path.takeIf { it.startsWith("/file/") }?.removePrefix("/file/") ?: return null
    if (!safeWorkspacePath(path)) return null
    MediaReference(label, image, path = path)
}.getOrNull()
