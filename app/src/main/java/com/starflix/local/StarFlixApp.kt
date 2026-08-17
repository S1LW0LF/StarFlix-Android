package com.starflix.local

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starflix.local.input.ControllerAction
import com.starflix.local.input.ControllerInput
import com.starflix.local.input.HomeControllerBridge
import com.starflix.local.input.MoviesControllerBridge
import com.starflix.local.ui.*
import com.starflix.local.ui.components.StarFlixBottomBar
import com.starflix.local.ui.components.StarFlixTopBar
import com.starflix.local.ui.screens.*
import kotlinx.coroutines.flow.collect

private sealed interface ReturnFocusTarget {
    data class Home(
        val section: HomeFocusSection,
        val movieId: String
    ) : ReturnFocusTarget

    data class Movies(val movieId: String) : ReturnFocusTarget
    data class Search(val movieId: String) : ReturnFocusTarget
}

@Composable
fun StarFlixApp(
    viewModel: StarFlixViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val libraryBrowser by viewModel.libraryBrowser.collectAsState()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val rootView = LocalView.current

    val homeNavFocus = remember { FocusRequester() }
    val moviesNavFocus = remember { FocusRequester() }
    val searchNavFocus = remember { FocusRequester() }
    val contentEntryFocus = remember { FocusRequester() }

    val tvMode =
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    // Para navegación con mando fuera de TV necesitamos saber si la barra
    // principal está abajo (móvil vertical/compacto) o arriba (tablet/horizontal).
    val compactControllerLayout = !tvMode && configuration.screenWidthDp < 720

    var controllerPrimed by remember { mutableStateOf(false) }
    val currentDestination by rememberUpdatedState(destination)
    val moviesControllerBridge = remember { MoviesControllerBridge() }
    val homeControllerBridge = remember { HomeControllerBridge() }

    /*
     * v0.11.7 mantiene dos estados separados:
     * - lastContentFocus: último elemento realmente enfocado en una pantalla.
     * - pendingReturnFocus: elemento al que debemos regresar después de
     *   Detalle/Player. Solo se consume cuando la pantalla origen confirma que
     *   volvió a enfocar ese elemento.
     */
    var lastContentFocus by remember { mutableStateOf<ReturnFocusTarget?>(null) }
    var pendingReturnFocus by remember { mutableStateOf<ReturnFocusTarget?>(null) }

    /*
     * v0.11.8: cuando BACK devuelve el foco a la barra superior, los eventos
     * Left/Right/Down deben pertenecer a la barra y no a los navegadores
     * internos de Home/Movies. MoviesControllerBridge conserva el último
     * índice enfocado aunque el foco ya esté arriba, por lo que sin esta marca
     * seguía consumiendo Left/Right y parecía que la barra estaba bloqueada.
     */
    var navigationBarActive by remember { mutableStateOf(false) }

    fun requestSelectedTopBarFocus(target: Destination) {
        navigationBarActive = true
        val requester = when (target) {
            Destination.Home -> homeNavFocus
            Destination.Movies -> moviesNavFocus
            Destination.Search -> searchNavFocus
            else -> homeNavFocus
        }
        runCatching { requester.requestFocus() }
    }

    fun clearReturnFocusForExplicitNavigation() {
        pendingReturnFocus = null
        lastContentFocus = null
    }

    val mediaPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var mediaPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val mediaPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaPermissionGranted = granted
    }

    LaunchedEffect(destination, mediaPermissionGranted) {
        if (destination is Destination.Library && mediaPermissionGranted) {
            viewModel.refreshLibraryVolumes()
        }
    }

    LaunchedEffect(Unit) {
        ControllerInput.actions.collect { action ->
            val activeDestination = currentDestination

            if (action == ControllerAction.Back) {
                if (
                    activeDestination is Destination.Home ||
                    activeDestination is Destination.Movies ||
                    activeDestination is Destination.Search
                ) {
                    /*
                     * Mando (TV o Android): B/Back desde el contenido no cambia
                     * de sección. Devuelve el foco a la pestaña seleccionada de
                     * la barra superior o inferior, según el layout actual.
                     * El Back táctil/sistema mantiene el comportamiento móvil
                     * normal en el BackHandler de abajo.
                     */
                    requestSelectedTopBarFocus(activeDestination)
                } else if (activeDestination !is Destination.Home) {
                    viewModel.back()
                }
                return@collect
            }

            // PlayerScreen escucha sus propios comandos de mando.
            if (activeDestination is Destination.Player) {
                return@collect
            }

            if (!controllerPrimed) {
                navigationBarActive = true
                runCatching { homeNavFocus.requestFocus() }
                controllerPrimed = true
                kotlinx.coroutines.yield()
            }

            // Si el foco está en la barra superior, el D-Pad debe navegar por
            // sus pestañas. No dejamos que MoviesControllerBridge/HomeBridge
            // consuman eventos usando el último elemento de contenido.
            if (navigationBarActive) {
                when (action) {
                    ControllerAction.Left -> focusManager.moveFocus(FocusDirection.Left)
                    ControllerAction.Right -> focusManager.moveFocus(FocusDirection.Right)

                    // En móvil vertical la navegación está abajo: UP vuelve al
                    // contenido. En tablet/horizontal/TV está arriba: DOWN entra.
                    ControllerAction.Up -> {
                        if (compactControllerLayout) {
                            navigationBarActive = false
                            runCatching { contentEntryFocus.requestFocus() }
                        } else {
                            focusManager.moveFocus(FocusDirection.Up)
                        }
                    }
                    ControllerAction.Down -> {
                        if (!compactControllerLayout) {
                            navigationBarActive = false
                            runCatching { contentEntryFocus.requestFocus() }
                        } else {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }

                    ControllerAction.Select -> {
                        val now = android.os.SystemClock.uptimeMillis()
                        rootView.dispatchKeyEvent(
                            KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                        )
                        rootView.dispatchKeyEvent(
                            KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                        )
                    }
                    ControllerAction.PlayPause -> Unit
                    ControllerAction.Back -> Unit
                }
                return@collect
            }

            if (
                activeDestination is Destination.Movies &&
                moviesControllerBridge.handle(action)
            ) {
                return@collect
            }

            if (
                activeDestination is Destination.Home &&
                homeControllerBridge.handle(action)
            ) {
                return@collect
            }

            when (action) {
                ControllerAction.Left -> focusManager.moveFocus(FocusDirection.Left)
                ControllerAction.Right -> focusManager.moveFocus(FocusDirection.Right)
                ControllerAction.Up -> focusManager.moveFocus(FocusDirection.Up)
                ControllerAction.Down -> focusManager.moveFocus(FocusDirection.Down)
                ControllerAction.Select -> {
                    val now = android.os.SystemClock.uptimeMillis()
                    rootView.dispatchKeyEvent(
                        KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                    )
                    rootView.dispatchKeyEvent(
                        KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                    )
                }
                ControllerAction.PlayPause -> Unit
                ControllerAction.Back -> Unit
            }
        }
    }

    // Móvil/tablet conserva el selector de carpetas nativo (SAF).
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.connectLibrary(uri)
        }
    }

    BackHandler(enabled = destination !is Destination.Home || tvMode) {
        if (tvMode && (
                destination is Destination.Home ||
                    destination is Destination.Movies ||
                    destination is Destination.Search
            )
        ) {
            requestSelectedTopBarFocus(destination)
        } else {
            viewModel.back()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = !tvMode && maxWidth < 720.dp

        val connectAction: () -> Unit = {
            if (tvMode) {
                viewModel.showLibrary()
            } else {
                folderPicker.launch(null)
            }
        }

        if (destination is Destination.Player) {
            val movieId = (destination as Destination.Player).movieId
            val movie = viewModel.movie(movieId)

            if (movie != null) {
                PlayerScreen(
                    movie = movie,
                    progress = progress[movie.id],
                    tvMode = tvMode,
                    onBack = viewModel::back,
                    onSaveProgress = { position, duration ->
                        viewModel.saveProgress(
                            movie.id,
                            position,
                            duration
                        )
                    }
                )
            }
            return@BoxWithConstraints
        }

        Scaffold(
            topBar = {
                StarFlixTopBar(
                    compact = compact,
                    tvMode = tvMode,
                    destination = destination,
                    onHome = {
                        clearReturnFocusForExplicitNavigation()
                        viewModel.showHome()
                    },
                    onMovies = {
                        clearReturnFocusForExplicitNavigation()
                        viewModel.showMovies()
                    },
                    onSearch = {
                        clearReturnFocusForExplicitNavigation()
                        viewModel.showSearch()
                    },
                    onConnect = connectAction,
                    homeFocusRequester = homeNavFocus,
                    moviesFocusRequester = moviesNavFocus,
                    searchFocusRequester = searchNavFocus,
                    contentFocusRequester = contentEntryFocus,
                    onNavigationFocusEntered = {
                        navigationBarActive = true
                    }
                )
            },
            bottomBar = {
                if (compact) {
                    StarFlixBottomBar(
                        destination = destination,
                        onHome = {
                            clearReturnFocusForExplicitNavigation()
                            viewModel.showHome()
                        },
                        onMovies = {
                            clearReturnFocusForExplicitNavigation()
                            viewModel.showMovies()
                        },
                        onSearch = {
                            clearReturnFocusForExplicitNavigation()
                            viewModel.showSearch()
                        },
                        homeFocusRequester = homeNavFocus,
                        moviesFocusRequester = moviesNavFocus,
                        searchFocusRequester = searchNavFocus,
                        contentFocusRequester = contentEntryFocus,
                        onNavigationFocusEntered = {
                            navigationBarActive = true
                        }
                    )
                }
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (
                    state.loading &&
                    state.movies.isEmpty() &&
                    destination !is Destination.Library
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                } else {
                    when (val current = destination) {
                        Destination.Home -> {
                            val restore = pendingReturnFocus as? ReturnFocusTarget.Home

                            HomeScreen(
                                movies = state.movies,
                                progress = progress,
                                metadataRepository = viewModel.metadataRepository,
                                thumbnailRepository = viewModel.thumbnailRepository,
                                compact = compact,
                                tvMode = tvMode,
                                focusModeEnabled = tvMode || controllerPrimed,
                                contentFocusRequester = contentEntryFocus,
                                topFocusRequester = homeNavFocus,
                                controllerBridge = homeControllerBridge,
                                restoreFocusSection = restore?.section,
                                restoreFocusMovieId = restore?.movieId,
                                onFocusRestored = { section, movieId ->
                                    val pending = pendingReturnFocus
                                    if (
                                        pending is ReturnFocusTarget.Home &&
                                        pending.section == section &&
                                        (movieId == null || pending.movieId == movieId)
                                    ) {
                                        pendingReturnFocus = null
                                    }
                                },
                                onContentFocusChanged = { section, movie ->
                                    navigationBarActive = false
                                    lastContentFocus = ReturnFocusTarget.Home(
                                        section = section,
                                        movieId = movie.id
                                    )
                                },
                                onConnect = connectAction,
                                onMovie = { movie ->
                                    pendingReturnFocus = lastContentFocus
                                    viewModel.openDetail(movie.id)
                                },
                                onPlay = { movie ->
                                    pendingReturnFocus = lastContentFocus
                                    viewModel.play(
                                        movie.id,
                                        returnTo = Destination.Home
                                    )
                                },
                                onContinuePlay = { movie ->
                                    pendingReturnFocus = lastContentFocus
                                    viewModel.play(
                                        movie.id,
                                        returnTo = Destination.Home
                                    )
                                },
                                onMovies = {
                                    clearReturnFocusForExplicitNavigation()
                                    viewModel.showMovies()
                                }
                            )
                        }

                        Destination.Movies -> {
                            val restore = pendingReturnFocus as? ReturnFocusTarget.Movies

                            MoviesScreen(
                                title = "Películas",
                                movies = state.movies,
                                progress = progress,
                                metadataRepository = viewModel.metadataRepository,
                                thumbnailRepository = viewModel.thumbnailRepository,
                                compact = compact,
                                tvMode = tvMode,
                                focusModeEnabled = tvMode || controllerPrimed,
                                contentFocusRequester = contentEntryFocus,
                                topFocusRequester = moviesNavFocus,
                                controllerBridge = moviesControllerBridge,
                                restoreFocusMovieId = restore?.movieId,
                                onFocusRestored = { movieId ->
                                    if (
                                        pendingReturnFocus is ReturnFocusTarget.Movies &&
                                        (pendingReturnFocus as ReturnFocusTarget.Movies).movieId == movieId
                                    ) {
                                        pendingReturnFocus = null
                                    }
                                },
                                onMovieFocusChanged = { movie ->
                                    navigationBarActive = false
                                    lastContentFocus = ReturnFocusTarget.Movies(movie.id)
                                },
                                onMovie = { movie ->
                                    pendingReturnFocus = ReturnFocusTarget.Movies(movie.id)
                                    viewModel.openDetail(movie.id)
                                }
                            )
                        }

                        Destination.Search -> {
                            val restore = pendingReturnFocus as? ReturnFocusTarget.Search

                            SearchScreen(
                                movies = state.movies,
                                query = state.query,
                                progress = progress,
                                metadataRepository = viewModel.metadataRepository,
                                thumbnailRepository = viewModel.thumbnailRepository,
                                compact = compact,
                                tvMode = tvMode,
                                focusModeEnabled = tvMode || controllerPrimed,
                                contentFocusRequester = contentEntryFocus,
                                topFocusRequester = searchNavFocus,
                                restoreFocusMovieId = restore?.movieId,
                                onFocusRestored = { movieId ->
                                    if (
                                        pendingReturnFocus is ReturnFocusTarget.Search &&
                                        (pendingReturnFocus as ReturnFocusTarget.Search).movieId == movieId
                                    ) {
                                        pendingReturnFocus = null
                                    }
                                },
                                onMovieFocusChanged = { movie ->
                                    navigationBarActive = false
                                    lastContentFocus = ReturnFocusTarget.Search(movie.id)
                                },
                                onQuery = viewModel::setQuery,
                                onMovie = { movie ->
                                    pendingReturnFocus = ReturnFocusTarget.Search(movie.id)
                                    viewModel.openDetail(movie.id)
                                }
                            )
                        }

                        Destination.Library -> LibraryScreen(
                            permissionGranted = mediaPermissionGranted,
                            browser = libraryBrowser,
                            contentFocusRequester = contentEntryFocus,
                            onRequestPermission = {
                                mediaPermissionLauncher.launch(mediaPermission)
                            },
                            onRefreshVolumes = viewModel::refreshLibraryVolumes,
                            onOpenVolume = viewModel::openLibraryVolume,
                            onOpenFolder = viewModel::openLibraryFolder,
                            onUseCurrentFolder = viewModel::useCurrentLibraryFolder
                        )

                        is Destination.Detail -> {
                            val movie = viewModel.movie(current.movieId)
                            if (movie != null) {
                                DetailScreen(
                                    movie = movie,
                                    progress = progress[movie.id],
                                    metadataRepository = viewModel.metadataRepository,
                                    thumbnailRepository = viewModel.thumbnailRepository,
                                    compact = compact,
                                    tvMode = tvMode,
                                    focusModeEnabled = tvMode || controllerPrimed,
                                    contentFocusRequester = contentEntryFocus,
                                    topFocusRequester = when (pendingReturnFocus) {
                                        is ReturnFocusTarget.Movies -> moviesNavFocus
                                        is ReturnFocusTarget.Search -> searchNavFocus
                                        else -> homeNavFocus
                                    },
                                    onBack = viewModel::back,
                                    onPlay = {
                                        viewModel.play(
                                            movie.id,
                                            returnTo = current
                                        )
                                    }
                                )
                            }
                        }

                        is Destination.Player -> Unit
                    }
                }
            }
        }
    }
}
