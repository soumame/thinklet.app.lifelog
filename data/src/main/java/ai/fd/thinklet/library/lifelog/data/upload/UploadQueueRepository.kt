package ai.fd.thinklet.library.lifelog.data.upload

import java.io.File

/**
 * アップロード待ちのファイルを管理するRepository
 */
interface UploadQueueRepository {
    
    /**
     * ファイルをアップロードキューに追加します
     * @param file アップロード対象のファイル
     */
    suspend fun enqueueFile(file: File)
    
    /**
     * アップロード待ちのファイル一覧を取得します
     * @return アップロード待ちのファイルリスト
     */
    suspend fun getPendingFiles(): List<File>
    
    /**
     * ファイルをアップロードキューから削除します（アップロード完了時）
     * @param file 削除対象のファイル
     */
    suspend fun removeFile(file: File)
    
    /**
     * WiFi接続時にキューに溜まったファイルを一括アップロードします
     */
    suspend fun processPendingUploads()
    
    /**
     * アップロードキューをクリアします
     */
    suspend fun clearQueue()
}