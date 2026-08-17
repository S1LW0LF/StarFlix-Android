package com.starflix.local.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starflix.local.data.MetadataRepository
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.input.ControllerAction
import com.starflix.local.input.MoviesControllerBridge
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import com.starflix.local.ui.components.MovieCard

@Composable
fun MoviesScreen(
    title: String,
    movies: List<Movie>,
    progress: Map<String, WatchProgress>,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    contentFocusRequester: FocusRequester?,
    topFocusRequester: FocusRequester,
    controllerBridge: MoviesControllerBridge? = null,
    restoreFocusMovieId: String? = null,
    onFocusRestored: (String) -> Unit = {},
    onMovieFocusChanged: (Movie) -> Unit = {},
    onMovie: (Movie) -> Unit
) {
    val cardMinWidth = when {
        compact -> 152.dp
        tvMode -> 180.dp
        else -> 188.dp
    }

    val gridState = rememberLazyGridState()

    /*
     * En TV el foco de una LazyVerticalGrid no siempre puede saltar de forma
     * fiable a una tarjeta que todavía no está compuesta. v0.11.2 intentaba
     * resolverlo creando un FocusRequester por película y esperando dos frames
     * después de animateScrollToItem(). En algunos televisores (Sony BRAVIA
     * Android 12) ese flujo puede dejar una petición de foco apuntando a un
     * elemento que ya fue reciclado, y DOWN aparenta quedarse bloqueado.
     *
     * v0.11.3 usa un índice de foco pendiente. Primero posiciona la cuadrícula
     * sin animación si la tarjeta destino no está completamente visible y,
     * cuando el item destino está realmente compuesto, ese mismo item solicita
     * el foco. Así no hay carrera entre scroll, composición y FocusRequester.
     */
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var pendingFocusIndex by remember { mutableIntStateOf(-1) }

    fun currentColumnCount(): Int {
        return gridState.layoutInfo.visibleItemsInfo
            .maxOfOrNull { it.column }
            ?.plus(1)
            ?.coerceAtLeast(1)
            ?: 1
    }

    suspend fun focusMovie(targetIndex: Int) {
        if (targetIndex !in movies.indices) return

        // Registrar primero el destino. Si llega otro D-Pad mientras se termina
        // de componer, el handler continuará desde este índice y no desde la
        // tarjeta anterior.
        pendingFocusIndex = targetIndex

        val itemInfo = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == targetIndex }

        val fullyVisible = itemInfo != null &&
            itemInfo.offset.y >= gridState.layoutInfo.viewportStartOffset &&
            itemInfo.offset.y + itemInfo.size.height <=
                gridState.layoutInfo.viewportEndOffset

        if (!fullyVisible) {
            // Sin animación: evita bloquear el collector del mando mientras el
            // LazyGrid está desplazándose y elimina la cola de pulsaciones que
            // hacía que la navegación pareciera congelada.
            gridState.scrollToItem(targetIndex)
        }
    }

    val restoreFocusIndex = remember(movies, restoreFocusMovieId) {
        restoreFocusMovieId?.let { movieId ->
            movies.indexOfFirst { it.id == movieId }
        } ?: -1
    }

    /*
     * v0.11.7: restauración universal de foco. Cuando volvemos desde Detalle
     * a una cuadrícula, reposicionamos primero el LazyGrid y dejamos que el
     * item ya compuesto reclame el foco mediante pendingFocusIndex.
     */
    LaunchedEffect(focusModeEnabled, restoreFocusMovieId, restoreFocusIndex) {
        if (!focusModeEnabled || restoreFocusMovieId == null || restoreFocusIndex < 0) {
            return@LaunchedEffect
        }

        focusMovie(restoreFocusIndex)
    }

    /*
     * Registrar el navegador mientras esta pantalla esté activa.
     */
    DisposableEffect(controllerBridge, movies.size) {
        if (controllerBridge != null) {
            controllerBridge.handler = handler@{ action ->
                val current = if (pendingFocusIndex in movies.indices) {
                    pendingFocusIndex
                } else {
                    focusedIndex
                }

                if (current !in movies.indices) {
                    false
                } else {
                    val columns = currentColumnCount()
                    val currentColumn = current % columns

                    val target: Int? = when (action) {
                        ControllerAction.Left -> {
                            if (currentColumn > 0) current - 1 else null
                        }

                        ControllerAction.Right -> {
                            val candidate = current + 1
                            if (
                                currentColumn < columns - 1 &&
                                candidate < movies.size
                            ) {
                                candidate
                            } else {
                                null
                            }
                        }

                        ControllerAction.Up -> {
                            val candidate = current - columns

                            if (candidate >= 0) {
                                candidate
                            } else {
                                /*
                                 * Primera fila: devolver false para que
                                 * StarFlixApp use el focus normal y pueda
                                 * regresar a la barra superior.
                                 */
                                return@handler false
                            }
                        }

                        ControllerAction.Down -> {
                            val candidate = current + columns

                            if (candidate < movies.size) {
                                candidate
                            } else {
                                // Última fila: consumir DOWN y permanecer ahí.
                                return@handler true
                            }
                        }

                        else -> return@handler false
                    }

                    if (target != null) {
                        focusMovie(target)
                    }

                    /*
                     * Si Left/Right llegan al extremo de una fila los
                     * consumimos para que no salten a otra región de la UI.
                     */
                    true
                }
            }
        }

        onDispose {
            if (controllerBridge != null) {
                controllerBridge.handler = null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = when {
                    compact -> 18.dp
                    tvMode -> 48.dp
                    else -> 30.dp
                },
                vertical = if (tvMode) 18.dp else 16.dp
            )
        ) {
            Text(
                title,
                fontSize = when {
                    compact -> 27.sp
                    tvMode -> 31.sp
                    else -> 33.sp
                },
                fontWeight = FontWeight.Black
            )
            Text(
                "${movies.size} películas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .focusGroup(),
            columns = GridCells.Adaptive(minSize = cardMinWidth),
            contentPadding = PaddingValues(
                start = when {
                    compact -> 14.dp
                    tvMode -> 48.dp
                    else -> 26.dp
                },
                end = when {
                    compact -> 14.dp
                    tvMode -> 48.dp
                    else -> 26.dp
                },
                bottom = when {
                    compact -> 96.dp
                    tvMode -> 52.dp
                    else -> 34.dp
                }
            ),
            horizontalArrangement = Arrangement.spacedBy(if (tvMode) 20.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (tvMode) 22.dp else 16.dp)
        ) {
            itemsIndexed(
                items = movies,
                key = { _, movie -> movie.id }
            ) { index, movie ->
                val localFocusRequester = remember(movie.id) { FocusRequester() }
                val cardFocusRequester =
                    if (index == 0 && contentFocusRequester != null) {
                        contentFocusRequester
                    } else {
                        localFocusRequester
                    }

                LaunchedEffect(pendingFocusIndex, index) {
                    if (pendingFocusIndex == index) {
                        // El item ya está compuesto; ahora sí es seguro pedir
                        // foco. Un frame basta porque el FocusRequester ya está
                        // enlazado al modifier de esta tarjeta.
                        withFrameNanos { }
                        runCatching { cardFocusRequester.requestFocus() }
                        if (pendingFocusIndex == index) {
                            pendingFocusIndex = -1
                        }
                    }
                }

                var itemModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(cardFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            focusedIndex = index
                            onMovieFocusChanged(movie)

                            if (pendingFocusIndex == index) {
                                pendingFocusIndex = -1
                            }

                            if (restoreFocusMovieId == movie.id) {
                                onFocusRestored(movie.id)
                            }
                        }
                    }

                if (index == 0) {
                    itemModifier = itemModifier
                        .focusProperties {
                            up = topFocusRequester
                        }
                }

                MovieCard(
                    movie = movie,
                    metadataRepository = metadataRepository,
                    thumbnailRepository = thumbnailRepository,
                    progress = progress[movie.id],
                    showContinueButton = false,
                    tvMode = tvMode,
                    modifier = itemModifier,
                    onClick = { onMovie(movie) },
                    onContinue = {}
                )
            }
        }
    }
}
