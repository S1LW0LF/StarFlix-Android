package com.starflix.local.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starflix.local.data.MetadataRepository
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.model.MediaTechnicalInfo
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import com.starflix.local.ui.components.MovieArtwork
import com.starflix.local.ui.components.controllerFocus
import com.starflix.local.ui.formatDuration

@Composable
fun DetailScreen(
    movie: Movie,
    progress: WatchProgress?,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    compact: Boolean,
    tvMode: Boolean,
    focusModeEnabled: Boolean,
    contentFocusRequester: FocusRequester,
    topFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    var info by remember(movie.id) {
        mutableStateOf<MediaTechnicalInfo?>(null)
    }

    LaunchedEffect(movie.id) {
        info = metadataRepository.metadata(movie)
    }

    /*
     * Al abrir una película desde una tarjeta, la barra superior permanece
     * compuesta y Android TV puede conservar allí el focus. Forzamos la entrada
     * a REPRODUCIR/CONTINUAR para que OK/A reproduzca de inmediato.
     */
    LaunchedEffect(movie.id, focusModeEnabled) {
        if (focusModeEnabled) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MovieArtwork(
            movie = movie,
            thumbnailRepository = thumbnailRepository,
            modifier = Modifier.fillMaxSize(),
            requestWidth = if (tvMode) 1280 else 1600,
            requestHeight = if (tvMode) 720 else 900
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.97f),
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
                            Color.Black.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color(0xFF050505)
                        )
                    )
                )
        )

        val backShape = RoundedCornerShape(99.dp)

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(if (tvMode) 32.dp else 18.dp)
                .align(Alignment.TopStart)
                .controllerFocus(
                    shape = backShape,
                    focusedScale = if (tvMode) 1.05f else 1.015f,
                    strong = tvMode
                )
        ) {
            Icon(Icons.Default.ArrowBack, "Volver")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    horizontal = when {
                        compact -> 24.dp
                        tvMode -> 52.dp
                        else -> 42.dp
                    },
                    vertical = when {
                        compact -> 34.dp
                        tvMode -> 38.dp
                        else -> 48.dp
                    }
                )
                .widthIn(
                    max = when {
                        compact -> 680.dp
                        tvMode -> 720.dp
                        else -> 900.dp
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "PELÍCULA • BIBLIOTECA LOCAL",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = if (tvMode) 12.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )

            Text(
                movie.title,
                color = Color.White,
                fontSize = when {
                    compact -> 48.sp
                    tvMode -> 54.sp
                    else -> 76.sp
                },
                lineHeight = when {
                    compact -> 47.sp
                    tvMode -> 52.sp
                    else -> 70.sp
                },
                fontWeight = FontWeight.Black,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            info?.let { technical ->
                Text(
                    text = listOf(
                        "Película",
                        movie.year?.toString().orEmpty(),
                        formatDuration(technical.durationMs),
                        technical.resolutionLabel,
                        technical.audioDolbyLabel
                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = if (tvMode) 13.sp else 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val playShape = RoundedCornerShape(99.dp)

            Button(
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(contentFocusRequester)
                    .focusProperties {
                        up = topFocusRequester
                    }
                    .controllerFocus(
                        shape = playShape,
                        focusedScale = if (tvMode) 1.055f else 1.015f,
                        strong = tvMode
                    ),
                shape = playShape,
                contentPadding = PaddingValues(
                    horizontal = if (tvMode) 28.dp else 24.dp,
                    vertical = if (tvMode) 15.dp else 14.dp
                )
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (progress != null) {
                        "CONTINUAR · ${formatDuration(progress.positionMs)}"
                    } else {
                        "REPRODUCIR"
                    },
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
