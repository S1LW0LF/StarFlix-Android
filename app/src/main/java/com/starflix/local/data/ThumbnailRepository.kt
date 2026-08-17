package com.starflix.local.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.starflix.local.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ThumbnailRepository(private val context: Context) {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val cacheSizeKb = (maxMemoryKb / 10).coerceAtLeast(16 * 1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount / 1024
    }

    private val slots = Semaphore(2)

    suspend fun thumbnail(
        movie: Movie,
        width: Int,
        height: Int
    ): Bitmap? {
        val key = "${movie.id}|${width}x${height}|${movie.lastModified}"
        cache.get(key)?.let { return it }

        return slots.withPermit {
            cache.get(key)?.let { return@withPermit it }

            val bitmap = withContext(Dispatchers.IO) {
                load(movie, width, height)
            }

            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    private fun load(movie: Movie, width: Int, height: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    movie.uri,
                    Size(width, height),
                    null
                )
            }.getOrNull()?.let { return it }
        }

        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, movie.uri)
                retriever.getFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}
