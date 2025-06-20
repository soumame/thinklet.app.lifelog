package ai.fd.thinklet.library.lifelog.data.s3

import java.io.File

/**
 * S3アップロード用のRepository
 */
interface S3UploadRepository {
    
    /**
     * S3の設定を行います
     * @param bucketName S3バケット名
     * @param region AWSリージョン（例: "ap-northeast-1"）
     * @param accessKey AWSアクセスキー
     * @param secretKey AWSシークレットキー
     * @param endpoint カスタムエンドポイント（S3互換サービス用、nullの場合はAWS S3を使用）
     */
    fun configure(bucketName: String, region: String, accessKey: String, secretKey: String, endpoint: String? = null)
    
    /**
     * ファイルをS3にアップロードします
     * @param file アップロードするファイル
     * @param keyPrefix S3オブジェクトキーのプレフィックス（オプション）
     * @return アップロードが成功した場合はS3のURL、失敗した場合はException
     */
    suspend fun uploadFile(file: File, keyPrefix: String = ""): Result<String>
    
    /**
     * S3の設定が有効かどうかを確認します
     * @return 設定が完了している場合はtrue
     */
    fun isConfigured(): Boolean
}