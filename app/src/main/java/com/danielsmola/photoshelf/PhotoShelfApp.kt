package com.danielsmola.photoshelf

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.danielsmola.photoshelf.data.MediaActions
import com.danielsmola.photoshelf.data.Net
import com.danielsmola.photoshelf.data.PhotoRepository
import com.danielsmola.photoshelf.data.SecureStore
import com.danielsmola.photoshelf.data.Settings
import com.danielsmola.photoshelf.data.SynologyClient

/** Simple app-wide service locator. */
object Graph {
    lateinit var store: SecureStore
    lateinit var settings: Settings
    lateinit var client: SynologyClient
    lateinit var repo: PhotoRepository
    lateinit var actions: MediaActions

    fun init(context: Context) {
        store = SecureStore(context)
        settings = Settings(context)
        client = SynologyClient(store, settings)
        repo = PhotoRepository(context, client)
        actions = MediaActions(context, client)
    }
}

class PhotoShelfApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .callFactory(Net.routing)
        // NAS thumbnails don't send cache headers; keep them anyway so the library works offline.
        .respectCacheHeaders(false)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbs"))
                .maxSizeBytes(1024L * 1024 * 1024)
                .build()
        }
        .crossfade(150)
        .build()
}
