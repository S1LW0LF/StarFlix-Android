package com.starflix.local.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.media3.common.MimeTypes
import com.starflix.local.model.MediaTechnicalInfo
import com.starflix.local.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MetadataRepository(private val context: Context) {
    private val cache = ConcurrentHashMap<String, MediaTechnicalInfo>()
    private val slots = Semaphore(2)

    suspend fun metadata(movie: Movie): MediaTechnicalInfo {
        val cacheKey = "${movie.id}|${movie.sizeBytes}|${movie.lastModified}"
        cache[cacheKey]?.let { return it }

        return slots.withPermit {
            cache[cacheKey]?.let { return@withPermit it }

            val info = withContext(Dispatchers.IO) {
                inspect(movie)
            }

            cache[cacheKey] = info
            info
        }
    }

    private fun inspect(movie: Movie): MediaTechnicalInfo {
        var durationMs = 0L
        var width = 0
        var height = 0
        var videoMime = ""
        var videoCodecsString = ""
        var audioMime = ""
        var audioCodecsString = ""
        var hasTrueHdTrack = false

        var dolbyVision = explicitDolbyVisionInName(movie.fileName)
        var dolbyAtmos = explicitDolbyAtmosInName(movie.fileName)

        runCatching {
            val extractor = MediaExtractor()

            try {
                extractor.setDataSource(
                    context,
                    movie.uri,
                    emptyMap<String, String>()
                )

                var firstVideoTrack = -1

                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format
                        .getString(MediaFormat.KEY_MIME)
                        .orEmpty()

                    val codecsString = readCodecsString(format)

                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = maxOf(
                            durationMs,
                            format.getLong(MediaFormat.KEY_DURATION) / 1_000L
                        )
                    }

                    when {
                        mime.startsWith("video/") -> {
                            if (firstVideoTrack < 0) {
                                firstVideoTrack = index
                            }

                            if (videoMime.isBlank()) {
                                videoMime = mime
                                videoCodecsString = codecsString
                                width = format.getIntegerOrZero(
                                    MediaFormat.KEY_WIDTH
                                )
                                height = format.getIntegerOrZero(
                                    MediaFormat.KEY_HEIGHT
                                )
                            }

                            if (isDolbyVisionFormat(mime, codecsString, format)) {
                                dolbyVision = true
                            }
                        }

                        mime.startsWith("audio/") -> {
                            if (
                                mime.equals(
                                    MimeTypes.AUDIO_TRUEHD,
                                    ignoreCase = true
                                )
                            ) {
                                hasTrueHdTrack = true
                            }

                            if (audioMime.isBlank()) {
                                audioMime = mime
                                audioCodecsString = codecsString
                            }

                            if (isAtmosFormat(mime, codecsString, format)) {
                                dolbyAtmos = true

                                // Si la pista Atmos no es la primera pista,
                                // mostramos su codec en la insignia técnica.
                                audioMime = mime
                                audioCodecsString = codecsString
                            }
                        }
                    }
                }

                /*
                 * Dolby Vision en MKV no siempre llega como video/dolby-vision
                 * desde MediaExtractor. Para HEVC hacemos una comprobación extra:
                 * buscamos RPU NAL type 62 en unos pocos access units.
                 *
                 * No lee toda la película; como máximo revisa ~8 muestras.
                 */
                if (
                    !dolbyVision &&
                    firstVideoTrack >= 0 &&
                    videoCodecLabel(videoMime, videoCodecsString) == "HEVC"
                ) {
                    dolbyVision = containsDolbyVisionRpu(
                        extractor = extractor,
                        trackIndex = firstVideoTrack
                    )
                }
            } finally {
                extractor.release()
            }
        }

        /*
         * TRUEHD + ATMOS - FALLBACK RAW
         * --------------------------------
         * Algunos extractores Android/OnePlus no exponen la pista TrueHD de
         * ciertos MKV o la exponen sin la metadata Atmos. En ese caso hacemos
         * una lectura binaria limitada del archivo local y buscamos el major
         * sync TrueHD real (F8 72 6F BA). FFmpeg usa el bit 0 del byte 25 y el
         * nibble alto del byte 26 para reconocer los bloques de extensión que
         * aparecieron con los substreams Atmos.
         *
         * No se lee toda la película: como máximo 64 MiB y el resultado queda
         * en caché por archivo/tamaño/mtime.
         */
        val shouldRawScanTrueHd =
            hasTrueHdTrack ||
            (
                audioMime.isBlank() &&
                movie.extension.uppercase(Locale.ROOT) in setOf(
                    "MKV", "M2TS", "MTS", "TS"
                )
            )

        if (!dolbyAtmos && shouldRawScanTrueHd) {
            val rawTrueHd = scanRawTrueHd(movie)

            if (rawTrueHd.trueHd) {
                hasTrueHdTrack = true

                // Si encontramos el stream TrueHD directamente en el archivo,
                // usamos TRUEHD como codec visible aunque MediaExtractor haya
                // omitido ese track o haya mostrado primero el core AC-3.
                if (rawTrueHd.atmos || audioMime.isBlank()) {
                    audioMime = MimeTypes.AUDIO_TRUEHD
                    audioCodecsString = "truehd"
                }
            }

            if (rawTrueHd.atmos) {
                dolbyAtmos = true
            }
        }

        if (durationMs <= 0L || width <= 0 || height <= 0) {
            runCatching {
                val retriever = MediaMetadataRetriever()

                try {
                    retriever.setDataSource(context, movie.uri)

                    if (durationMs <= 0L) {
                        durationMs = retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION
                            )
                            ?.toLongOrNull()
                            ?: 0L
                    }

                    if (width <= 0) {
                        width = retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                            )
                            ?.toIntOrNull()
                            ?: 0
                    }

                    if (height <= 0) {
                        height = retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                            )
                            ?.toIntOrNull()
                            ?: 0
                    }
                } finally {
                    retriever.release()
                }
            }
        }

        return MediaTechnicalInfo(
            durationMs = durationMs,
            width = width,
            height = height,
            videoCodec = videoCodecLabel(
                videoMime,
                videoCodecsString
            ),
            audioCodec = audioCodecLabel(
                audioMime,
                audioCodecsString
            ),
            dolbyVision = dolbyVision,
            dolbyAtmos = dolbyAtmos
        )
    }

    private fun readCodecsString(format: MediaFormat): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ""
        }

        return runCatching {
            if (format.containsKey(MediaFormat.KEY_CODECS_STRING)) {
                format.getString(MediaFormat.KEY_CODECS_STRING).orEmpty()
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun isDolbyVisionFormat(
        mime: String,
        codecsString: String,
        format: MediaFormat
    ): Boolean {
        if (mime.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true)) {
            return true
        }

        val codec = codecsString.lowercase(Locale.ROOT)

        if (
            codec.contains("dvhe") ||
            codec.contains("dvh1") ||
            codec.contains("dvav") ||
            codec.contains("dva1") ||
            codec.contains("dovi") ||
            codec.contains("dolby")
        ) {
            return true
        }

        val raw = format.toString().lowercase(Locale.ROOT)

        return (
            raw.contains("dolby vision") ||
            raw.contains("dovi") ||
            raw.contains("dvhe") ||
            raw.contains("dvh1")
        )
    }

    private fun isAtmosFormat(
        mime: String,
        codecsString: String,
        format: MediaFormat
    ): Boolean {
        if (mime.equals(MimeTypes.AUDIO_E_AC3_JOC, ignoreCase = true)) {
            return true
        }

        val codec = codecsString.lowercase(Locale.ROOT)

        if (
            codec.contains("ec+3") ||
            codec.contains("eac3_joc") ||
            codec.contains("e-ac-3 joc")
        ) {
            return true
        }

        val raw = format.toString().lowercase(Locale.ROOT)

        return (
            raw.contains("dolby atmos") ||
            raw.contains("atmos") ||
            raw.contains("eac3_joc") ||
            raw.contains("e-ac-3 joc") ||
            raw.contains("joint object coding")
        )
    }

    private fun explicitDolbyVisionInName(name: String): Boolean =
        Regex(
            """(?i)(dolby[\s._-]*vision|dovi|dvhe|dvh1)"""
        ).containsMatchIn(name)

    private fun explicitDolbyAtmosInName(name: String): Boolean =
        Regex(
            """(?i)(dolby[\s._-]*atmos|atmos)"""
        ).containsMatchIn(name)

    private fun containsDolbyVisionRpu(
        extractor: MediaExtractor,
        trackIndex: Int
    ): Boolean {
        return runCatching {
            extractor.selectTrack(trackIndex)
            extractor.seekTo(
                0L,
                MediaExtractor.SEEK_TO_CLOSEST_SYNC
            )

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)

            repeat(8) {
                buffer.clear()

                val size = extractor.readSampleData(buffer, 0)
                if (size <= 0) {
                    return@runCatching false
                }

                val bytes = ByteArray(size)
                buffer.position(0)
                buffer.get(bytes)

                if (containsHevcNalType62(bytes)) {
                    return@runCatching true
                }

                if (!extractor.advance()) {
                    return@runCatching false
                }
            }

            false
        }.getOrDefault(false).also {
            runCatching {
                extractor.unselectTrack(trackIndex)
            }
        }
    }

    private fun containsHevcNalType62(data: ByteArray): Boolean {
        // Annex-B: 00 00 01 or 00 00 00 01
        var i = 0

        while (i + 5 < data.size) {
            val startLength = when {
                data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() &&
                data[i + 2] == 1.toByte() -> 3

                data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() &&
                data[i + 3] == 1.toByte() -> 4

                else -> 0
            }

            if (startLength > 0) {
                val nalIndex = i + startLength

                if (nalIndex < data.size) {
                    val nalType =
                        (data[nalIndex].toInt() ushr 1) and 0x3F

                    // Dolby Vision RPU commonly uses HEVC UNSPEC62.
                    if (nalType == 62) {
                        return true
                    }
                }

                i = nalIndex + 1
            } else {
                i++
            }
        }

        return false
    }


    private data class RawTrueHdResult(
        val trueHd: Boolean,
        val atmos: Boolean
    )

    private fun scanRawTrueHd(movie: Movie): RawTrueHdResult {
        return runCatching {
            val input = context.contentResolver.openInputStream(movie.uri)
                ?: return@runCatching RawTrueHdResult(false, false)

            input.use { stream ->
                val chunk = ByteArray(1024 * 1024)
                var overlap = ByteArray(32)
                var scanned = 0L
                val maxScan = 64L * 1024L * 1024L
                var foundTrueHd = false

                while (scanned < maxScan) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    scanned += read

                    val merged = ByteArray(overlap.size + read)
                    System.arraycopy(overlap, 0, merged, 0, overlap.size)
                    System.arraycopy(chunk, 0, merged, overlap.size, read)

                    val result = inspectTrueHdBytes(merged)
                    if (result.atmos) {
                        return@runCatching result
                    }
                    if (result.trueHd) {
                        foundTrueHd = true
                    }

                    val keep = minOf(32, merged.size)
                    overlap = merged.copyOfRange(merged.size - keep, merged.size)
                }

                RawTrueHdResult(foundTrueHd, false)
            }
        }.getOrDefault(RawTrueHdResult(false, false))
    }

    private fun inspectTrueHdBytes(data: ByteArray): RawTrueHdResult {
        var i = 0
        var foundTrueHd = false

        while (i + 28 < data.size) {
            val majorSync =
                (data[i].toInt() and 0xFF) == 0xF8 &&
                (data[i + 1].toInt() and 0xFF) == 0x72 &&
                (data[i + 2].toInt() and 0xFF) == 0x6F &&
                (data[i + 3].toInt() and 0xFF) == 0xBA

            if (majorSync) {
                foundTrueHd = true

                // Mismo criterio que mlp_get_major_sync_size() de FFmpeg:
                // has_extension = buf[25] & 1
                // extensions    = buf[26] >> 4
                val hasExtension =
                    (data[i + 25].toInt() and 0x01) != 0
                val extensionBlocks =
                    (data[i + 26].toInt() ushr 4) and 0x0F

                if (hasExtension && extensionBlocks > 0) {
                    return RawTrueHdResult(true, true)
                }
            }

            i++
        }

        return RawTrueHdResult(foundTrueHd, false)
    }

    private fun MediaFormat.getIntegerOrZero(key: String): Int =
        if (containsKey(key)) {
            runCatching { getInteger(key) }.getOrDefault(0)
        } else 0

    private fun videoCodecLabel(
        mime: String,
        codecsString: String
    ): String {
        val codec = codecsString.lowercase(Locale.ROOT)

        return when {
            mime == MimeTypes.VIDEO_DOLBY_VISION -> "HEVC"
            mime == MimeTypes.VIDEO_H265 -> "HEVC"
            mime == MimeTypes.VIDEO_H264 -> "H264"
            mime == MimeTypes.VIDEO_AV1 -> "AV1"
            mime == MimeTypes.VIDEO_VP9 -> "VP9"
            mime == MimeTypes.VIDEO_VP8 -> "VP8"

            codec.contains("hev1") ||
            codec.contains("hvc1") ||
            codec.contains("dvhe") ||
            codec.contains("dvh1") -> "HEVC"

            codec.contains("avc1") ||
            codec.contains("avc3") -> "H264"

            codec.contains("av01") -> "AV1"
            codec.contains("vp09") -> "VP9"

            else -> mime
                .substringAfter('/')
                .uppercase(Locale.ROOT)
        }
    }

    private fun audioCodecLabel(
        mime: String,
        codecsString: String
    ): String {
        val codec = codecsString.lowercase(Locale.ROOT)

        return when {
            mime == MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
            mime == MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
            mime == MimeTypes.AUDIO_AC3 -> "AC-3"
            mime == MimeTypes.AUDIO_TRUEHD -> "TRUEHD"
            mime == MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
            mime == MimeTypes.AUDIO_DTS -> "DTS"
            mime == MimeTypes.AUDIO_AAC -> "AAC"
            mime == MimeTypes.AUDIO_FLAC -> "FLAC"
            mime == MimeTypes.AUDIO_OPUS -> "OPUS"
            mime == MimeTypes.AUDIO_MPEG -> "MP3"

            codec.contains("ec+3") ||
            codec.contains("ec-3") -> "E-AC-3"

            codec.contains("ac-3") -> "AC-3"
            codec.contains("mlpa") -> "TRUEHD"
            codec.contains("mp4a") -> "AAC"

            else -> mime
                .substringAfter('/')
                .uppercase(Locale.ROOT)
        }
    }
}
