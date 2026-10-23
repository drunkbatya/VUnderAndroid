package com.drunkbatya.vunder.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.drunkbatya.vunder.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class Connectivity(val isConnected: Boolean, val isMetered: Boolean)

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    val connectivity: StateFlow<Connectivity> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toConnectivity())
            }

            override fun onLost(network: Network) {
                trySend(Connectivity(isConnected = false, isMetered = false))
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .onEach { Log.i(TAG, "network connected=${it.isConnected} metered=${it.isMetered}") }
        .stateIn(scope, SharingStarted.Eagerly, initialConnectivity())

    val isConnected: Boolean
        get() = connectivity.value.isConnected

    private fun initialConnectivity(): Connectivity =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.toConnectivity()
            ?: Connectivity(isConnected = false, isMetered = false)

    private fun NetworkCapabilities.toConnectivity() = Connectivity(
        isConnected = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
