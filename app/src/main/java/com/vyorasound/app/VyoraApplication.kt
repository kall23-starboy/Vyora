package com.vyorasound.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vyorasound.app.database.VyoraDataStore
import com.vyorasound.app.network.NetworkModule
import com.vyorasound.app.network.YouTubeSessionManager
import com.vyorasound.app.player.PlayerController
import com.vyorasound.app.repository.DataStoreLibraryRepository
import com.vyorasound.app.repository.InnerTubeMusicRepository

class AppContainer(application: Application) {
    private val dataStore = VyoraDataStore(
        context = application.applicationContext,
        moshi = NetworkModule.moshi
    )

    val youTubeSessionManager: YouTubeSessionManager by lazy {
        YouTubeSessionManager()
    }

    val musicRepository: InnerTubeMusicRepository by lazy {
        InnerTubeMusicRepository(
            apiService = NetworkModule.innerTubeApiService,
            authProvider = youTubeSessionManager
        )
    }

    val libraryRepository: DataStoreLibraryRepository by lazy {
        DataStoreLibraryRepository(dataStore)
    }

    val playerController: PlayerController by lazy {
        PlayerController(
            context = application.applicationContext,
            musicRepository = musicRepository,
            libraryRepository = libraryRepository
        )
    }
}

class VyoraApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(80L * 1024L * 1024L)
                    .build()
            }
            .crossfade(200)
            .build()
    }
}
