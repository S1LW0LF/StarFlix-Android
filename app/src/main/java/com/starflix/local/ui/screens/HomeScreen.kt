package com.starflix.local.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starflix.local.data.MetadataRepository
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.input.ControllerAction
import com.starflix.local.input.HomeControllerBridge
import com.starflix.local.model.MediaTechnicalInfo
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import com.starflix.local.ui.components.MovieArtwork
import com.starflix.local.ui.components.MovieCard
import com.starflix.local.ui.components.controllerFocus
import com.starflix.local.ui.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeFocusSection {
    Hero,
    ContinueWatching,
    Library
}

/*
 * Controlador pequeño que permite a HomeScreen pedir foco a una LazyRow aunque
 * su tarjeta destino todavía no esté compuesta. MediaRow registra aquí una
 * acción que primero desplaza la fila y luego enfoca la película solicitada.
 */
private class HomeRowFocusController {
    var requestFocus: ((String?) -> Unit)? = null

    fun request(movieId: String? = null): Boolean {
        val action = requestFocus ?: return false
        action(movieId)
        return true
    }
}

@Composable
fun HomeScreen(
    movies: List<Movie>,
    progress: Map<String, WatchProgress>,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    contentFocusRequester: FocusRequester,
    topFocusRequester: FocusRequester,
    controllerBridge: HomeControllerBridge,
    restoreFocusSection: HomeFocusSection? = null,
    restoreFocusMovieId: String? = null,
    onFocusRestored: (HomeFocusSection, String?) -> Unit = { _, _ -> },
    onContentFocusChanged: (HomeFocusSection, Movie) -> Unit = { _, _ -> },
    onConnect: () -> Unit,
    onMovie: (Movie) -> Unit,
    onPlay: (Movie) -> Unit,
    onContinuePlay: (Movie) -> Unit = onPlay,
    onMovies: () -> Unit
) {
    if (movies.isEmpty()) {
        EmptyLibrary(onConnect = onConnect, tvMode = tvMode)
        return
    }

    val continueMovies = remember(movies, progress) {
        movies
            .filter { progress[it.id] != null }
            .sortedByDescending { progress[it.id]?.updatedAt ?: 0L }
    }

    /*
     * Película cuyo botón CONTINUAR tiene foco.
     * Solo en ese caso Xbox A debe saltar directamente al Player.
     */
    var focusedContinueMovie by remember {
        mutableStateOf<Movie?>(null)
    }

    DisposableEffect(controllerBridge, focusedContinueMovie?.id) {
        controllerBridge.selectHandler = {
            val movie = focusedContinueMovie

            if (movie != null) {
                onContinuePlay(movie)
                true
            } else {
                false
            }
        }

        onDispose {
            controllerBridge.selectHandler = null
        }
    }

    // Entrada explícita a la primera fila bajo el carrusel.
    val belowCarouselFocusRequester = remember { FocusRequester() }

    // Entrada explícita a la biblioteca. Es necesaria cuando "Seguir viendo"
    // contiene varias películas y el foco queda dentro de su LazyRow.
    val libraryFocusRequester = remember { FocusRequester() }

    val homeListState = rememberLazyListState()
    val homeScope = rememberCoroutineScope()

    // v0.11.9: navegación vertical determinista entre las tres zonas de Inicio.
    // Guardamos la última película enfocada de cada fila para poder regresar a
    // ella al subir/bajar, incluso si la LazyRow la sacó de composición.
    var focusedHomeSection by remember { mutableStateOf(HomeFocusSection.Hero) }
    var lastContinueMovieId by remember { mutableStateOf<String?>(null) }
    var lastLibraryMovieId by remember { mutableStateOf<String?>(null) }
    val continueFocusController = remember { HomeRowFocusController() }
    val libraryFocusController = remember { HomeRowFocusController() }

    val focusHeroSection: () -> Unit = {
        homeScope.launch {
            homeListState.scrollToItem(0)
            withFrameNanos { }
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    val focusContinueSection: () -> Unit = {
        if (continueMovies.isNotEmpty()) {
            homeScope.launch {
                // Hero = 0, Seguir viendo = 1. Alineamos el item completo para
                // que el carrusel no quede cortado a media altura.
                homeListState.scrollToItem(1)
                repeat(6) {
                    withFrameNanos { }
                    if (continueFocusController.request(lastContinueMovieId)) {
                        return@launch
                    }
                }
            }
        }
    }

    val focusLibrarySection: () -> Unit = {
        homeScope.launch {
            val libraryIndex = if (continueMovies.isEmpty()) 1 else 2
            homeListState.scrollToItem(libraryIndex)
            repeat(6) {
                withFrameNanos { }
                if (libraryFocusController.request(lastLibraryMovieId)) {
                    return@launch
                }
            }
        }
    }

    DisposableEffect(
        controllerBridge,
        focusedHomeSection,
        continueMovies.isNotEmpty(),
        lastContinueMovieId,
        lastLibraryMovieId
    ) {
        controllerBridge.actionHandler = { action ->
            when (action) {
                ControllerAction.Down -> when (focusedHomeSection) {
                    HomeFocusSection.Hero -> {
                        if (continueMovies.isNotEmpty()) focusContinueSection()
                        else focusLibrarySection()
                        true
                    }

                    HomeFocusSection.ContinueWatching -> {
                        focusLibrarySection()
                        true
                    }

                    // No hay otra sección focalizable debajo de la biblioteca.
                    HomeFocusSection.Library -> false
                }

                ControllerAction.Up -> when (focusedHomeSection) {
                    HomeFocusSection.Hero -> false
                    HomeFocusSection.ContinueWatching -> {
                        focusHeroSection()
                        true
                    }
                    HomeFocusSection.Library -> {
                        if (continueMovies.isNotEmpty()) focusContinueSection()
                        else focusHeroSection()
                        true
                    }
                }

                else -> false
            }
        }

        onDispose {
            controllerBridge.actionHandler = null
        }
    }

    /*
     * v0.11.7: Home recuerda la sección y la película que tenían el foco.
     * Primero restauramos la posición vertical; el Hero o MediaRow restauran
     * después la posición horizontal/página y reclaman el foco.
     */
    LaunchedEffect(
        focusModeEnabled,
        restoreFocusSection,
        restoreFocusMovieId,
        continueMovies,
        movies
    ) {
        if (!focusModeEnabled || restoreFocusSection == null) return@LaunchedEffect

        when (restoreFocusSection) {
            HomeFocusSection.Hero -> {
                val movieId = restoreFocusMovieId
                if (movieId != null && movies.none { it.id == movieId }) {
                    onFocusRestored(HomeFocusSection.Hero, movieId)
                    return@LaunchedEffect
                }
                homeListState.scrollToItem(0)
            }

            HomeFocusSection.ContinueWatching -> {
                val movieId = restoreFocusMovieId
                if (movieId == null || continueMovies.none { it.id == movieId }) {
                    onFocusRestored(HomeFocusSection.ContinueWatching, movieId)
                    return@LaunchedEffect
                }
                // Hero = 0, Seguir viendo = 1.
                homeListState.scrollToItem(1)
            }

            HomeFocusSection.Library -> {
                val movieId = restoreFocusMovieId
                if (movieId == null || movies.none { it.id == movieId }) {
                    onFocusRestored(HomeFocusSection.Library, movieId)
                    return@LaunchedEffect
                }
                // Si existe Seguir viendo: Hero 0, Continue 1, Library 2.
                homeListState.scrollToItem(if (continueMovies.isEmpty()) 1 else 2)
            }
        }

        withFrameNanos { }
    }

    /*
     * Al regresar hacia arriba desde la primera fila:
     * primero restauramos la posición vertical exacta del Hero y DESPUÉS
     * devolvemos el foco a su botón principal.
     *
     * Si se mueve el foco primero, Compose intenta mostrar solo el botón
     * inferior del Hero y deja la portada/título fuera de pantalla.
     */
    val returnToFullHero: () -> Unit = focusHeroSection

    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = when {
                compact -> 92.dp
                tvMode -> 58.dp
                else -> 42.dp
            }
        ),
        verticalArrangement = Arrangement.spacedBy(if (tvMode) 42.dp else 34.dp)
    ) {
        item {
            FeaturedCarousel(
                movies = movies,
                progress = progress,
                metadataRepository = metadataRepository,
                thumbnailRepository = thumbnailRepository,
                compact = compact,
                tvMode = tvMode,
                focusModeEnabled = focusModeEnabled,
                contentFocusRequester = contentFocusRequester,
                topFocusRequester = topFocusRequester,
                downFocusRequester = belowCarouselFocusRequester,
                restoreFocusMovieId = if (restoreFocusSection == HomeFocusSection.Hero) {
                    restoreFocusMovieId
                } else {
                    null
                },
                onRestoreFocusConsumed = { movieId ->
                    onFocusRestored(HomeFocusSection.Hero, movieId)
                },
                onHeroFocused = { movie ->
                    focusedHomeSection = HomeFocusSection.Hero
                    onContentFocusChanged(HomeFocusSection.Hero, movie)
                    homeScope.launch {
                        // Al volver arriba mostramos el HERO COMPLETO,
                        // no únicamente el botón que recibió el focus.
                        homeListState.scrollToItem(0)
                    }
                },
                onMovie = onMovie,
                onPlay = onPlay,
                onMovies = onMovies
            )
        }

        if (continueMovies.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Seguir viendo",
                    subtitle = "${continueMovies.size} ${
                        if (continueMovies.size == 1) "película" else "películas"
                    } con progreso guardado",
                    movies = continueMovies,
                    progress = progress,
                    metadataRepository = metadataRepository,
                    thumbnailRepository = thumbnailRepository,
                    showContinue = true,
                    compact = compact,
                    tvMode = tvMode,
                    focusModeEnabled = focusModeEnabled,
                    entryFocusRequester = belowCarouselFocusRequester,
                    upFocusRequester = contentFocusRequester,
                    downFocusRequester = libraryFocusRequester,
                    focusController = continueFocusController,
                    onReturnToHero = returnToFullHero,
                    onContinueFocusChanged = { movie, focused ->
                        if (focused) {
                            focusedContinueMovie = movie
                        } else if (focusedContinueMovie?.id == movie.id) {
                            focusedContinueMovie = null
                        }
                    },
                    restoreFocusMovieId = if (
                        restoreFocusSection == HomeFocusSection.ContinueWatching
                    ) {
                        restoreFocusMovieId
                    } else {
                        null
                    },
                    onRestoreFocusConsumed = { movieId ->
                        onFocusRestored(HomeFocusSection.ContinueWatching, movieId)
                    },
                    onMovieFocusChanged = { movie ->
                        focusedHomeSection = HomeFocusSection.ContinueWatching
                        lastContinueMovieId = movie.id
                        onContentFocusChanged(HomeFocusSection.ContinueWatching, movie)
                    },
                    onMovie = onMovie,
                    onPlay = onContinuePlay
                )
            }
        }

        item {
            MediaRow(
                title = "Tu biblioteca de películas",
                subtitle = "${movies.size} películas",
                movies = movies,
                progress = progress,
                metadataRepository = metadataRepository,
                thumbnailRepository = thumbnailRepository,
                showContinue = false,
                compact = compact,
                tvMode = tvMode,
                focusModeEnabled = focusModeEnabled,
                entryFocusRequester = if (continueMovies.isEmpty()) {
                    belowCarouselFocusRequester
                } else {
                    libraryFocusRequester
                },
                upFocusRequester = if (continueMovies.isEmpty()) {
                    contentFocusRequester
                } else {
                    belowCarouselFocusRequester
                },
                downFocusRequester = null,
                focusController = libraryFocusController,
                onReturnToHero = if (continueMovies.isEmpty()) {
                    returnToFullHero
                } else {
                    null
                },
                onContinueFocusChanged = { _, _ -> },
                restoreFocusMovieId = if (restoreFocusSection == HomeFocusSection.Library) {
                    restoreFocusMovieId
                } else {
                    null
                },
                onRestoreFocusConsumed = { movieId ->
                    onFocusRestored(HomeFocusSection.Library, movieId)
                },
                onMovieFocusChanged = { movie ->
                    focusedHomeSection = HomeFocusSection.Library
                    lastLibraryMovieId = movie.id
                    onContentFocusChanged(HomeFocusSection.Library, movie)
                },
                onMovie = onMovie,
                onPlay = onPlay
            )
        }

        item {
            StarFlixFooter(tvMode = tvMode)
        }
    }
}

