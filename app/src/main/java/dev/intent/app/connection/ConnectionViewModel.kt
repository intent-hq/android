package dev.intent.app.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** Retains credentials and the session across activity recreation without storing tokens in bundles. */
class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    val controller = ConnectionController(SavedConnections.open(application))

    init {
        controller.restore()
    }

    override fun onCleared() {
        controller.close()
    }
}
