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

/** Persistence is scoped to one saved host identity and workspace, never the current IP address. */
class SavedBrowserStore(private val persistence: SavedBrowserPersistence) {
    private val mutableState = MutableStateFlow(SavedBrowsersState())
    val state = mutableState.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load() {
        loaded = false
        mutableState.value = state.value.copy(loading = true, error = null)
        runCatching { persistence.load() }.onSuccess { browsers ->
            loaded = true
            mutableState.value = SavedBrowsersState(
                browsers = browsers.filter { runCatching { PreviewUrl.parse(it.url) }.isSuccess }
                    .distinctBy { it.id },
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
            PreviewUrl.parse(url)
            val existing = state.value.browsers.firstOrNull { it.id == id }
                ?: state.value.browsers.firstOrNull { it.url == url }
            val browser = SavedBrowser(
                existing?.id ?: UUID.randomUUID().toString(),
                title?.trim()?.takeIf { it.isNotEmpty() } ?: url,
                url
            )
            val browsers = state.value.browsers.filterNot { it.id == browser.id } + browser
            persistence.save(browsers)
            mutableState.value = SavedBrowsersState(browsers, loading = false)
            browser.id
        }.getOrElse {
            mutableState.value = state.value.copy(error = "Cannot save this browser. Return to the list and retry.")
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
            mutableState.value = state.value.copy(error = "Cannot remove this browser. Retry to try again.")
        }
    }
}
