package com.drunkbatya.vunder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.cache.SavedTrackCoverFetcher
import com.drunkbatya.vunder.core.cache.SavedTrackCoverKeyer
import com.drunkbatya.vunder.core.session.SessionLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VUnderApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var sessionLifecycle: SessionLifecycle

    @Inject
    lateinit var downloads: DownloadCenter

    override fun onCreate() {
        super.onCreate()
        sessionLifecycle.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(SavedTrackCoverKeyer())
            add(SavedTrackCoverFetcher.Factory(downloads))
        }
        .build()
}
