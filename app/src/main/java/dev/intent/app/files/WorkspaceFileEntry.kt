package dev.intent.app.files

import dev.intent.app.attachments.safeWorkspacePath
import java.text.Collator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WorkspaceFileEntry(val path: String, val name: String, val isDirectory: Boolean)
data class WorkspaceFileRow(val entry: WorkspaceFileEntry, val depth: Int)

internal fun parseFileList(result: JsonElement, directory: String): List<WorkspaceFileEntry> {
    require(directory == "." || safeWorkspacePath(directory))
    val names = mutableSetOf<String>()
    val entries = result.jsonArray.map {
        val item = it.jsonObject
        require(item.getValue("name").jsonPrimitive.isString)
        require(item.getValue("type").jsonPrimitive.isString)
        val name = item.getValue("name").jsonPrimitive.content
        val type = item.getValue("type").jsonPrimitive.content
        require(safeWorkspacePath(name) && '/' !in name && names.add(name))
        require(type == "file" || type == "directory")
        WorkspaceFileEntry(
            if (directory ==
                "."
            ) {
                name
            } else {
                "$directory/$name"
            },
            name,
            type == "directory"
        )
    }
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    return entries.filter { it.name != ".git" }.sortedWith { left, right ->
        when {
            left.isDirectory != right.isDirectory -> if (left.isDirectory) -1 else 1
            else -> naturalCompare(left.name, right.name, collator).takeIf { it != 0 }
                ?: left.path.compareTo(right.path)
        }
    }
}

private val nameParts = Regex("[0-9]+|[^0-9]+")

private fun naturalCompare(left: String, right: String, collator: Collator): Int {
    val a = nameParts.findAll(left).map { it.value }.toList()
    val b = nameParts.findAll(right).map { it.value }.toList()
    for (index in 0 until minOf(a.size, b.size)) {
        val x = a[index]
        val y = b[index]
        val order = if (x.first().isDigit() && y.first().isDigit()) {
            val digitsX = x.trimStart('0').ifEmpty { "0" }
            val digitsY = y.trimStart('0').ifEmpty { "0" }
            digitsX.length.compareTo(digitsY.length).takeIf { it != 0 }
                ?: digitsX.compareTo(digitsY)
        } else {
            collator.compare(x, y)
        }
        if (order != 0) return order
    }
    return a.size.compareTo(b.size)
}
