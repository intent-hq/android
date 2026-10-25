package dev.intent.core.protocol.session

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject

/** Payloads stay JSON until feature stores decode their own wire models. */
data class SubscriptionSpec(
    val method: String,
    val params: JsonObject,
    val notificationMethod: String = "subscription.push",
    val unsubscribeMethod: String = "events.unsubscribe"
) {
    init {
        require(method.isNotBlank())
        require(notificationMethod == "subscription.push" || notificationMethod == "events.event")
        // Generic loss recovery is deliberately not negotiated until fenced store recovery exists.
        require("lossRecovery" !in params)
        require("replaceGroup" !in params) { "Close the previous local subscription explicitly" }
    }
}

interface SessionSubscription : Closeable {
    /** First admission only. Each reconnect reports a fresh admission through onSubscribed. */
    val ready: CompletableFuture<String>
}

internal class SubscriptionEntry(
    val spec: SubscriptionSpec,
    val onMessage: (JsonObject) -> Unit,
    val onSubscribed: (String) -> Unit,
    val onError: (Throwable) -> Unit
) {
    val ready = CompletableFuture<String>()
    var serverId: String? = null
    var sequence = -1L
    var active = true
}
