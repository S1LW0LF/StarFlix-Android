package com.starflix.local.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.starflix.local.input.ControllerInput
import com.starflix.local.model.Movie
import com.starflix.local.model.WatchProgress
import com.starflix.local.ui.findActivity
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movie: Movie,
    progress: WatchProgress?,
    tvMode: Boolean,
    onBack: () -> Unit,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    /*
     * La ruta de reproducción móvil/tablet sigue siendo la estable de v0.10.11.
     * La navegación con mando se integra en PlayerView sin sustituir los gestos
     * ni los controles táctiles del reproductor.
     */
    val player = remember(movie.id) {
        ExoPlayer.Builder(context).build().apply {
            val resumeMs = progress?.positionMs ?: 0L
            setMediaItem(MediaItem.fromUri(movie.uri), resumeMs)
            prepare()
            playWhenReady = true
        }
    }

    fun save() {
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0) {
            onSaveProgress(
                player.currentPosition.coerceAtLeast(0L),
                duration
            )
        }
    }

    DisposableEffect(activity, player) {
        // Mientras el reproductor está visible, los eventos de un mando físico
        // se entregan directamente a PlayerView. Touch continúa funcionando
        // normalmente porque este camino solo se usa para GAMEPAD/DPAD.
        ControllerInput.passThroughToFocusedView = true
        ControllerInput.onPlayerControllerInteraction = {
            playerViewRef?.let { playerView ->
                playerView.showController()

                // Cuando Media3 oculta el controller, el hijo que tenía foco
                // puede dejar de ser visible. Al volver a usar el mando,
                // restauramos un foco válido para que la navegación continúe.
                val focused = playerView.findFocus()
                if (focused == null || !focused.isShown || focused.visibility != View.VISIBLE) {
                    playerView
                        .findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                        ?.requestFocus()
                        ?: playerView.requestFocus()
                }
            }
        }

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }

        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            ControllerInput.passThroughToFocusedView = false
            ControllerInput.onPlayerControllerInteraction = null
            playerViewRef = null
            save()
            player.release()

            activity?.window?.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5_000L)
            save()
        }
    }

    BackHandler {
        save()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    playerViewRef = this

                    isFocusable = true
                    isFocusableInTouchMode = true

                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerAutoShow = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setShowSubtitleButton(true)
                    setTimeBarScrubbingEnabled(true)

                    val controllerAttached = hasControllerAttached()
                    controllerHideOnTouch = true
                    setControllerShowTimeoutMs(
                        if (tvMode) 8_000 else 5_000
                    )

                    // El estilo de alto contraste se activa cuando realmente
                    // hay mando conectado. La versión 0.11.3 pintaba también
                    // el contenedor padre de algunos botones de Media3; en
                    // móvil eso producía el gran rectángulo blanco mostrado
                    // en pantalla. Ahora solo se modifica el control enfocado.
                    if (tvMode || controllerAttached) {
                        applyStarFlixControllerFocusStyle()
                    }

                    this.player = player

                    post {
                        if (tvMode || controllerAttached) {
                            showController()
                            findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                                ?.requestFocus()
                                ?: requestFocus()
                        } else {
                            requestFocus()
                        }
                    }
                }
            },
            update = {
                playerViewRef = it
                it.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!tvMode) {
            IconButton(
                onClick = {
                    save()
                    onBack()
                },
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
        }
    }
}

private fun hasControllerAttached(): Boolean =
    InputDevice.getDeviceIds().any { deviceId ->
        val device = InputDevice.getDevice(deviceId) ?: return@any false
        device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)
    }

private fun Drawable?.freshCopy(): Drawable? =
    this?.constantState?.newDrawable()?.mutate() ?: this

@OptIn(UnstableApi::class)
private fun PlayerView.applyStarFlixControllerFocusStyle() {
    val density = resources.displayMetrics.density

    fun roundedBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(color)
        }

    fun roundedOutline(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(AndroidColor.TRANSPARENT)
            setStroke(
                (2f * density).toInt().coerceAtLeast(1),
                AndroidColor.WHITE
            )
        }

    fun visit(view: View) {
        when (view) {
            is ImageButton -> {
                val originalBackground = view.background.freshCopy()

                view.imageTintList = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf()
                    ),
                    intArrayOf(
                        AndroidColor.BLACK,
                        AndroidColor.BLACK,
                        AndroidColor.WHITE
                    )
                )

                view.setOnFocusChangeListener { button, hasFocus ->
                    // Importante: SOLO el botón recibe el fondo blanco.
                    // Nunca pintamos su parent, porque algunos contenedores
                    // internos de Media3 ocupan casi todo el ancho del player.
                    button.background = if (hasFocus) {
                        roundedBackground(AndroidColor.WHITE)
                    } else {
                        originalBackground.freshCopy()
                    }
                }
            }

            is Button -> {
                val originalBackground = view.background.freshCopy()
                val originalTextColors = view.textColors

                view.setOnFocusChangeListener { buttonView, hasFocus ->
                    val button = buttonView as Button
                    button.setTextColor(
                        if (hasFocus) AndroidColor.BLACK
                        else originalTextColors.defaultColor
                    )
                    button.background = if (hasFocus) {
                        roundedBackground(AndroidColor.WHITE)
                    } else {
                        originalBackground.freshCopy()
                    }

                    // Si el botón tiene un icono compuesto, mantenemos el
                    // mismo contraste que el texto.
                    val tint = if (hasFocus) AndroidColor.BLACK else AndroidColor.WHITE
                    button.compoundDrawablesRelative.forEach { drawable ->
                        drawable?.mutate()?.setTint(tint)
                    }
                }
            }

            is DefaultTimeBar -> {
                val originalBackground = view.background.freshCopy()
                view.setOnFocusChangeListener { timeBar, hasFocus ->
                    timeBar.background = if (hasFocus) {
                        roundedOutline()
                    } else {
                        originalBackground.freshCopy()
                    }
                }
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                visit(view.getChildAt(index))
            }
        }
    }

    visit(this)
}
