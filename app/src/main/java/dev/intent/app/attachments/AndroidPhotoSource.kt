package dev.intent.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.TransferProgress
import java.io.File
import java.util.concurrent.CompletableFuture

class AndroidPhotoSource(
    private val context: Context,
    private val upload: AttachmentUploadService
) : PhotoAttachmentSource {
    @Suppress("TooGenericExceptionCaught") // Delete temporary files on every import failure.
    override fun prepare(uri: String): CompletableFuture<File> = CompletableFuture.supplyAsync {
        val directory = File(context.cacheDir, "photo-imports").also {
            check(
                it.isDirectory || it.mkdirs()
            )
        }
        val raw = File.createTempFile("source-", ".photo", directory)
        val output = File.createTempFile("photo-", ".jpg", directory)
        try {
            context.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
                requireNotNull(input)
                raw.outputStream().use { target ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_IMPORT_BYTES)
                        target.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = decodePhoto(raw)
            try {
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it))
                }
                check(output.length() <= MAX_PHOTO_BYTES)
            } finally {
                bitmap.recycle()
            }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            raw.delete()
        }
    }

    override fun upload(file: File, key: String, progress: (TransferProgress) -> Unit) =
        upload.placeFile(file, "photo.jpg", "image/jpeg", key, onProgress = progress)
}

/** Decode off the main thread. Bound bitmap memory and apply the camera's EXIF orientation. */
@Suppress("MagicNumber") // EXIF orientation angles in degrees.
fun decodePhoto(file: File): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    check(options.outWidth > 0 && options.outHeight > 0)
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > MAX_DIMENSION) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, options))
    val orientation = ExifInterface(
        file.path
    ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !==
            bitmap
        ) {
            bitmap.recycle()
        }
    }
}

private const val COPY_BUFFER = 64 * 1024
private const val MAX_IMPORT_BYTES = 100L * 1024 * 1024
private const val MAX_PHOTO_BYTES = 7L * 1024 * 1024
private const val MAX_DIMENSION = 1600
private const val JPEG_QUALITY = 85
