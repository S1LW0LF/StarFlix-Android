package com.starflix.local.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.starflix.local.ui.formatDuration
import kotlinx.coroutines.delay

@Composable
fun MovieCard(
    movie: Movie,
    metadataRepository: MetadataRepository,
    thumbnailRepository: ThumbnailRepository,
    progress: WatchProgress?,
    showContinueButton: Boolean,
    tvMode: Boolean = false,
    modifier: Modifier = Modifier,
    continueDownFocusRequester: FocusRequester? = null,
    onCardFocusChanged: (Boolean) -> Unit = {},
    onContinueFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    onContinue: () -> Unit
) {
    var metadata by remember(movie.id) {
        mutableStateOf<MediaTechnicalInfo?>(null)
    }
    var cardFocused by remember(movie.id) {
        mutableStateOf(false)
    }
    val cardInteraction = remember { MutableInteractionSource() }
    val interactionFocused by cardInteraction.collectIsFocusedAsState()
    val isCardFocused = cardFocused || interactionFocused

    /*
     * TV: no analizamos técnicamente todas las películas mientras el usuario
     * se desplaza. Algunos MKV requieren una inspección relativamente costosa
     * (codec/Dolby) y eso compite con la lectura de miniaturas del USB.
     *
     * Solo cargamos esos datos si la tarjeta permanece enfocada unos instantes.
     * Al recorrer rápidamente la fila, los trabajos se cancelan antes de tocar
     * el almacenamiento. Móvil/tablet conserva el comportamiento anterior.
     */
    LaunchedEffect(movie.id, movie.lastModified, tvMode, isCardFocused) {
        if (tvMode) {
            if (!isCardFocused) return@LaunchedEffect
            delay(350L)
        }
        metadata = metadataRepository.metadata(movie)
    }

    val cardShape = RoundedCornerShape(if (tvMode) 14.dp else 16.dp)

    Card(
        modifier = modifier
            .onFocusChanged { state ->
                cardFocused = state.isFocused
                onCardFocusChanged(state.isFocused)
            }
            .controllerFocus(
                shape = cardShape,
                focusedScale = 1f,
                strong = true,
                // LazyRow/LazyGrid ya gestionan el viewport. En TV, usar además
                // BringIntoView producía dos desplazamientos para un solo D-Pad.
                autoBringIntoView = !tvMode,
                // En TV el borde lo pinta Card directamente: es más visible y
                // no depende del orden de los modificadores de focus/clickable.
                drawRing = !tvMode,
                // La selección TV conserva exactamente el tamaño/posición de
                // la tarjeta: solo cambia el contorno. Esto evita vibración
                // visual cuando se recorre una cuadrícula rápidamente.
                enableLift = !tvMode
            )
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D0D0D)
        ),
        border = if (tvMode && isCardFocused) {
            BorderStroke(4.dp, Color.White)
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
        ) {
            MovieArtwork(
                movie = movie,
                thumbnailRepository = thumbnailRepository,
                modifier = Modifier.fillMaxSize(),
                requestWidth = if (tvMode) 320 else 480,
                requestHeight = if (tvMode) 480 else 720
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    text = "PELÍCULA",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (tvMode) 13.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (tvMode) 8.dp else 7.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (tvMode) 16.sp else 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val info = metadata
                if (info != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = listOf(
                                formatDuration(info.durationMs),
                                info.resolutionLabel
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = if (tvMode) 10.sp else 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        val badge = info.audioDolbyLabel
                        if (badge.isNotBlank()) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Text(
                                    text = badge,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 4.dp
                                    ),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (tvMode) {
                            listOf(
                                movie.year?.toString().orEmpty(),
                                movie.extension.uppercase()
                            ).filter { it.isNotBlank() }.joinToString(" • ")
                                .ifBlank { "PELÍCULA" }
                        } else {
                            "Analizando…"
                        },
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = if (tvMode) 10.sp else 9.sp
                    )
                }

                if (showContinueButton) {
                    val continueShape = RoundedCornerShape(9.dp)

                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (tvMode) 40.dp else 36.dp)
                            .focusProperties {
                                continueDownFocusRequester?.let { requester ->
                                    down = requester
                                }
                            }
                            .onFocusChanged { state ->
                                onContinueFocusChanged(state.isFocused)
                            }
                            .controllerFocus(
                                shape = continueShape,
                                focusedScale = if (tvMode) 1.035f else 1.012f,
                                strong = tvMode
                            ),
                        shape = continueShape,
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "CONTINUAR",
                            fontSize = if (tvMode) 11.sp else 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                /*
                 * La barra de progreso es exclusiva de "Seguir viendo".
                 * En el resto de la app una película puede tener progreso
                 * guardado, pero no mostramos la barra visual.
                 */
                if (showContinueButton && progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }
            }
        }
    }
}
