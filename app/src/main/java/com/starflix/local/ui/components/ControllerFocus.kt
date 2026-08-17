package com.starflix.local.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/*
 * StarFlix 0.10.1
 *
 * Esta versión parte de 0.10 y SOLO cambia el aspecto visual del elemento
 * seleccionado.
 *
 * - mismo fondo
 * - mismo tamaño
 * - mismo radio de esquinas
 * - contorno blanco COMPLETO siguiendo la forma real
 * - sin bloque gris
 * - sin sombra
 *
 * El trazo se dibuja hacia dentro para que no sea recortado.
 */
@Composable
fun Modifier.controllerFocus(
    shape: Shape,
    focusedScale: Float = 1f,
    strong: Boolean = false,
    autoBringIntoView: Boolean = true,
    drawRing: Boolean = true,
    enableLift: Boolean = true
): Modifier {
    var focused by remember { mutableStateOf(false) }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 95),
        label = "starflix-focus-scale"
    )

    val lift by animateFloatAsState(
        targetValue = if (focused && enableLift) {
            if (strong) -3f else -2f
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 95),
        label = "starflix-focus-lift"
    )

    val ringWidth by animateDpAsState(
        targetValue = if (focused) {
            if (strong) 2.5.dp else 2.dp
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 75),
        label = "starflix-focus-ring"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 75),
        label = "starflix-focus-alpha"
    )

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { state ->
            val nowFocused = state.isFocused

            if (nowFocused && !focused && autoBringIntoView) {
                scope.launch {
                    withFrameNanos { }
                    bringIntoViewRequester.bringIntoView()
                }
            }

            focused = nowFocused
        }
        .zIndex(if (focused) 40f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift
            this.shape = shape
            clip = false
        }
        .drawWithContent {
            drawContent()

            if (drawRing && ringAlpha > 0.01f && ringWidth.value > 0f) {
                val outline = shape.createOutline(
                    size = size,
                    layoutDirection = layoutDirection,
                    density = this
                )

                drawInsetStarFlixOutline(
                    outline = outline,
                    color = Color.White.copy(alpha = ringAlpha),
                    strokeWidth = ringWidth.toPx()
                )
            }
        }
}

private fun DrawScope.drawInsetStarFlixOutline(
    outline: Outline,
    color: Color,
    strokeWidth: Float
) {
    val inset = strokeWidth / 2f
    val stroke = Stroke(width = strokeWidth)

    when (outline) {
        is Outline.Rectangle -> {
            drawRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(
                    width = (outline.rect.width - strokeWidth).coerceAtLeast(0f),
                    height = (outline.rect.height - strokeWidth).coerceAtLeast(0f)
                ),
                style = stroke
            )
        }

        is Outline.Rounded -> {
            val rr = outline.roundRect
            val radiusX = (rr.topLeftCornerRadius.x - inset).coerceAtLeast(0f)
            val radiusY = (rr.topLeftCornerRadius.y - inset).coerceAtLeast(0f)

            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(
                    width = (rr.width - strokeWidth).coerceAtLeast(0f),
                    height = (rr.height - strokeWidth).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(radiusX, radiusY),
                style = stroke
            )
        }

        is Outline.Generic -> {
            drawPath(
                path = outline.path,
                color = color,
                style = stroke
            )
        }
    }
}
