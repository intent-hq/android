package dev.intent.app.attachments

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaLoadState(
    val file: File? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class MediaLoadStore(private val source: MediaSource, private val reference: MediaReference) :
    Closeable {
    private val mutableState = MutableStateFlow(MediaLoadState())
    val state = mutableState.asStateFlow()
    private var closed = false

    @Synchronized
    fun load() {
        if (closed || state.value.loading) return
        mutableState.value = MediaLoadState(loading = true)
        source.load(reference).whenComplete { file, error ->
            synchronized(this) {
                if (!closed) {
                    mutableState.value =
                        if (error !=
                            null
                        ) {
                            MediaLoadState(error = "Cannot load attachment. Retry.")
                        } else {
                            MediaLoadState(file = file)
                        }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
    }
}
