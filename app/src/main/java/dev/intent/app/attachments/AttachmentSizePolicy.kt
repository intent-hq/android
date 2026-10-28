package dev.intent.app.attachments

/** Match iOS ImportedPhotoVideo for both media imports and document selections. */
internal const val MAX_VIDEO_BYTES = 200L * 1024 * 1024
internal const val MAX_FILE_BYTES = 1024L * 1024 * 1024
internal const val VIDEO_TOO_LARGE = "Video is too large to send (max 200 MB)."

internal class VideoAttachmentTooLarge : IllegalArgumentException(VIDEO_TOO_LARGE)

internal val AttachmentDescription.isVideo: Boolean
    get() = mimeType.lowercase().startsWith("video/") ||
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

internal fun AttachmentDescription.validateSize(size: Long) {
    if (isVideo && size > MAX_VIDEO_BYTES) throw VideoAttachmentTooLarge()
    require(size <= MAX_FILE_BYTES) { "Attachment exceeds 1 GiB" }
}

internal fun attachmentFailureMessage(error: Throwable, fallback: String): String =
    generateSequence(error) { it.cause }.filterIsInstance<VideoAttachmentTooLarge>()
        .firstOrNull()?.message ?: fallback

private val VIDEO_EXTENSIONS = setOf(
    "webm", "mov", "qt", "mp4", "m4v", "mpeg", "mpg", "mpe", "m1v", "m2v",
    "3gp", "3gpp", "3g2", "3gpp2", "avi", "wmv", "asf", "vob", "mkv",
    "ts", "mts", "m2ts", "flv", "ogv"
)
