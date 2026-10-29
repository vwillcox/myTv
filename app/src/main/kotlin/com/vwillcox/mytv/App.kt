package com.vwillcox.mytv

import android.app.Application
import android.content.Context
import com.vwillcox.mytv.data.AppRepository
import com.vwillcox.mytv.data.ConfigStore
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.Library
import com.vwillcox.mytv.data.Tmdb
import com.vwillcox.mytv.data.TvListings
import com.vwillcox.mytv.data.baseHttpClient
import com.vwillcox.mytv.system.Installer
import com.vwillcox.mytv.system.SystemControl
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class App : Application(), SingletonImageLoader.Factory {
    lateinit var store: ConfigStore
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var library: Library
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var feed: Feed
        private set
    lateinit var system: SystemControl
        private set
    lateinit var installer: Installer
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        http = baseHttpClient()
        library = Library(this)
        apps = AppRepository(this, scope)
        system = SystemControl(this, apps, store)
        installer = Installer(this, http)
        feed = Feed(store, library, Tmdb(http), TvListings(this), apps, scope)

        // The settings that change what the rows hold, rather than how they look, rebuild them.
        scope.launch {
            store.config.map { listOf(it.watching?.id, it.sources, it.rows, it.popular, it.dismissed, it.hiddenChannels) }
                .distinctUntilChanged().drop(1).collect { feed.refresh() }
        }
        // AllMedia says when its answers change (something played, someone added); a burst of those is one refresh.
        scope.launch { library.changes().collectLatest { delay(1500); feed.libraryChanged() } }
        feed.refresh()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}

val Context.app: App get() = applicationContext as App
