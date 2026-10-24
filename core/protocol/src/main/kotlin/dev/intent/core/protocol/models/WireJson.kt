package dev.intent.core.protocol.models

import kotlinx.serialization.json.Json

/** Shared payload codec. Unknown fields and open status strings support newer daemons. */
val WireJson = Json { ignoreUnknownKeys = true }
