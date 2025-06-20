package ai.fd.thinklet.library.lifelog.data.network

import kotlinx.coroutines.flow.Flow

/**
 * ネットワーク状態を監視するRepository
 */
interface NetworkRepository {
    
    /**
     * WiFi接続状態を監視します
     * @return WiFi接続の状態を示すFlow（true: WiFi接続中、false: WiFi未接続）
     */
    fun isWifiConnectedFlow(): Flow<Boolean>
    
    /**
     * 現在のWiFi接続状態を取得します
     * @return 現在WiFiに接続している場合はtrue
     */
    fun isWifiConnected(): Boolean
    
    /**
     * インターネット接続可能かどうかをチェックします
     * @return インターネットに接続可能な場合はtrue
     */
    suspend fun hasInternetConnection(): Boolean
}