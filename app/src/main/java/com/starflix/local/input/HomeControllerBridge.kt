package com.starflix.local.input

/*
 * Puente de navegación específico de Inicio para TV.
 *
 * Compose resuelve bien Left/Right dentro de cada LazyRow, pero en televisores
 * reales el salto vertical entre secciones puede fallar si la fila destino aún
 * no está compuesta. Este bridge permite que HomeScreen haga primero el scroll
 * vertical/horizontal necesario y después solicite el foco de forma explícita.
 */
class HomeControllerBridge {
    var selectHandler: (() -> Boolean)? = null
    var actionHandler: ((ControllerAction) -> Boolean)? = null

    fun handle(action: ControllerAction): Boolean {
        if (action == ControllerAction.Select && (selectHandler?.invoke() == true)) {
            return true
        }

        return actionHandler?.invoke(action) ?: false
    }

    fun handleSelect(): Boolean = handle(ControllerAction.Select)
}
