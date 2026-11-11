package com.utsavlens.app.di

import android.content.Context
import android.util.Log
import com.utsavlens.app.jobs.EngineRegistry
import com.utsavlens.app.jobs.JobController
import com.utsavlens.app.jobs.VideoJobs
import com.utsavlens.app.repo.HistoryRepository
import com.utsavlens.app.repo.ThemeRepository
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DefaultDispatchers
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.secure.SecretStore
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.data.secure.KeystoreCipher
import com.utsavlens.engine.api.ImageEditProvider
import com.utsavlens.engine.cloud.CloudEngine
import com.utsavlens.engine.cloud.GeminiImageProvider
import com.utsavlens.engine.cloud.UsageCounter
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.engine.lite.LiteEngine
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.segmentation.MaskProvider
import com.utsavlens.segmentation.MediaPipeMaskProvider
import com.utsavlens.video.VideoExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun dispatchers(): DispatcherProvider = DefaultDispatchers

    /** Warnings only, and callers never pass secrets (ApiKey.toString() is masked). */
    @Provides @Singleton
    fun logger(): AppLogger = AppLogger { tag, message -> Log.w("UtsavLens/$tag", message) }

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context, d: DispatcherProvider, logger: AppLogger): SettingsStore =
        SettingsStore(settingsFile(context), d.io, logger = logger)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.build(context)

    @Provides @Singleton
    fun mediaStoreWriter(@ApplicationContext context: Context): MediaStoreWriter = MediaStoreWriter(context)

    @Provides @Singleton
    fun maskProvider(@ApplicationContext context: Context, d: DispatcherProvider, logger: AppLogger): MaskProvider =
        MediaPipeMaskProvider(context, d.default, logger)

    @Provides @Singleton
    fun themeRepository(@ApplicationContext context: Context, d: DispatcherProvider, logger: AppLogger): ThemeRepository =
        ThemeRepository(context, d, logger)

    @Provides @Singleton
    fun liteEngine(
        @ApplicationContext context: Context, themes: ThemeRepository, masks: MaskProvider,
        writer: MediaStoreWriter, d: DispatcherProvider, logger: AppLogger,
    ): LiteEngine = LiteEngine(context, themes, masks, writer, d, logger)

    /** Keys are stored in files/secure (excluded from backups) under a Keystore-backed AES-GCM key. */
    @Provides @Singleton
    fun secretStore(@ApplicationContext context: Context, logger: AppLogger): SecretStore =
        SecretStore(File(context.filesDir, "secure"), KeystoreCipher(), logger)

    /** No logging interceptor on purpose: requests carry the user's API key and photo. */
    @Provides @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides @Singleton
    fun imageProviders(client: OkHttpClient): Map<String, @JvmSuppressWildcards ImageEditProvider> =
        listOf<ImageEditProvider>(GeminiImageProvider(client)).associateBy { it.id }

    @Provides @Singleton
    fun usageCounter(db: AppDatabase): UsageCounter = UsageCounter { providerId ->
        db.cloudUsageDao().increment(LocalDate.now().toString(), providerId)
    }

    @Provides @Singleton
    fun cloudEngine(
        @ApplicationContext context: Context, themes: ThemeRepository,
        providers: Map<String, @JvmSuppressWildcards ImageEditProvider>, secrets: SecretStore, settings: SettingsStore,
        usage: UsageCounter, writer: MediaStoreWriter, d: DispatcherProvider, logger: AppLogger,
    ): CloudEngine = CloudEngine(context, themes, providers, secrets, settings, usage, writer, d, logger)

    @Provides @Singleton
    fun engineRegistry(lite: LiteEngine, cloud: CloudEngine): EngineRegistry = EngineRegistry(listOf(lite, cloud))

    @Provides @Singleton
    fun jobController(
        @ApplicationContext context: Context, db: AppDatabase, engines: EngineRegistry,
        d: DispatcherProvider, logger: AppLogger,
    ): JobController = JobController(context, db, engines, d, logger)

    @Provides @Singleton
    fun historyRepository(db: AppDatabase, writer: MediaStoreWriter, d: DispatcherProvider): HistoryRepository =
        HistoryRepository(db, writer, d)

    @Provides @Singleton
    fun videoExporter(@ApplicationContext context: Context, themes: ThemeRepository, writer: MediaStoreWriter, d: DispatcherProvider): VideoExporter =
        VideoExporter(context, themes, writer, d)

    @Provides @Singleton
    fun videoJobs(controller: JobController, exporter: VideoExporter, db: AppDatabase): VideoJobs = VideoJobs(controller, exporter, db)

    fun settingsFile(context: Context): File = File(File(context.filesDir, "datastore"), "settings.json")
}
