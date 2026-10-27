package dev.intent.app.lists

/** Include each matching row's ancestors. Missing parents and cycles terminate the walk. */
internal fun <T> retainMatchingBranches(
    items: List<T>,
    id: (T) -> String,
    parentId: (T) -> String?,
    matches: (T) -> Boolean
): List<T> {
    val byId = items.associateBy(id)
    val included = mutableSetOf<String>()
    items.filter(matches).forEach { match ->
        var current: T? = match
        while (current != null && included.add(id(current))) {
            current = parentId(current)?.let(byId::get)
        }
    }
    return items.filter { id(it) in included }
}
