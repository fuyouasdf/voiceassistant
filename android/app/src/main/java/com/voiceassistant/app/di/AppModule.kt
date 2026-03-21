package com.voiceassistant.app.di

import android.content.Context
import androidx.room.Room
import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.audio.AudioPlayer
import com.voiceassistant.core.intent.IntentRouter
import com.voiceassistant.core.pipeline.PipelineConfig
import com.voiceassistant.core.pipeline.VoicePipeline
import com.voiceassistant.core.sherpa.SherpaASR
import com.voiceassistant.core.sherpa.SherpaASRImpl
import com.voiceassistant.core.sherpa.SherpaKWS
import com.voiceassistant.core.sherpa.SherpaKWSImpl
import com.voiceassistant.core.sherpa.SherpaTTS
import com.voiceassistant.core.sherpa.SherpaTTSImpl
import com.voiceassistant.core.sherpa.SherpaVAD
import com.voiceassistant.core.sherpa.SherpaVADImpl
import com.voiceassistant.data.local.AppDatabase
import com.voiceassistant.data.local.ConfigDao
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.data.remote.NavidromeApi
import com.voiceassistant.data.repository.LLMRepositoryImpl
import com.voiceassistant.data.repository.MusicRepositoryImpl
import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.data.repository.SettingsRepositoryImpl
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.MusicRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "voice_assistant.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideConfigDao(database: AppDatabase): ConfigDao {
        return database.configDao()
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(configDao: ConfigDao): SettingsRepository {
        return SettingsRepositoryImpl(configDao)
    }

    @Provides
    @Singleton
    fun provideNavidromeApi(): NavidromeApi {
        // URL is read from settings at runtime; default placeholder
        return Retrofit.Builder()
            .baseUrl("http://192.168.1.100:4533/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NavidromeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLLMApi(): LLMApi {
        // Note: API base URL and key are read from SettingsRepository at runtime
        // Default values are used here; settings take effect after app restart
        return Retrofit.Builder()
            .baseUrl("https://api.deepseek.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val request = original.newBuilder()
                            .header("Content-Type", "application/json")
                            .method(original.method, original.body)
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            )
            .build()
            .create(LLMApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        api: NavidromeApi,
        settingsRepository: SettingsRepository
    ): MusicRepository {
        return MusicRepositoryImpl(api, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideLLMRepository(
        llmApi: LLMApi,
        settingsRepository: SettingsRepository
    ): LLMRepository {
        return LLMRepositoryImpl(llmApi, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideAudioCapture(): AudioCapture {
        return AudioCapture()
    }

    @Provides
    @Singleton
    fun provideAudioPlayer(): AudioPlayer {
        return AudioPlayer()
    }

    @Provides
    @Singleton
    fun provideSherpaKWS(@ApplicationContext context: Context): SherpaKWS {
        return SherpaKWSImpl(context)
    }

    @Provides
    @Singleton
    fun provideSherpaVAD(@ApplicationContext context: Context): SherpaVAD {
        return SherpaVADImpl(context)
    }

    @Provides
    @Singleton
    fun provideSherpaASR(@ApplicationContext context: Context): SherpaASR {
        return SherpaASRImpl(context)
    }

    @Provides
    @Singleton
    fun provideSherpaTTS(@ApplicationContext context: Context): SherpaTTS {
        return SherpaTTSImpl(context)
    }

    @Provides
    @Singleton
    fun provideIntentRouter(
        musicRepository: MusicRepository?,
        llmRepository: LLMRepository?
    ): IntentRouter {
        return IntentRouter(musicRepository, llmRepository)
    }

    @Provides
    @Singleton
    fun provideVoicePipeline(
        kws: SherpaKWS,
        vad: SherpaVAD,
        asr: SherpaASR,
        tts: SherpaTTS,
        intentRouter: IntentRouter,
        audioCapture: AudioCapture,
        audioPlayer: AudioPlayer
    ): VoicePipeline {
        return VoicePipeline(
            config = PipelineConfig(),
            kws = kws,
            vad = vad,
            asr = asr,
            tts = tts,
            intentRouter = intentRouter,
            audioCapture = audioCapture,
            audioPlayer = audioPlayer
        )
    }
}
