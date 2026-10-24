package com.juying.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.juying.tv.data.remote.Http
import com.juying.tv.data.source.SourceManager
import okhttp3.Call

class TvApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SourceManager.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .callFactory { Call.Factory(Http.client::newCall) }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
}
