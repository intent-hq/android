package dev.intentapp.android.connection

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel

/** Retains credentials and the session across activity recreation without storing tokens in bundles. */
class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    val controller = ConnectionController(SavedConnections.open(application))
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    // Like iOS path monitoring: a new or lost default network ends any retry wait.
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = controller.recoverNow()
        override fun onLost(network: Network) = controller.recoverNow()
    }

    init {
        controller.restore()
        connectivity.registerDefaultNetworkCallback(network)
    }

    override fun onCleared() {
        connectivity.unregisterNetworkCallback(network)
        controller.close()
    }
}
