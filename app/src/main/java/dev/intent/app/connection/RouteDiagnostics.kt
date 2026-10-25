package dev.intent.app.connection

import android.util.Log
import dev.intent.tailcat.NativeTunnelException

/** Only sanitized native errors may include text. Other errors expose classes only. */
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
                    " exception=${it.javaClass.simpleName}" +
                        " cause=${it.cause?.javaClass?.simpleName}" +
                        (
                            (it as? NativeTunnelException)?.let { native ->
                                " detail=${native.diagnosticDetail}"
                            }
                                ?: ""
                            )
                }
                    ?: ""
                )
}
