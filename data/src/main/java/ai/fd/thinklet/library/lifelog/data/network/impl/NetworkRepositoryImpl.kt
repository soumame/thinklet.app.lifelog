package ai.fd.thinklet.library.lifelog.data.network.impl

import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

class NetworkRepositoryImpl @Inject constructor(
    private val context: Context
) : NetworkRepository {

    companion object {
        private const val TAG = "NetworkRepositoryImpl"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isWifiConnectedFlow(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isWifiConnected())
            }

            override fun onLost(network: Network) {
                trySend(isWifiConnected())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(isWifiConnected())
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        
        // 初回の状態を送信
        trySend(isWifiConnected())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    override fun isWifiConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connection", e)
            false
        }
    }

    override suspend fun hasInternetConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Googleの8.8.8.8 DNS serverへの接続テスト
            val socket = Socket()
            val socketAddress = InetSocketAddress("8.8.8.8", 53)
            socket.connect(socketAddress, 3000)
            socket.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "No internet connection", e)
            false
        }
    }
}