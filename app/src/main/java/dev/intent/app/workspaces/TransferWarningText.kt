package dev.intent.app.workspaces

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Rewrite only known warning prefixes; preserve path lists and future server warnings. */
internal fun transferWarningText(warning: JsonObject): String? {
    val message = (warning["message"] as? JsonPrimitive)?.contentOrNull ?: return null
    val code = (warning["code"] as? JsonPrimitive)?.contentOrNull
    return when (code) {
        "agents-running" -> message.replace(Regex("^(\\d+) agent\\(s\\) are")) {
            val count = it.groupValues[1]
            if (count == "1") "1 agent is" else "$count agents are"
        }
        "uncommitted-changes" -> message.replace(Regex("^(\\d+) uncommitted file\\(s\\)")) {
            val count = it.groupValues[1]
            if (count == "1") "1 uncommitted file" else "$count uncommitted files"
        }
        "unmerged-sandboxes" -> message.replace(
            Regex("^(\\d+) unmerged sandbox\\(es\\); their branches")
        ) {
            val count = it.groupValues[1]
            if (count ==
                "1"
            ) {
                "1 unmerged sandbox; its branches"
            } else {
                "$count unmerged sandboxes; their branches"
            }
        }
        "nested-repos-skipped" -> message.replace(
            Regex("^(\\d+) nested git repo\\(s\\)/worktree\\(s\\) will not travel with the export:")
        ) {
            val count = it.groupValues[1]
            "Nested Git repositories or worktrees excluded from export ($count):"
        }
        else -> message
    }
}
