package dev.intent.app.browser

import dev.intent.core.protocol.browser.PreviewUrl
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class SavedBrowser(val id: String, val title: String, val url: String)

data class SavedBrowsersState(
    val browsers: List<SavedBrowser> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

interface SavedBrowserPersistence {
    fun load(): List<SavedBrowser>
    fun save(browsers: List<SavedBrowser>)
}

internal class MemorySavedBrowserPersistence : SavedBrowserPersistence {
    private var browsers: List<SavedBrowser> = emptyList()
    override fun load(): List<SavedBrowser> = browsers
    override fun save(browsers: List<SavedBrowser>) {
        this.browsers = browsers.toList()
    }
}

internal fun browserPersistence(
    connectionId: String,
    workspaceId: String,
    persistent: () -> SavedBrowserPersistence
): SavedBrowserPersistence = if (connectionId.isBlank() || workspaceId.isBlank()) {
    MemorySavedBrowserPersistence()
} else {
    persistent()
}

/** Query strings and fragments can contain login tokens; only keep the page location on disk. */
internal fun SavedBrowser.withoutUrlSecrets(): SavedBrowser {
    val parsed = PreviewUrl.parse(url)
    val location = "${parsed.scheme}://${parsed.rawAuthority}${parsed.rawPath.orEmpty()}"
    return copy(url = location, title = if (location == url) title else location)
}

/** Persistence is scoped to one saved host identity and workspace, never the current IP address. */
class SavedBrowserStore(private val persistence: SavedBrowserPersistence) {
    private val mutableState = MutableStateFlow(SavedBrowsersState())
    val state = mutableState.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load() {
        loaded = false
        mutableState.value = state.value.copy(loading = true, error = null)
        runCatching {
            val original = persistence.load()
            val browsers = original.mapNotNull {
                runCatching { it.withoutUrlSecrets() }.getOrNull()
            }
                .distinctBy { it.id }
            if (browsers != original) persistence.save(browsers)
            browsers
        }.onSuccess { browsers ->
            loaded = true
            mutableState.value = SavedBrowsersState(
                browsers = browsers,
                loading = false
            )
        }.onFailure {
            mutableState.value = state.value.copy(
                loading = false,
                error = "Cannot load saved browsers. Retry to recover them."
            )
        }
    }

    @Synchronized
    fun savePage(id: String?, url: String, title: String?): String? {
        if (!loaded || state.value.loading) return null
        return runCatching {
            val page = SavedBrowser("", title?.trim()?.takeIf { it.isNotEmpty() } ?: url, url)
                .withoutUrlSecrets()
            val existing = state.value.browsers.firstOrNull { it.id == id }
                ?: state.value.browsers.firstOrNull { it.url == page.url }
            val browser = page.copy(id = existing?.id ?: UUID.randomUUID().toString())
            val browsers = state.value.browsers.filterNot { it.id == browser.id } + browser
            persistence.save(browsers)
            mutableState.value = SavedBrowsersState(browsers, loading = false)
            browser.id
        }.getOrElse {
            mutableState.value =
                state.value.copy(error = "Cannot save this browser. Return to the list and retry.")
            null
        }
    }

    @Synchronized
    fun remove(id: String) {
        if (!loaded || state.value.loading) return
        val browsers = state.value.browsers.filterNot { it.id == id }
        runCatching { persistence.save(browsers) }.onSuccess {
            mutableState.value = SavedBrowsersState(browsers, loading = false)
        }.onFailure {
            mutableState.value =
                state.value.copy(error = "Cannot remove this browser. Retry to try again.")
        }
    }
}
