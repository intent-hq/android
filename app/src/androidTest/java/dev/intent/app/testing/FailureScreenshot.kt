package dev.intent.app.testing

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.runner.Description

internal fun captureFailureScreenshot(description: Description, failure: Throwable) {
    // Keep the original test failure even if screenshot capture is unavailable.
    runCatching {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        output.mkdirs()
        val name = "failure-${description.className}-${description.methodName}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }.onFailure { failure.addSuppressed(it) }
}
