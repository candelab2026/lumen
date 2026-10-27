package com.lumen.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class TvApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
            .build()
}
