package dev.intent.app.connection

import android.util.Log

/** Never include endpoints, payloads, exception messages or stack traces. */
internal object RouteDiagnostics {
    fun record(route: String, stage: String, outcome: String, error: Throwable? = null) {
        val entry = format(route, stage, outcome, error)
        // Diagnostics must not change transport behavior, including in JVM-only tests.
        runCatching { Log.d("IntentConn", entry) }
    }

    internal fun format(route: String, stage: String, outcome: String, error: Throwable?): String =
        "route=$route stage=$stage outcome=$outcome" +
            (
                error?.let {
                    " exception=${it.javaClass.simpleName} cause=${it.cause?.javaClass?.simpleName}"
                }
                    ?: ""
                )
}
