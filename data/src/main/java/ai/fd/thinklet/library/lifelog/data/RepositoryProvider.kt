package ai.fd.thinklet.library.lifelog.data

import ai.fd.thinklet.library.lifelog.data.audio.AudioProcessorRepository
import ai.fd.thinklet.library.lifelog.data.audio.impl.AudioProcessorRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.audioCapture.impl.AudioCaptureRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.file.impl.FileSelectorRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.gif.GifEncoderRepository
import ai.fd.thinklet.library.lifelog.data.gif.impl.GifEncoderRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverRepository
import ai.fd.thinklet.library.lifelog.data.jpeg.impl.JpegSaverRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.network.impl.NetworkRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.s3.impl.S3UploadRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import ai.fd.thinklet.library.lifelog.data.upload.impl.UploadQueueRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.mic.MicRepository
import ai.fd.thinklet.library.lifelog.data.mic.impl.MicRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.snapshot.impl.SnapShotRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import ai.fd.thinklet.library.lifelog.data.timer.impl.TimerRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.vibrate.VibrateRepository
import ai.fd.thinklet.library.lifelog.data.vibrate.impl.VibrateRepositoryImpl
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryProvider {
    @Provides
    @Singleton
    fun provideSnapShort(
        @ApplicationContext context: Context,
        vibrateRepository: VibrateRepository
    ): SnapShotRepository =
        SnapShotRepositoryImpl(context, vibrateRepository)

    @Provides
    @Singleton
    fun provideGifEncoder(fileSelectorRepository: FileSelectorRepository): GifEncoderRepository =
        GifEncoderRepositoryImpl(fileSelectorRepository)

    @Provides
    @Singleton
    fun provideFileSelector(@ApplicationContext context: Context): FileSelectorRepository =
        FileSelectorRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideTimerRepository(): TimerRepository = TimerRepositoryImpl()

    @Provides
    @Singleton
    fun provideMic(
        @ApplicationContext context: Context,
    ): MicRepository = MicRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideAudioCapture(
        fileSelectorRepository: FileSelectorRepository
    ): AudioCaptureRepository = AudioCaptureRepositoryImpl(fileSelectorRepository)

    @Provides
    @Singleton
    fun provideVibrate(
        @ApplicationContext context: Context,
    ): VibrateRepository = VibrateRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideNetwork(@ApplicationContext context: Context): NetworkRepository = 
        NetworkRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideS3Upload(): S3UploadRepository = S3UploadRepositoryImpl()

    @Provides
    @Singleton
    fun provideUploadQueue(
        @ApplicationContext context: Context,
        networkRepository: NetworkRepository,
        s3UploadRepository: S3UploadRepository
    ): UploadQueueRepository = UploadQueueRepositoryImpl(context, networkRepository, s3UploadRepository)

    @Provides
    @Singleton
    fun provideJpegSaver(
        fileSelectorRepository: FileSelectorRepository,
        s3UploadRepository: S3UploadRepository,
        networkRepository: NetworkRepository,
        uploadQueueRepository: UploadQueueRepository
    ): JpegSaverRepository = JpegSaverRepositoryImpl(
        fileSelectorRepository, 
        s3UploadRepository, 
        networkRepository, 
        uploadQueueRepository
    )

    @Provides
    @Singleton
    fun provideAudioProcessor(
        @ApplicationContext context: Context,
        networkRepository: NetworkRepository,
        s3UploadRepository: S3UploadRepository,
        uploadQueueRepository: UploadQueueRepository
    ): AudioProcessorRepository = AudioProcessorRepositoryImpl(
        context,
        networkRepository,
        s3UploadRepository,
        uploadQueueRepository
    )
}

