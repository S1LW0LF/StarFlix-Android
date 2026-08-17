package com.starflix.local.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import com.starflix.local.data.MetadataRepository
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import com.starflix.local.ui.components.controllerFocus

@Composable
fun SearchScreen(
    movies: List<Movie>,
    query: String,
    progress: Map<String, WatchProgress>,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    contentFocusRequester: FocusRequester,
    topFocusRequester: FocusRequester,
    restoreFocusMovieId: String? = null,
    onFocusRestored: (String) -> Unit = {},
    onMovieFocusChanged: (Movie) -> Unit = {},
    onQuery: (String) -> Unit,
    onMovie: (Movie) -> Unit
) {
    val filtered = if (query.isBlank()) {
        movies
    } else {
        movies.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.fileName.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val searchShape = RoundedCornerShape(18.dp)

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tvMode) Modifier.widthIn(max = 760.dp) else Modifier)
                .padding(
                    horizontal = when {
                        compact -> 18.dp
                        tvMode -> 48.dp
                        else -> 30.dp
                    },
                    vertical = if (tvMode) 18.dp else 14.dp
                )
                .focusRequester(contentFocusRequester)
                .focusProperties {
                    up = topFocusRequester
                }
                .controllerFocus(
                    shape = searchShape,
                    focusedScale = if (tvMode) 1.035f else 1.015f,
                    strong = tvMode
                ),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Buscar películas…") },
            singleLine = true,
            shape = searchShape
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MoviesScreen(
                title = if (query.isBlank()) "Buscar" else "Resultados",
                movies = filtered,
                progress = progress,
                metadataRepository = metadataRepository,
                thumbnailRepository = thumbnailRepository,
                compact = compact,
                tvMode = tvMode,
                focusModeEnabled = focusModeEnabled,
                contentFocusRequester = null,
                topFocusRequester = topFocusRequester,
                restoreFocusMovieId = restoreFocusMovieId,
                onFocusRestored = onFocusRestored,
                onMovieFocusChanged = onMovieFocusChanged,
                onMovie = onMovie
            )
        }
    }
}
