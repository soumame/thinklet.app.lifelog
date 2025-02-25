package ai.fd.thinklet.library.lifelog.data

import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.audioCapture.impl.AudioCaptureRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.file.impl.FileSelectorRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.gif.GifEncoderRepository
import ai.fd.thinklet.library.lifelog.data.gif.impl.GifEncoderRepositoryImpl
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
}

