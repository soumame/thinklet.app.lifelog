package ai.fd.thinklet.library.lifelog.data.http

import java.io.File

/**
 * HTTPアップロード用のRepository
 */
interface HttpUploadRepository {
    
    /**
     * HTTPアップロードの設定を行います
     * @param uploadUrl アップロード先のURL
     * @param apiKey APIキー（x-api-keyヘッダーで送信）
     */
    fun configure(uploadUrl: String, apiKey: String)
    
    /**
     * ファイルをHTTPでアップロードします
     * @param file アップロードするファイル
     * @return アップロードが成功した場合はレスポンス、失敗した場合はException
     */
    suspend fun uploadFile(file: File): Result<String>
    
    /**
     * HTTPアップロードの設定が有効かどうかを確認します
     * @return 設定が完了している場合はtrue
     */
    fun isConfigured(): Boolean
}