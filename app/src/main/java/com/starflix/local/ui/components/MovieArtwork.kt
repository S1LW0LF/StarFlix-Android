package com.starflix.local.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.starflix.local.data.ThumbnailRepository
import com.starflix.local.model.Movie

@Composable
fun MovieArtwork(
    movie: Movie,
    thumbnailRepository: ThumbnailRepository,
    modifier: Modifier = Modifier,
    requestWidth: Int = 640,
    requestHeight: Int = 360
) {
    var bitmap by remember(movie.id, movie.lastModified, requestWidth, requestHeight) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(movie.id, movie.lastModified, requestWidth, requestHeight) {
        bitmap = thumbnailRepository.thumbnail(
            movie,
            requestWidth,
            requestHeight
        )
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(
                Brush.linearGradient(
                    listOf(
                        colorFrom(movie.id, 0),
                        Color(0xFF121212),
                        colorFrom(movie.id, 1).copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null && !image.isRecycled) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = movie.title.firstOrNull()?.uppercase() ?: "S",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.17f),
                fontSize = 74.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun colorFrom(seed: String, salt: Int): Color {
    val hash = (seed.hashCode() * (31 + salt * 13))
    val hue = ((hash ushr 8) and 0xFF) / 255f
    return Color.hsv(
        hue = hue * 360f,
        saturation = 0.68f,
        value = 0.45f
    )
}
