package dev.intent.core.protocol

import java.io.IOException

/** HTTP status only; server bodies, headers and addresses stay out of diagnostics. */
class WebSocketUpgradeException(val statusCode: Int) :
    IOException("WebSocket upgrade rejected (HTTP $statusCode)")
