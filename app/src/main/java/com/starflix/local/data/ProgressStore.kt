package com.starflix.local.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.starflix.local.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class ProgressStore(private val context: Context) {
    private val progressKey = stringPreferencesKey("watch_progress_v1")

    val progress: Flow<Map<String, WatchProgress>> =
        context.starFlixDataStore.data.map { prefs ->
            decode(prefs[progressKey])
        }

    suspend fun save(
        movieId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L) return

        context.starFlixDataStore.edit { prefs ->
            val map = decode(prefs[progressKey]).toMutableMap()

            if (positionMs < 2_000L || positionMs >= durationMs - 20_000L) {
                map.remove(movieId)
            } else {
                map[movieId] = WatchProgress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis()
                )
            }

            prefs[progressKey] = encode(map)
        }
    }

    private fun encode(map: Map<String, WatchProgress>): String {
        val root = JSONObject()
        map.forEach { (id, progress) ->
            root.put(
                id,
                JSONObject()
                    .put("position", progress.positionMs)
                    .put("duration", progress.durationMs)
                    .put("updatedAt", progress.updatedAt)
            )
        }
        return root.toString()
    }

    private fun decode(raw: String?): Map<String, WatchProgress> {
        if (raw.isNullOrBlank()) return emptyMap()

        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.optJSONObject(id) ?: continue
                    put(
                        id,
                        WatchProgress(
                            positionMs = item.optLong("position"),
                            durationMs = item.optLong("duration"),
                            updatedAt = item.optLong("updatedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }
}
