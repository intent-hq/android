package dev.intent.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.concurrent.CompletableFuture

class AndroidPhotoSource(private val context: Context) {
    @Suppress("TooGenericExceptionCaught") // Delete temporary files on every import failure.
    fun prepareImage(uri: String, cancelled: () -> Boolean): CompletableFuture<File> =
        CompletableFuture.supplyAsync {
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
                            check(!cancelled()) { "Image import cancelled" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= MAX_IMPORT_BYTES)
                            target.write(buffer, 0, count)
                        }
                    }
                }
                check(!cancelled()) { "Image import cancelled" }
                val bitmap = decodePhoto(raw)
                try {
                    writeBoundedJPEG(bitmap, output, cancelled)
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
}

/** Decode off the main thread. Bound bitmap memory and apply the camera's EXIF orientation. */
@Suppress("MagicNumber") // EXIF orientation angles in degrees.
fun decodePhoto(file: File, maxDimension: Int = MAX_DIMENSION): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    check(options.outWidth > 0 && options.outHeight > 0)
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > maxDimension) sample *= 2
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
private const val MAX_IMPORT_BYTES = 30L * 1024 * 1024
private const val MAX_PHOTO_BYTES = 1L * 1024 * 1024
private const val MAX_DIMENSION = 2048

@Suppress("MagicNumber") // JPEG quality ladder shared with iOS.
private val JPEG_QUALITIES = listOf(85, 60, 40)
private const val RESIZE_PASSES = 3

private fun writeBoundedJPEG(original: Bitmap, output: File, cancelled: () -> Boolean) {
    var bitmap = original
    try {
        repeat(RESIZE_PASSES) { pass ->
            for (quality in JPEG_QUALITIES) {
                check(!cancelled()) { "Image import cancelled" }
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it))
                }
                if (output.length() <= MAX_PHOTO_BYTES) return
            }
            if (pass < RESIZE_PASSES - 1) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    maxOf(1, bitmap.width / 2),
                    maxOf(
                        1,
                        bitmap.height / 2
                    ),
                    true
                )
                if (bitmap !== original && bitmap !== scaled) bitmap.recycle()
                bitmap = scaled
            }
        }
        error("Image exceeds 1 MiB after resizing")
    } finally {
        if (bitmap !== original) bitmap.recycle()
    }
}
