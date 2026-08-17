package com.starflix.local.model

import android.net.Uri

data class Movie(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val title: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val year: Int?
)

data class MediaTechnicalInfo(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val dolbyVision: Boolean = false,
    val dolbyAtmos: Boolean = false
) {
    /*
     * Etiqueta de resolución orientada al usuario.
     *
     * Se usa principalmente el ancho para contemplar películas scope:
     * 3840×1600 sigue perteneciendo a la clase 4K,
     * 1920×800 sigue perteneciendo a la clase Full HD.
     */
    val resolutionLabel: String
        get() = when {
            width >= 7_000 || height >= 4_000 -> "8K"
            width >= 3_500 || height >= 2_000 -> "4K"
            width >= 2_400 || height >= 1_400 -> "QHD"
            width >= 1_700 || height >= 950 -> "FULL HD"
            width >= 1_100 || height >= 650 -> "HD"
            width > 0 || height > 0 -> "SD"
            else -> ""
        }

    /*
     * La UI ya no muestra:
     * - MKV / MP4
     * - HEVC / H264 / AV1
     *
     * Solo dejamos el codec de AUDIO y las capacidades Dolby detectadas.
     *
     * Ejemplos:
     * TRUEHD · DOLBY VISION • DOLBY ATMOS
     * E-AC-3 · DOLBY ATMOS
     * AAC · DOLBY VISION
     * AAC
     */
    val audioDolbyLabel: String
        get() {
            val dolby = buildList {
                if (dolbyVision) add("DOLBY VISION")
                if (dolbyAtmos) add("DOLBY ATMOS")
            }

            return listOf(
                audioCodec,
                dolby.joinToString(" • ")
            )
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
}

data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val fraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
