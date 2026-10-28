package dev.intent.app.workspaces

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class CreationRepositoryStore(private val operations: WorkspaceOperations) {
    fun repositories(query: String): List<CreationRepository> {
        val recent = if (query.isBlank()) {
            runCatching { operations.request("repo.list").rows("repos") }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val result = operations.request(
            if (query.isBlank()) "github.repos.list" else "github.repos.search",
            buildJsonObject {
                put("limit", REPOSITORY_PAGE_SIZE)
                if (query.isNotBlank()) put("query", repositoryQuery(query))
            }
        ).rows("repos")
        return (recent + result).mapNotNull { row ->
            val owner = (row["owner"] as? JsonPrimitive)?.contentOrNull
            val name = (row["name"] as? JsonPrimitive)?.contentOrNull
            if (owner.isNullOrBlank() || name.isNullOrBlank()) {
                null
            } else {
                CreationRepository(
                    owner,
                    name,
                    (row["defaultBranch"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                )
            }
        }.distinctBy { it.fullName }
    }

    fun branches(repository: CreationRepository, query: String): List<String> {
        val params = buildJsonObject {
            put("owner", repository.owner)
            put("repo", repository.name)
            put("limit", BRANCH_PAGE_SIZE)
            if (query.isNotBlank()) put("prefix", query.trim())
        }
        val result = operations.request("github.branches.list", params)
        val branches = (result["branches"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }
        val default = repository.defaultBranch.ifBlank {
            runCatching {
                operations.request("github.repos.get", params).obj("repo").string("defaultBranch")
            }.getOrDefault("")
        }
        return (
            listOf(default).filter {
                it.isNotBlank() &&
                    (query.isBlank() || it.startsWith(query))
            } +
                branches
            ).distinct()
    }

    fun checkDestination(path: String) {
        val result = operations.request(
            "host.directoryStatus",
            buildJsonObject {
                put("path", path)
            }
        )
        check(result["exists"] == JsonPrimitive(false)) {
            "This folder exists or could not be checked. Choose another name or retry."
        }
    }
}

internal fun repositoryQuery(query: String): String {
    val value = query.trim()
    if ('/' !in value || ' ' in value) return value
    val owner = value.substringBefore('/')
    val name = value.substringAfter('/')
    return listOf(name, owner.takeIf { it.isNotEmpty() }?.let { "user:$it" }.orEmpty())
        .filter { it.isNotEmpty() }.joinToString(" ")
}

private const val REPOSITORY_PAGE_SIZE = 50
private const val BRANCH_PAGE_SIZE = 100
