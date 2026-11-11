package com.utsavlens.app.di

import android.content.Context
import android.util.Log
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DefaultDispatchers
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.segmentation.MaskProvider
import com.utsavlens.segmentation.MediaPipeMaskProvider
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

    fun settingsFile(context: Context): File = File(File(context.filesDir, "datastore"), "settings.json")
}
