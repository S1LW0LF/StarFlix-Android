package com.starflix.local.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.starflix.local.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Volumen multimedia que StarFlix puede consultar directamente mediante
 * MediaStore. Esto permite que Android TV muestre almacenamiento interno y
 * unidades USB sin depender de una app externa de "Archivos".
 */
data class MediaLibraryVolume(
    val mediaStoreName: String,
    val title: String,
    val detail: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L
)

data class MediaLibraryFolder(
    val name: String,
    val relativePath: String,
    val videoCount: Int
)

data class MediaFolderListing(
    val currentPath: String,
    val directVideoCount: Int,
    val totalVideoCount: Int,
    val folders: List<MediaLibraryFolder>
)

class LibraryRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val libraryKey = stringPreferencesKey("library_tree_uri_v1")

    private data class ChildEntry(
        val documentId: String,
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val lastModified: Long
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private data class MediaRow(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val lastModified: Long,
        val relativePath: String
    )

    companion object {
        private const val MEDIA_LIBRARY_SCHEME = "starflix"
        private const val MEDIA_LIBRARY_AUTHORITY = "media-library"

        /*
         * Si la carpeta seleccionada ya tiene varias películas directamente
         * dentro, StarFlix la considera la biblioteca exacta.
         *
         * Esto se conserva para el selector SAF de móvil/tablet.
         */
        private const val DIRECT_LIBRARY_THRESHOLD = 5

        private val ignoredDirectoryNames = setOf(
            "\$recycle.bin",
            "system volume information",
            "lost.dir",
            "android",
            ".android_secure",
            ".trash",
            ".trashes",
            ".recycle",
            ".recycle.bin",
            "recycler"
        )
    }

    val savedLibraryUri: Flow<Uri?> =
        context.starFlixDataStore.data.map { prefs ->
            prefs[libraryKey]?.let(Uri::parse)
        }

    suspend fun rememberLibrary(uri: Uri) {
        context.starFlixDataStore.edit { prefs ->
            prefs[libraryKey] = uri.toString()
        }
    }

    suspend fun clearLibrary() {
        context.starFlixDataStore.edit { prefs ->
            prefs.remove(libraryKey)
        }
    }

    /**
     * Crea una URI interna persistible que identifica un volumen MediaStore y
     * una carpeta relativa. No es una URI de archivo: sirve para restaurar la
     * biblioteca elegida en TV después de reiniciar StarFlix.
     */
    fun mediaLibraryUri(volumeName: String, relativePath: String): Uri =
        Uri.Builder()
            .scheme(MEDIA_LIBRARY_SCHEME)
            .authority(MEDIA_LIBRARY_AUTHORITY)
            .appendQueryParameter("volume", volumeName)
            .appendQueryParameter("path", normalizeRelativePath(relativePath))
            .build()

    fun isMediaLibraryUri(uri: Uri): Boolean =
        uri.scheme == MEDIA_LIBRARY_SCHEME &&
            uri.authority == MEDIA_LIBRARY_AUTHORITY

    /**
     * Volúmenes actualmente montados que MediaStore conoce. En Android 10+
     * cada USB puede aparecer como un volumen independiente.
     */
    suspend fun listMediaVolumes(): List<MediaLibraryVolume> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext listOf(
                    MediaLibraryVolume(
                        mediaStoreName = "external",
                        title = "Almacenamiento del dispositivo",
                        detail = "Videos disponibles en Android",
                        isPrimary = true,
                        isRemovable = false
                    )
                )
            }

            val storageManager = context.getSystemService(StorageManager::class.java)
            val storageVolumes = storageManager?.storageVolumes.orEmpty()
            val mediaNames = MediaStore.getExternalVolumeNames(context)
                .toList()
                .sortedWith(
                    compareBy<String> {
                        if (it == MediaStore.VOLUME_EXTERNAL_PRIMARY) 0 else 1
                    }.thenBy { it.lowercase(Locale.ROOT) }
                )

            mediaNames.mapIndexed { index, mediaName ->
                val storageVolume = findStorageVolume(
                    mediaName = mediaName,
                    storageVolumes = storageVolumes
                )
                val primary = mediaName == MediaStore.VOLUME_EXTERNAL_PRIMARY ||
                    storageVolume?.isPrimary == true
                val removable = storageVolume?.isRemovable == true
                val (total, free) = storageStats(storageVolume)
                val description = storageVolume
                    ?.getDescription(context)
                    ?.trim()
                    .orEmpty()

                val title = when {
                    primary -> "Almacenamiento interno"
                    description.isNotBlank() && !description.equals("USB drive", true) -> description
                    description.isNotBlank() -> "Unidad USB"
                    removable -> "Unidad USB"
                    else -> "Almacenamiento externo ${index + 1}"
                }

                val identifier = storageVolume?.uuid
                    ?.takeIf { it.isNotBlank() }
                    ?: mediaName

                val detail = buildString {
                    append(if (removable) "USB / extraíble" else if (primary) "Memoria del televisor" else "Almacenamiento externo")
                    if (!primary && identifier.isNotBlank()) {
                        append(" · ")
                        append(identifier)
                    }
                }

                MediaLibraryVolume(
                    mediaStoreName = mediaName,
                    title = title,
                    detail = detail,
                    isPrimary = primary,
                    isRemovable = removable,
                    totalBytes = total,
                    freeBytes = free
                )
            }
        }

    /**
     * Devuelve únicamente carpetas que contienen al menos un video en ellas o
     * en alguna subcarpeta. Esto mantiene el navegador de TV limpio y evita
     * mostrar directorios del sistema que no sirven como biblioteca.
     */
    suspend fun listMediaFolder(
        volumeName: String,
        currentPath: String
    ): MediaFolderListing = withContext(Dispatchers.IO) {
        val normalizedCurrent = normalizeRelativePath(currentPath)
        val rows = queryMediaRows(volumeName)

        var directCount = 0
        var totalCount = 0
        val children = linkedMapOf<String, Int>()

        rows.forEach { row ->
            val path = normalizeRelativePath(row.relativePath)
            if (containsIgnoredDirectory(path)) return@forEach
            if (!path.startsWith(normalizedCurrent, ignoreCase = true)) return@forEach

            totalCount += 1
            val remainder = path.substring(normalizedCurrent.length)

            if (remainder.isBlank()) {
                directCount += 1
                return@forEach
            }

            val childName = remainder.substringBefore('/').trim()
            if (childName.isBlank() || shouldIgnoreDirectory(childName)) return@forEach

            children[childName] = (children[childName] ?: 0) + 1
        }

        MediaFolderListing(
            currentPath = normalizedCurrent,
            directVideoCount = directCount,
            totalVideoCount = totalCount,
            folders = children
                .map { (name, count) ->
                    MediaLibraryFolder(
                        name = name,
                        relativePath = normalizeRelativePath(normalizedCurrent + name),
                        videoCount = count
                    )
                }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
        )
    }

    /**
     * Escanea la biblioteca. Acepta tanto la URI SAF histórica de móvil/tablet
     * como la URI interna MediaStore que usa Android TV.
     */
    suspend fun scan(libraryUri: Uri): List<Movie> =
        if (isMediaLibraryUri(libraryUri)) {
            scanMediaLibrary(libraryUri)
        } else {
            scanDocumentTree(libraryUri)
        }

    private suspend fun scanMediaLibrary(libraryUri: Uri): List<Movie> =
        withContext(Dispatchers.IO) {
            val volumeName = libraryUri.getQueryParameter("volume")
                ?: error("La biblioteca no contiene un volumen válido.")
            val selectedPath = normalizeRelativePath(
                libraryUri.getQueryParameter("path").orEmpty()
            )

            queryMediaRows(volumeName)
                .asSequence()
                .filter { row ->
                    val path = normalizeRelativePath(row.relativePath)
                    path.startsWith(selectedPath, ignoreCase = true) &&
                        !containsIgnoredDirectory(path) &&
                        !shouldIgnoreFile(row.displayName)
                }
                .map(::toMovie)
                .distinctBy { it.uri.toString() }
                .sortedWith(
                    compareBy<Movie>(
                        { it.title.lowercase(Locale.getDefault()) },
                        { it.fileName.lowercase(Locale.getDefault()) }
                    )
                )
                .toList()
        }

    private suspend fun scanDocumentTree(treeUri: Uri): List<Movie> =
        withContext(Dispatchers.IO) {
            val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootEntries = queryChildren(treeUri, rootDocumentId)

            val directMovies = rootEntries
                .asSequence()
                .filterNot { it.isDirectory }
                .filter { isVideo(it.displayName, it.mimeType) }
                .filterNot { shouldIgnoreFile(it.displayName) }
                .map(::toMovie)
                .toList()

            val result = if (directMovies.size >= DIRECT_LIBRARY_THRESHOLD) {
                directMovies
            } else {
                val recursive = ArrayList<Movie>(320)
                recursive.addAll(directMovies)

                rootEntries
                    .asSequence()
                    .filter { it.isDirectory }
                    .filterNot { shouldIgnoreDirectory(it.displayName) }
                    .forEach { child ->
                        walk(treeUri, child.documentId, recursive)
                    }

                recursive
            }

            result
                .distinctBy { it.uri.toString() }
                .sortedWith(
                    compareBy<Movie>(
                        { it.title.lowercase(Locale.getDefault()) },
                        { it.fileName.lowercase(Locale.getDefault()) }
                    )
                )
        }

    private fun findStorageVolume(
        mediaName: String,
        storageVolumes: List<StorageVolume>
    ): StorageVolume? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageVolumes.firstOrNull {
                it.mediaStoreVolumeName?.equals(mediaName, ignoreCase = true) == true
            }?.let { return it }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            mediaName == MediaStore.VOLUME_EXTERNAL_PRIMARY
        ) {
            return storageVolumes.firstOrNull { it.isPrimary }
        }

        return storageVolumes.firstOrNull {
            it.uuid?.equals(mediaName, ignoreCase = true) == true
        }
    }

    private fun storageStats(volume: StorageVolume?): Pair<Long, Long> {
        if (volume == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0L to 0L
        }

        if (volume.state != Environment.MEDIA_MOUNTED &&
            volume.state != Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            return 0L to 0L
        }

        val directory = volume.directory ?: return 0L to 0L

        return runCatching {
            val stats = StatFs(directory.absolutePath)
            stats.totalBytes to stats.availableBytes
        }.getOrDefault(0L to 0L)
    }

    private fun queryMediaRows(volumeName: String): List<MediaRow> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(volumeName)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val result = ArrayList<MediaRow>(320)

        resolver.query(
            collection,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val modifiedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mime = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) {
                    cursor.getString(mimeIndex)
                } else null

                if (!isVideo(displayName, mime)) continue

                val relativePath = if (
                    pathIndex >= 0 && !cursor.isNull(pathIndex)
                ) {
                    cursor.getString(pathIndex).orEmpty()
                } else {
                    ""
                }

                result += MediaRow(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = displayName,
                    mimeType = mime,
                    sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else 0L,
                    lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                        // MediaStore.DATE_MODIFIED está expresado en segundos.
                        cursor.getLong(modifiedIndex) * 1_000L
                    } else 0L,
                    relativePath = relativePath
                )
            }
        }

        return result
    }

    private fun walk(
        treeUri: Uri,
        documentId: String,
        output: MutableList<Movie>
    ) {
        queryChildren(treeUri, documentId).forEach { child ->
            if (child.isDirectory) {
                if (!shouldIgnoreDirectory(child.displayName)) {
                    walk(treeUri, child.documentId, output)
                }
                return@forEach
            }

            if (
                isVideo(child.displayName, child.mimeType) &&
                !shouldIgnoreFile(child.displayName)
            ) {
                output += toMovie(child)
            }
        }
    }

    private fun queryChildren(
        treeUri: Uri,
        documentId: String
    ): List<ChildEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val result = ArrayList<ChildEntry>()

        resolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val sizeIndex = cursor.getColumnIndex(
                DocumentsContract.Document.COLUMN_SIZE
            )
            val modifiedIndex = cursor.getColumnIndex(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mime = cursor.getString(mimeIndex)

                result += ChildEntry(
                    documentId = childId,
                    uri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        childId
                    ),
                    displayName = displayName,
                    mimeType = mime,
                    sizeBytes = if (
                        sizeIndex >= 0 &&
                        !cursor.isNull(sizeIndex)
                    ) {
                        cursor.getLong(sizeIndex)
                    } else 0L,
                    lastModified = if (
                        modifiedIndex >= 0 &&
                        !cursor.isNull(modifiedIndex)
                    ) {
                        cursor.getLong(modifiedIndex)
                    } else 0L
                )
            }
        }

        return result
    }

    private fun toMovie(entry: ChildEntry): Movie {
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .uppercase(Locale.ROOT)

        return Movie(
            id = entry.uri.toString(),
            uri = entry.uri,
            fileName = entry.displayName,
            title = cleanTitle(entry.displayName),
            extension = extension.ifBlank { "VIDEO" },
            mimeType = entry.mimeType,
            sizeBytes = entry.sizeBytes,
            lastModified = entry.lastModified,
            year = extractYear(entry.displayName)
        )
    }

    private fun toMovie(row: MediaRow): Movie {
        val extension = row.displayName
            .substringAfterLast('.', "")
            .uppercase(Locale.ROOT)

        return Movie(
            id = row.uri.toString(),
            uri = row.uri,
            fileName = row.displayName,
            title = cleanTitle(row.displayName),
            extension = extension.ifBlank { "VIDEO" },
            mimeType = row.mimeType,
            sizeBytes = row.sizeBytes,
            lastModified = row.lastModified,
            year = extractYear(row.displayName)
        )
    }

    private fun normalizeRelativePath(path: String): String {
        val cleaned = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/")

        return if (cleaned.isBlank()) "" else "$cleaned/"
    }

    private fun containsIgnoredDirectory(path: String): Boolean =
        normalizeRelativePath(path)
            .split('/')
            .filter { it.isNotBlank() }
            .any(::shouldIgnoreDirectory)

    private fun shouldIgnoreDirectory(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)

        if (normalized.isBlank()) return true
        if (normalized.startsWith(".")) return true
        if (normalized in ignoredDirectoryNames) return true

        return false
    }

    private fun shouldIgnoreFile(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)

        if (normalized.startsWith(".")) return true

        if (
            normalized.startsWith("\$i") ||
            normalized.startsWith("\$r")
        ) {
            return true
        }

        return false
    }

    private fun isVideo(name: String, mime: String?): Boolean {
        if (mime?.startsWith("video/") == true) return true

        val extension = name
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)

        return extension in setOf(
            "mp4", "mkv", "webm", "mov", "m4v", "avi", "ts", "m2ts",
            "mts", "3gp", "mpg", "mpeg", "vob"
        )
    }

    private fun cleanTitle(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)

        return base
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
    }

    private fun extractYear(name: String): Int? =
        Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""")
            .find(name)
            ?.value
            ?.toIntOrNull()
}
