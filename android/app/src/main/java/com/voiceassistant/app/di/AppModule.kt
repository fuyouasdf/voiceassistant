package com.voiceassistant.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import java.io.File
import coil.ImageLoader
import com.voiceassistant.app.ui.music.JellyfinPlaybackReporter
import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.audio.AudioPlayer
import com.voiceassistant.core.dlna.DLNAManager
import com.voiceassistant.core.dlna.DLNAPlayer
import com.voiceassistant.core.ConversationContextManager
import com.voiceassistant.core.intent.IntentRouter
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.PlaybackReporter
import com.voiceassistant.core.pipeline.ASRManager
import com.voiceassistant.core.pipeline.PipelineConfig
import com.voiceassistant.core.pipeline.VoicePipeline
import com.voiceassistant.core.pipeline.WakeWordManager
import com.voiceassistant.core.playback.state.PlaybackStateManager
import com.voiceassistant.core.playback.state.PlaybackStateManagerImpl
import com.voiceassistant.core.sherpa.EndpointTimingConfig
import com.voiceassistant.core.sherpa.SherpaASR
import com.voiceassistant.core.sherpa.SherpaASRImpl
import com.voiceassistant.core.sherpa.SherpaKWS
import com.voiceassistant.core.sherpa.SherpaKWSImpl
import com.voiceassistant.core.sherpa.SherpaTTS
import com.voiceassistant.core.sherpa.SherpaTTSImpl
import com.voiceassistant.core.sherpa.SherpaVAD
import com.voiceassistant.core.sherpa.SherpaVADImpl
import com.voiceassistant.core.sherpa.StatefulVad
import com.voiceassistant.core.sherpa.StatefulVadImpl
import com.voiceassistant.data.local.AppDatabase
import com.voiceassistant.data.local.ChatMessageDao
import com.voiceassistant.data.local.ConfigDao
import com.voiceassistant.data.local.PlaylistDao
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.data.repository.LLMRepositoryImpl
import com.voiceassistant.data.repository.MusicRepositoryImpl
import com.voiceassistant.data.repository.MessageRepositoryImpl
import com.voiceassistant.data.repository.PlaybackQueueManagerImpl
import com.voiceassistant.data.repository.PlaylistRepositoryImpl
import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.data.repository.SettingsRepositoryImpl
import com.voiceassistant.domain.repository.ChatContextProvider
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.MessageRepository
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaybackQueueManager
import com.voiceassistant.domain.repository.PlayerRepository
import com.voiceassistant.domain.repository.PlaylistRepository
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
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun provideConfigDao(database: AppDatabase): ConfigDao {
        return database.configDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun providePlaylistRepository(playlistDao: PlaylistDao): PlaylistRepository {
        return PlaylistRepositoryImpl(playlistDao)
    }

    @Provides
    @Singleton
    fun providePlaybackQueueManager(): PlaybackQueueManager {
        return PlaybackQueueManagerImpl()
    }

    @Provides
    @Singleton
    fun provideMessageRepository(chatMessageDao: ChatMessageDao): MessageRepository {
        return MessageRepositoryImpl(chatMessageDao)
    }

    @Provides
    @Singleton
    fun provideJellyfinClient(@ApplicationContext context: Context, configHolder: ConfigHolder): JellyfinClient {
        return JellyfinClient(
            baseUrl = configHolder.jellyfinUrl.ifEmpty { "http://localhost:8096" },
            apiKey = configHolder.jellyfinApiKey,
            cacheDir = context.cacheDir
        )
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
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("voice_assistant_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context).build()
    }


    @Provides
    @Singleton
    fun provideLLMApi(@ApplicationContext context: Context, configHolder: ConfigHolder): LLMApi {
        // HTTP response cache - 10 MB
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = okhttp3.Cache(cacheDir, 10 * 1024 * 1024)

        // Use dynamic URL and API key from settings
        return Retrofit.Builder()
            .baseUrl("https://localhost/") // Placeholder, actual URL set in interceptor
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                okhttp3.OkHttpClient.Builder()
                    .cache(cache)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
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
    fun provideStreamingOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        jellyfinClient: JellyfinClient
    ): MusicRepository {
        return MusicRepositoryImpl(jellyfinClient)
    }

    @Provides
    @Singleton
    fun providePlaybackReporter(
        jellyfinPlaybackReporter: JellyfinPlaybackReporter
    ): PlaybackReporter {
        return jellyfinPlaybackReporter
    }

    @Provides
    @Singleton
    fun provideLLMRepository(
        llmApi: LLMApi,
        settingsRepository: SettingsRepository,
        streamingClient: okhttp3.OkHttpClient,
        messageRepository: MessageRepository
    ): LLMRepository {
        return LLMRepositoryImpl(llmApi, settingsRepository, streamingClient, messageRepository)
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
        // Use PipelineConfig defaults for endpoint timing
        // Endpoint timing values from PipelineConfig defaults
        val endpointTimingConfig = EndpointTimingConfig(
            rule1MustStartWithTrailingSilence = false,
            rule1TimeoutSec = 4.0f,
            rule1TrailingSilenceSec = 0.0f,
            rule2MustStartWithTrailingSilence = true,
            rule2TimeoutSec = 4.0f,
            rule2TrailingSilenceSec = 0.0f,
            rule3MustStartWithTrailingSilence = false,
            rule3TimeoutSec = 0.0f,
            rule3TrailingSilenceSec = 30.0f
        )
        // ASR 模型: sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30
        return ASRManager(sherpaASR, "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30", endpointTimingConfig)
    }

    @Provides
    @Singleton
    fun provideSherpaTTS(@ApplicationContext context: Context): SherpaTTS {
        return SherpaTTSImpl(context)
    }

    @Provides
    @Singleton
    fun provideWakeWordManager(@ApplicationContext context: Context): WakeWordManager {
        return WakeWordManager(context)
    }

    @Provides
    @Singleton
    fun provideVoicePipeline(
        @ApplicationContext context: Context,
        kws: SherpaKWS,
        vad: SherpaVAD,
        asrManager: ASRManager,
        tts: SherpaTTS,
        intentRouter: IntentRouter,
        audioCapture: AudioCapture,
        audioPlayer: AudioPlayer,
        configHolder: ConfigHolder,
        wakeWordManager: WakeWordManager
    ): VoicePipeline {
        return VoicePipeline(
            config = PipelineConfig(),
            kws = kws,
            vad = vad,
            asrManager = asrManager,
            tts = tts,
            intentRouter = intentRouter,
            audioCapture = audioCapture,
            audioPlayer = audioPlayer,
            ttsEnabledProvider = { configHolder.ttsEnabled },
            ttsSpeedProvider = { configHolder.ttsSpeed },
            ttsPitchProvider = { configHolder.ttsPitch },
            wakeSensitivityProvider = { configHolder.wakeSensitivity },
            wakeWordManager = wakeWordManager,
            statefulVadFactory = { StatefulVadImpl(context) }
        )
    }

    // Playback module providers
    @Provides
    @Singleton
    fun providePlaybackStateManager(): PlaybackStateManager {
        return PlaybackStateManagerImpl()
    }

    @Provides
    @Singleton
    fun provideConversationContextManager(
        messageRepository: MessageRepository,
        configHolder: ConfigHolder
    ): ConversationContextManager {
        val manager = ConversationContextManager(messageRepository)
        // Initialize with settings from ConfigHolder
        manager.updateSettings(
            maxContextCount = configHolder.maxContextCount,
            maxTurnsBeforeReset = configHolder.maxTurnsBeforeReset,
            conversationTimeoutSeconds = configHolder.conversationTimeoutSeconds,
            summarizationThreshold = configHolder.summarizationThreshold
        )
        return manager
    }

    @Provides
    @Singleton
    fun provideConversationContext(
        conversationContextManager: ConversationContextManager
    ): ChatContextProvider {
        return conversationContextManager
    }
}
