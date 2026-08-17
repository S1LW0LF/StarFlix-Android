package com.starflix.local.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ControllerAction {
    Left,
    Right,
    Up,
    Down,
    Select,
    Back,
    PlayPause
}

object ControllerInput {
    private val _actions = MutableSharedFlow<ControllerAction>(
        extraBufferCapacity = 32
    )

    val actions = _actions.asSharedFlow()

    /**
     * Mientras el reproductor está visible dejamos que PlayerView reciba
     * directamente D-Pad/OK/teclas multimedia. Fuera del reproductor,
     * MainActivity sigue traduciendo el mando a ControllerAction para Compose.
     */
    @Volatile
    var passThroughToFocusedView: Boolean = false

    /**
     * PlayerScreen instala temporalmente este callback para volver a mostrar
     * los controles de Media3 cuando llega cualquier interacción del mando.
     * Esto evita que, al agotarse el timeout del controller, el foco quede
     * aparentemente perdido y el usuario ya no pueda seguir navegando.
     */
    @Volatile
    var onPlayerControllerInteraction: (() -> Unit)? = null

    fun notifyPlayerControllerInteraction() {
        onPlayerControllerInteraction?.invoke()
    }

    fun emit(action: ControllerAction) {
        _actions.tryEmit(action)
    }
}
