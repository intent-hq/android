package dev.intentapp.android.scripts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Port of iOS WorkspaceScriptDiscovery: manifest scripts that can be saved on first launch. */
object WorkspaceScriptDiscovery {
    val manifestPaths = listOf("package.json", "Makefile", "Cargo.toml", "pyproject.toml")

    private data class Candidate(val name: String, val command: String, val source: String)

    fun scripts(
        workspaceId: String,
        files: Map<String, String>,
        fileNames: Set<String>
    ): List<WorkspaceScriptRow> {
        val candidates = packageScripts(files["package.json"].orEmpty(), fileNames) +
            makeScripts(files["Makefile"].orEmpty()) +
            cargoScripts(files["Cargo.toml"].orEmpty()) +
            pythonScripts(files["pyproject.toml"].orEmpty())
        val used = mutableSetOf<String>()
        return candidates.map { candidate ->
            WorkspaceScriptRow(
                id = "manifest:${candidate.source}:${candidate.name}",
                workspaceId = workspaceId,
                name = uniqueName(candidate, used),
                command = candidate.command,
                mode = mode(candidate.name),
                runtime = null,
                registered = false
            )
        }
    }

    private fun packageScripts(content: String, fileNames: Set<String>): List<Candidate> {
        val manifest = runCatching { Json.parseToJsonElement(content).jsonObject }.getOrNull()
        val scripts = manifest?.get("scripts") as? JsonObject ?: return emptyList()
        val manager = packageManager(
            (manifest["packageManager"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            fileNames
        )
        return scripts.keys.sorted().mapNotNull { name ->
            val command = (scripts[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            val quoted = shellName(name)
            if (command.isNullOrBlank() || '\u0000' in command || quoted == null) {
                null
            } else {
                Candidate(name, "$manager run $quoted", "package.json")
            }
        }
    }

    private fun packageManager(declared: String?, fileNames: Set<String>): String {
        if (declared != null) {
            val value = declared.trim()
            val parts = value.split("@", limit = 2)
            val valid = parts.first() in PACKAGE_MANAGERS &&
                value.none { it.isWhitespace() || it.isISOControl() } &&
                (parts.size == 1 || parts[1].isNotEmpty())
            if (valid) return parts.first()
        }
        return when {
            "pnpm-lock.yaml" in fileNames -> "pnpm"
            "yarn.lock" in fileNames -> "yarn"
            "bun.lock" in fileNames || "bun.lockb" in fileNames -> "bun"
            else -> "npm"
        }
    }

    private fun shellName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() || trimmed.startsWith("-") || name.any { it.isISOControl() } -> null
            SAFE_SHELL_NAME.matches(name) -> name
            else -> "'" + name.replace("'", "'\"'\"'") + "'"
        }
    }

    private fun makeScripts(content: String): List<Candidate> {
        val targets = linkedSetOf<String>()
        content.lineSequence().forEach { line ->
            MAKE_TARGET.find(line)?.groupValues?.get(1)?.split(WHITESPACE)?.forEach {
                if (it.isNotEmpty()) targets.add(it)
            }
        }
        return targets.map { Candidate(it, "make $it", "Makefile") }
    }

    private fun cargoScripts(content: String): List<Candidate> {
        if (!CARGO_MANIFEST.containsMatchIn(content)) return emptyList()
        return listOf("build", "test", "check").map { Candidate(it, "cargo $it", "Cargo.toml") }
    }

    private fun pythonScripts(content: String): List<Candidate> {
        fun has(pattern: String) = Regex(pattern, RegexOption.MULTILINE).containsMatchIn(content)
        val pytest = has("""^\[tool\.pytest(?:\.ini_options)?\]""")
        val ruff = has("""^\[tool\.ruff(?:\]|\.)""")
        val black = has("""^\[tool\.black\]""")
        val mypy = has("""^\[tool\.mypy\]""")
        val pyright = has("""^\[tool\.pyright\]""")
        val source = "pyproject.toml"
        return buildList {
            if (pytest) add(Candidate("test", "pytest", source))
            if (ruff) add(Candidate("lint", "ruff check .", source))
            if (black || ruff) {
                add(Candidate("format", if (black) "black ." else "ruff format .", source))
            }
            if (mypy || pyright) {
                add(Candidate("typecheck", if (mypy) "mypy ." else "pyright", source))
            }
        }
    }

    private fun uniqueName(candidate: Candidate, used: MutableSet<String>): String {
        var name = candidate.name
        if (name in used) {
            val prefix = when (candidate.source) {
                "Makefile" -> "make"
                "Cargo.toml" -> "cargo"
                "pyproject.toml" -> "python"
                else -> "script"
            }
            val prefixed = "$prefix:$name"
            name = prefixed
            var suffix = 2
            while (name in used) name = "$prefixed-${suffix++}"
        }
        used.add(name)
        return name
    }

    internal fun mode(name: String): String {
        val lower = name.lowercase()
        return when {
            lower in SERVICE_NAMES -> "service"
            lower in COMMAND_NAMES -> "command"
            else -> MODE_PREFIXES.firstOrNull { lower.startsWith(it.first) }?.second
                ?: if ("storybook" in lower) "service" else "command"
        }
    }

    private val PACKAGE_MANAGERS = setOf("pnpm", "yarn", "npm", "bun")
    private val SAFE_SHELL_NAME = Regex("""^[A-Za-z0-9_./:@%+=,-]+$""")
    private val WHITESPACE = Regex("""\s+""")
    private val MAKE_TARGET =
        Regex("""^([A-Za-z0-9][A-Za-z0-9_.-]*(?:\s+[A-Za-z0-9][A-Za-z0-9_.-]*)*)\s*::?(?:\s|$)""")
    private val CARGO_MANIFEST =
        Regex("""^\[(package|workspace)\]|^\[\[bin\]\]""", RegexOption.MULTILINE)
    private val SERVICE_NAMES =
        setOf("dev", "start", "serve", "develop", "storybook", "watch", "preview")
    private val COMMAND_NAMES = setOf(
        "build", "test", "lint", "typecheck", "type-check", "format", "fmt", "prettier",
        "eslint", "check"
    )
    private val MODE_PREFIXES = listOf(
        "dev:" to "service", "dev-" to "service", "start:" to "service",
        "start-" to "service", "build:" to "command", "build-" to "command",
        "test:" to "command", "test-" to "command", "lint:" to "command",
        "lint-" to "command", "format:" to "command", "format-" to "command",
        "typecheck:" to "command", "type-check:" to "command", "storybook:" to "service",
        "watch:" to "service", "watch-" to "service"
    )
}
