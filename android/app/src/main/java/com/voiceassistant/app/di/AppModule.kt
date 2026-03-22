package com.voiceassistant.app.di

import android.content.Context
import androidx.room.Room
import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.audio.AudioPlayer
import com.voiceassistant.core.dlna.DLNAManager
import com.voiceassistant.core.dlna.DLNAPlayer
import com.voiceassistant.core.intent.IntentRouter
import com.voiceassistant.core.pipeline.ASRManager
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
import com.voiceassistant.domain.repository.PlayerRepository
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
    fun provideConfigHolder(): ConfigHolder {
        return ConfigHolder()
    }

    @Provides
    @Singleton
    fun provideNavidromeApi(configHolder: ConfigHolder): NavidromeApi {
        // Use dynamic URL from settings via interceptor
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Placeholder, actual URL set in interceptor
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val original = chain.request()
                        // Get base URL from config holder
                        val baseUrl = configHolder.navidromeUrl.ifEmpty { "http://192.168.31.206:4533" }
                        val host = baseUrl.removePrefix("http://").removePrefix("https://")
                            .substringBefore(":").substringBefore("/")

                        val portStr = baseUrl.substringAfterLast(":")
                        val port = if (portStr.toIntOrNull() != null) portStr.toInt() else if (baseUrl.startsWith("https")) 443 else 80

                        val newUrl = original.url.newBuilder()
                            .scheme(if (baseUrl.startsWith("https")) "https" else "http")
                            .host(host)
                            .port(port)
                            .build()
                        val request = original.newBuilder()
                            .url(newUrl)
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            )
            .build()
            .create(NavidromeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLLMApi(configHolder: ConfigHolder): LLMApi {
        // Use dynamic URL and API key from settings
        return Retrofit.Builder()
            .baseUrl("https://localhost/") // Placeholder, actual URL set in interceptor
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        // Get config from config holder
                        val baseUrl = configHolder.llmBaseUrl.ifEmpty { "https://api.deepseek.com" }
                        val apiKey = configHolder.llmApiKey

                        // Rebuild URL
                        val host = baseUrl.removePrefix("http://").removePrefix("https://")
                            .substringBefore("/").substringBefore(":")

                        val portStr = baseUrl.substringAfterLast(":")
                        val port = if (portStr.toIntOrNull() != null) portStr.toInt() else if (baseUrl.startsWith("https")) 443 else 80

                        val newUrl = original.url.newBuilder()
                            .scheme(if (baseUrl.startsWith("https")) "https" else "http")
                            .host(host)
                            .port(port)
                            .build()

                        val requestBuilder = original.newBuilder()
                            .url(newUrl)
                            .header("Content-Type", "application/json")

                        if (apiKey.isNotEmpty()) {
                            requestBuilder.header("Authorization", "Bearer $apiKey")
                        }

                        chain.proceed(requestBuilder.build())
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
    fun provideDLNAManager(@ApplicationContext context: Context): DLNAManager {
        return DLNAManager(context)
    }

    @Provides
    @Singleton
    fun provideDLNAPlayer(dlnaManager: DLNAManager): DLNAPlayer {
        return DLNAPlayer(dlnaManager)
    }

    @Provides
    @Singleton
    fun providePlayerRepository(dlnaPlayer: DLNAPlayer): PlayerRepository {
        return dlnaPlayer
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
    fun provideASRManager(
        sherpaASR: SherpaASR
    ): ASRManager {
        // ASR 模型: sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30
        return ASRManager(sherpaASR, "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30")
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
        llmRepository: LLMRepository?,
        playerRepository: PlayerRepository?
    ): IntentRouter {
        return IntentRouter(musicRepository, llmRepository, playerRepository)
    }

    @Provides
    @Singleton
    fun provideVoicePipeline(
        kws: SherpaKWS,
        vad: SherpaVAD,
        asrManager: ASRManager,
        tts: SherpaTTS,
        intentRouter: IntentRouter,
        audioCapture: AudioCapture,
        audioPlayer: AudioPlayer
    ): VoicePipeline {
        return VoicePipeline(
            config = PipelineConfig(),
            kws = kws,
            vad = vad,
            asrManager = asrManager,
            tts = tts,
            intentRouter = intentRouter,
            audioCapture = audioCapture,
            audioPlayer = audioPlayer
        )
    }
}