@Composable
private fun FeaturedCarousel(
    movies: List<Movie>,
    progress: Map<String, WatchProgress>,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    contentFocusRequester: FocusRequester,
    topFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    restoreFocusMovieId: String?,
    onRestoreFocusConsumed: (String?) -> Unit,
    onHeroFocused: (Movie) -> Unit,
    onMovie: (Movie) -> Unit,
    onPlay: (Movie) -> Unit,
    onMovies: () -> Unit
) {
    val featured = remember(movies) { movies }
    val pagerState = rememberPagerState(pageCount = { featured.size })
    val heroRestoreFocusRequester = remember { FocusRequester() }
    val restorePage = remember(featured, restoreFocusMovieId) {
        restoreFocusMovieId?.let { movieId ->
            featured.indexOfFirst { it.id == movieId }
        } ?: -1
    }

    LaunchedEffect(focusModeEnabled, restoreFocusMovieId, restorePage) {
        if (!focusModeEnabled || restoreFocusMovieId == null || restorePage < 0) {
            return@LaunchedEffect
        }

        pagerState.scrollToPage(restorePage)
        repeat(4) {
            withFrameNanos { }
            val restored = runCatching {
                heroRestoreFocusRequester.requestFocus()
            }.getOrDefault(false)
            if (restored) {
                onRestoreFocusConsumed(restoreFocusMovieId)
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(featured.size) {
        if (featured.size <= 1) return@LaunchedEffect

        while (true) {
            delay(8_500L)
            val next = (pagerState.currentPage + 1) % featured.size
            pagerState.animateScrollToPage(next)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when {
                    compact -> 510.dp
                    tvMode -> 390.dp
                    else -> 610.dp
                }
            )
    ) { page ->
        val movie = featured[page]
        var info by remember(movie.id) {
            mutableStateOf<MediaTechnicalInfo?>(null)
        }

        LaunchedEffect(movie.id) {
            info = metadataRepository.metadata(movie)
        }

        Box(Modifier.fillMaxSize()) {
            MovieArtwork(
                movie = movie,
                thumbnailRepository = thumbnailRepository,
                modifier = Modifier.fillMaxSize(),
                requestWidth = 1400,
                requestHeight = 800
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.96f),
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.16f)
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF050505)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = when {
                            compact -> 22.dp
                            tvMode -> 48.dp
                            else -> 36.dp
                        },
                        end = if (tvMode) 48.dp else 22.dp,
                        bottom = when {
                            compact -> 34.dp
                            tvMode -> 30.dp
                            else -> 48.dp
                        }
                    )
                    .widthIn(
                        max = when {
                            compact -> 620.dp
                            tvMode -> 640.dp
                            else -> 780.dp
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "DESTACADO DE TU BIBLIOTECA",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = if (tvMode) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )

                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = when {
                        compact -> 46.sp
                        tvMode -> 48.sp
                        else -> 68.sp
                    },
                    lineHeight = when {
                        compact -> 44.sp
                        tvMode -> 47.sp
                        else -> 62.sp
                    },
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                val technical = info
                if (technical != null) {
                    Text(
                        text = listOf(
                            "Película",
                            movie.year?.toString().orEmpty(),
                            formatDuration(technical.durationMs),
                            technical.resolutionLabel,
                            technical.audioDolbyLabel
                        ).filter { it.isNotBlank() }.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = if (tvMode) 13.sp else 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val primaryShape = RoundedCornerShape(99.dp)

                    Button(
                        onClick = {
                            if (progress[movie.id] != null) onPlay(movie)
                            else onMovie(movie)
                        },
                        modifier = Modifier
                            .focusRequester(contentFocusRequester)
                            .then(
                                if (movie.id == restoreFocusMovieId) {
                                    Modifier.focusRequester(heroRestoreFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .focusProperties {
                                up = topFocusRequester
                                down = downFocusRequester
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    onHeroFocused(movie)
                                }
                            }
                            .controllerFocus(
                                shape = primaryShape,
                                focusedScale = if (tvMode) 1.055f else 1.025f,
                                strong = tvMode,
                                autoBringIntoView = false
                            ),
                        shape = primaryShape,
                        contentPadding = PaddingValues(
                            horizontal = if (tvMode) 24.dp else 16.dp,
                            vertical = if (tvMode) 13.dp else 10.dp
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (progress[movie.id] != null) "CONTINUAR" else "VER PELÍCULA",
                            fontWeight = FontWeight.Black
                        )
                    }

                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = when {
                            compact -> 18.dp
                            tvMode -> 46.dp
                            else -> 34.dp
                        },
                        bottom = when {
                            compact -> 24.dp
                            tvMode -> 28.dp
                            else -> 42.dp
                        }
                    ),
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(99.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Text(
                    "${page + 1} / ${featured.size}",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = if (tvMode) 11.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    subtitle: String,
    movies: List<Movie>,
    progress: Map<String, WatchProgress>,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    showContinue: Boolean,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    entryFocusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    focusController: HomeRowFocusController,
    onReturnToHero: (() -> Unit)?,
    onContinueFocusChanged: (Movie, Boolean) -> Unit,
    restoreFocusMovieId: String?,
    onRestoreFocusConsumed: (String) -> Unit,
    onMovieFocusChanged: (Movie) -> Unit,
    onMovie: (Movie) -> Unit,
    onPlay: (Movie) -> Unit
) {
    val rowState = rememberLazyListState()
    val rowScope = rememberCoroutineScope()
    val movieIds = remember(movies) { movies.map { it.id } }
    val itemFocusRequesters = remember(movieIds) {
        movieIds.associateWith { FocusRequester() }
    }
    val restoreIndex = remember(movies, restoreFocusMovieId) {
        restoreFocusMovieId?.let { movieId ->
            movies.indexOfFirst { it.id == movieId }
        } ?: -1
    }

    fun requestMovieFocus(movieId: String?, consumeRestore: Boolean = false) {
        val targetIndex = movieId
            ?.let { id -> movies.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val targetMovie = movies.getOrNull(targetIndex) ?: return
        val requester = itemFocusRequesters[targetMovie.id] ?: return

        rowScope.launch {
            // La tarjeta puede estar fuera de composición porque LazyRow
            // virtualiza sus elementos. Primero la hacemos visible.
            rowState.scrollToItem(targetIndex)

            repeat(5) {
                withFrameNanos { }
                val focused = runCatching { requester.requestFocus() }
                    .getOrDefault(false)
                if (focused) {
                    if (consumeRestore && movieId != null) {
                        onRestoreFocusConsumed(movieId)
                    }
                    return@launch
                }
            }
        }
    }

    DisposableEffect(focusController, movieIds) {
        focusController.requestFocus = { preferredMovieId ->
            requestMovieFocus(preferredMovieId)
        }

        onDispose {
            focusController.requestFocus = null
        }
    }

    /*
     * El progreso guardado al salir del Player puede reordenar "Seguir viendo".
     * Restauramos por movieId (no por índice) usando el mismo mecanismo robusto
     * que la navegación vertical entre filas.
     */
    LaunchedEffect(focusModeEnabled, restoreFocusMovieId, restoreIndex) {
        val movieId = restoreFocusMovieId ?: return@LaunchedEffect
        if (!focusModeEnabled || restoreIndex < 0) return@LaunchedEffect
        requestMovieFocus(movieId, consumeRestore = true)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (tvMode) 16.dp else 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = when {
                    compact -> 18.dp
                    tvMode -> 48.dp
                    else -> 28.dp
                }
            )
        ) {
            Text(
                title,
                fontSize = when {
                    compact -> 25.sp
                    tvMode -> 28.sp
                    else -> 30.sp
                },
                fontWeight = FontWeight.Black
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (tvMode) 13.sp else 12.sp
            )
        }

        LazyRow(
            state = rowState,
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(if (tvMode) 18.dp else 14.dp),
            contentPadding = PaddingValues(
                horizontal = when {
                    compact -> 18.dp
                    tvMode -> 48.dp
                    else -> 28.dp
                }
            )
        ) {
            itemsIndexed(
                items = movies,
                key = { _, movie -> movie.id }
            ) { index, movie ->
                var cardModifier =
                    Modifier.width(
                        when {
                            compact -> 205.dp
                            tvMode -> 196.dp
                            else -> 230.dp
                        }
                    )

                itemFocusRequesters[movie.id]?.let { requester ->
                    cardModifier = cardModifier.focusRequester(requester)
                }

                if (index == 0 && entryFocusRequester != null) {
                    cardModifier = cardModifier.focusRequester(entryFocusRequester)
                }

                if (index == 0) {
                    cardModifier = cardModifier
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionUp &&
                                onReturnToHero != null
                            ) {
                                onReturnToHero()
                                true
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            // Fallback para teclado/dispositivos donde el
                            // preview event no sea entregado como esperamos.
                            upFocusRequester?.let { requester ->
                                up = requester
                            }
                        }
                }

                MovieCard(
                    movie = movie,
                    metadataRepository = metadataRepository,
                    thumbnailRepository = thumbnailRepository,
                    progress = progress[movie.id],
                    showContinueButton = showContinue,
                    tvMode = tvMode,
                    modifier = cardModifier
                        .focusProperties {
                            /*
                             * Si el foco está en la tarjeta de "Seguir viendo"
                             * y Android no entra primero al botón CONTINUAR,
                             * DOWN puede salir directamente hacia la biblioteca.
                             */
                            if (showContinue) {
                                downFocusRequester?.let { requester ->
                                    down = requester
                                }
                            }
                        },
                    continueDownFocusRequester = if (showContinue) {
                        downFocusRequester
                    } else {
                        null
                    },
                    onCardFocusChanged = { focused ->
                        if (focused) {
                            onMovieFocusChanged(movie)
                        }
                        if (showContinue) {
                            onContinueFocusChanged(movie, focused)
                        }
                    },
                    onContinueFocusChanged = { focused ->
                        if (focused) {
                            onMovieFocusChanged(movie)
                        }
                        if (showContinue) {
                            onContinueFocusChanged(movie, focused)
                        }
                    },
                    onClick = { onMovie(movie) },
                    onContinue = { onPlay(movie) }
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onConnect: () -> Unit, tvMode: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF26120E),
                        Color(0xFF0D0925),
                        Color(0xFF050505)
                    ),
                    radius = 1_200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(if (tvMode) 48.dp else 26.dp)
                .widthIn(max = if (tvMode) 760.dp else 700.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "Tu cine local,\nen una sola\nbiblioteca.",
                fontSize = if (tvMode) 56.sp else 50.sp,
                lineHeight = if (tvMode) 54.sp else 50.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                "Selecciona la carpeta donde guardas tus películas. StarFlix recordará el acceso para futuros inicios.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val connectShape = RoundedCornerShape(99.dp)

            Button(
                onClick = onConnect,
                modifier = Modifier.controllerFocus(
                    shape = connectShape,
                    focusedScale = if (tvMode) 1.06f else 1.025f,
                    strong = tvMode
                ),
                shape = connectShape,
                contentPadding = PaddingValues(
                    horizontal = if (tvMode) 28.dp else 16.dp,
                    vertical = if (tvMode) 15.dp else 10.dp
                )
            ) {
                Text("＋ Conectar biblioteca", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StarFlixFooter(tvMode: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (tvMode) 48.dp else 28.dp,
                vertical = if (tvMode) 42.dp else 38.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(14.dp))

        Text(
            "StarFlix",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Biblioteca de vídeo local. Tus archivos permanecen en tu dispositivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            "© 2026 StarFlix. Las películas, títulos e imágenes pertenecen a sus respectivos propietarios.",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            fontSize = 10.sp
        )
    }
}
