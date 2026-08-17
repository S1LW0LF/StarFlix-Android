package com.starflix.local.input

/*
 * Puente entre el flujo global del mando y LazyVerticalGrid.
 *
 * MoviesScreen registra un handler mientras está compuesto. StarFlixApp lo
 * consulta antes de usar el moveFocus() genérico.
 */
class MoviesControllerBridge {
    var handler: (suspend (ControllerAction) -> Boolean)? = null

    suspend fun handle(action: ControllerAction): Boolean =
        handler?.invoke(action) ?: false
}
