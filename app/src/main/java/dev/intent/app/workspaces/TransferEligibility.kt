package dev.intent.app.workspaces

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Current principal.me reply only; saved connection metadata never grants transfer access. */
@Suppress("ReturnCount") // Unknown or inconsistent authority is never eligible.
internal fun transferPrincipalIsOwner(principal: JsonObject, capabilities: JsonObject): Boolean {
    val id = principal["id"] as? JsonPrimitive
    if (id?.isString != true || id.contentOrNull.isNullOrBlank()) return false
    if (principal["isAdministrator"] != JsonPrimitive(true)) return false
    val membership = capabilities["hostMembership"] == JsonPrimitive(1)
    val role = principal["hostRole"]
    if (role != null && role != JsonPrimitive("owner")) return false
    if (!membership) return true
    val revision = principal["hostMembershipRevision"] as? JsonPrimitive
    return role == JsonPrimitive("owner") &&
        revision?.isString == false &&
        revision.longOrNull?.let { it in 0..MAX_MEMBERSHIP_REVISION } == true
}

private const val MAX_MEMBERSHIP_REVISION = 9_007_199_254_740_991L
