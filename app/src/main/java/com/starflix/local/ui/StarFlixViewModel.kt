package com.starflix.local.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starflix.local.data.LibraryRepository
import com.starflix.local.data.MediaFolderListing
import com.starflix.local.data.MediaLibraryFolder
import com.starflix.local.data.MediaLibraryVolume
import com.starflix.local.data.MetadataRepository
import com.starflix.local.data.ProgressStore
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Destination {
    data object Home : Destination
    data object Movies : Destination
    data object Search : Destination
    data object Library : Destination
    data class Detail(val movieId: String) : Destination
    data class Player(val movieId: String) : Destination
}

data class LibraryUiState(
    val movies: List<Movie> = emptyList(),
    val libraryUri: Uri? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = ""
)

data class LibraryBrowserUiState(
    val loading: Boolean = false,
    val volumes: List<MediaLibraryVolume> = emptyList(),
    val selectedVolume: MediaLibraryVolume? = null,
    val currentPath: String = "",
    val directVideoCount: Int = 0,
    val totalVideoCount: Int = 0,
    val folders: List<MediaLibraryFolder> = emptyList(),
    val error: String? = null
)

class StarFlixViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryRepository = LibraryRepository(application)
    private val progressStore = ProgressStore(application)

    val metadataRepository = MetadataRepository(application)
    val thumbnailRepository = ThumbnailRepository(application)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _libraryBrowser = MutableStateFlow(LibraryBrowserUiState())
    val libraryBrowser: StateFlow<LibraryBrowserUiState> = _libraryBrowser.asStateFlow()

    private val _destination = MutableStateFlow<Destination>(Destination.Home)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private var detailReturnDestination: Destination = Destination.Home
    private var playerReturnDestination: Destination = Destination.Home
    private var libraryReturnDestination: Destination = Destination.Home

    val progress: StateFlow<Map<String, WatchProgress>> =
        progressStore.progress.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    init {
        restoreLibrary()
    }

    /** Selector SAF histórico, usado todavía en teléfono/tablet. */
    fun connectLibrary(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        viewModelScope.launch {
            libraryRepository.rememberLibrary(uri)
            scan(uri)
        }
    }

    fun showLibrary() {
        libraryReturnDestination = when (val current = _destination.value) {
            Destination.Home,
            Destination.Movies,
            Destination.Search -> current
            is Destination.Detail -> current
            Destination.Library -> libraryReturnDestination
            is Destination.Player -> libraryReturnDestination
        }
        _destination.value = Destination.Library
    }

    fun refreshLibraryVolumes() {
        viewModelScope.launch {
            _libraryBrowser.value = _libraryBrowser.value.copy(
                loading = true,
                error = null,
                selectedVolume = null,
                currentPath = "",
                folders = emptyList(),
                directVideoCount = 0,
                totalVideoCount = 0
            )

            runCatching {
                libraryRepository.listMediaVolumes()
            }.onSuccess { volumes ->
                _libraryBrowser.value = _libraryBrowser.value.copy(
                    loading = false,
                    volumes = volumes,
                    error = if (volumes.isEmpty()) {
                        "No se detectaron almacenamientos con videos disponibles."
                    } else null
                )
            }.onFailure { error ->
                _libraryBrowser.value = _libraryBrowser.value.copy(
                    loading = false,
                    error = error.message ?: "No se pudieron leer los almacenamientos."
                )
            }
        }
    }

    fun openLibraryVolume(volume: MediaLibraryVolume) {
        browseLibraryFolder(volume, "")
    }

    fun openLibraryFolder(folder: MediaLibraryFolder) {
        val volume = _libraryBrowser.value.selectedVolume ?: return
        browseLibraryFolder(volume, folder.relativePath)
    }

    fun useCurrentLibraryFolder() {
        val browser = _libraryBrowser.value
        val volume = browser.selectedVolume ?: return
        val uri = libraryRepository.mediaLibraryUri(
            volumeName = volume.mediaStoreName,
            relativePath = browser.currentPath
        )

        viewModelScope.launch {
            libraryRepository.rememberLibrary(uri)
            scan(uri)
            _destination.value = Destination.Home
        }
    }

    fun rescan() {
        _state.value.libraryUri?.let { uri ->
            viewModelScope.launch { scan(uri) }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun showHome() {
        _destination.value = Destination.Home
    }

    fun showMovies() {
        _destination.value = Destination.Movies
    }

    fun showSearch() {
        _destination.value = Destination.Search
    }

    fun openDetail(movieId: String) {
        detailReturnDestination = when (val current = _destination.value) {
            Destination.Home,
            Destination.Movies,
            Destination.Search -> current
            Destination.Library -> libraryReturnDestination
            is Destination.Detail,
            is Destination.Player -> detailReturnDestination
        }

        _destination.value = Destination.Detail(movieId)
    }

    fun play(movieId: String, returnTo: Destination? = null) {
        playerReturnDestination = returnTo ?: _destination.value
        _destination.value = Destination.Player(movieId)
    }

    fun back() {
        when (val current = _destination.value) {
            Destination.Home -> Unit
            Destination.Movies -> _destination.value = Destination.Home
            Destination.Search -> _destination.value = Destination.Home

            Destination.Library -> {
                val browser = _libraryBrowser.value
                val volume = browser.selectedVolume

                if (volume == null) {
                    _destination.value = libraryReturnDestination
                    return
                }

                if (browser.currentPath.isBlank()) {
                    _libraryBrowser.value = browser.copy(
                        selectedVolume = null,
                        currentPath = "",
                        folders = emptyList(),
                        directVideoCount = 0,
                        totalVideoCount = 0,
                        error = null
                    )
                    return
                }

                val parent = parentPath(browser.currentPath)
                browseLibraryFolder(volume, parent)
            }

            is Destination.Detail -> _destination.value = detailReturnDestination
            is Destination.Player -> _destination.value = playerReturnDestination
        }
    }

    fun movie(id: String): Movie? =
        _state.value.movies.firstOrNull { it.id == id }

    fun saveProgress(
        movieId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            progressStore.save(movieId, positionMs, durationMs)
        }
    }

    private fun browseLibraryFolder(
        volume: MediaLibraryVolume,
        path: String
    ) {
        viewModelScope.launch {
            _libraryBrowser.value = _libraryBrowser.value.copy(
                loading = true,
                selectedVolume = volume,
                currentPath = path,
                folders = emptyList(),
                directVideoCount = 0,
                totalVideoCount = 0,
                error = null
            )

            runCatching {
                libraryRepository.listMediaFolder(
                    volumeName = volume.mediaStoreName,
                    currentPath = path
                )
            }.onSuccess { listing: MediaFolderListing ->
                _libraryBrowser.value = _libraryBrowser.value.copy(
                    loading = false,
                    selectedVolume = volume,
                    currentPath = listing.currentPath,
                    directVideoCount = listing.directVideoCount,
                    totalVideoCount = listing.totalVideoCount,
                    folders = listing.folders,
                    error = null
                )
            }.onFailure { error ->
                _libraryBrowser.value = _libraryBrowser.value.copy(
                    loading = false,
                    selectedVolume = volume,
                    error = error.message ?: "No se pudo abrir esta ubicación."
                )
            }
        }
    }

    private fun parentPath(path: String): String {
        val parts = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        if (parts.size <= 1) return ""
        return parts.dropLast(1).joinToString("/", postfix = "/")
    }

    private fun restoreLibrary() {
        viewModelScope.launch {
            val saved = libraryRepository.savedLibraryUri.first()

            if (saved == null) {
                _state.value = _state.value.copy(loading = false)
            } else {
                scan(saved)
            }
        }
    }

    private suspend fun scan(uri: Uri) {
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            libraryUri = uri
        )

        runCatching {
            libraryRepository.scan(uri)
        }.onSuccess { movies ->
            _state.value = _state.value.copy(
                movies = movies,
                libraryUri = uri,
                loading = false,
                error = null
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                loading = false,
                error = error.message ?: "No se pudo abrir la biblioteca."
            )
        }
    }
}
