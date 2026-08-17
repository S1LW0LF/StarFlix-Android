package com.starflix.local

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.starflix.local.input.ControllerAction
import com.starflix.local.input.ControllerInput
import com.starflix.local.ui.StarFlixTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var lastDirection: ControllerAction? = null
    private var lastDirectionAt = 0L

    companion object {
        private const val FALLBACK_DEAD_ZONE = 0.45f
        private const val REPEAT_MS = 165L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StarFlixTheme {
                StarFlixApp()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isController(event.device)) {
            return super.dispatchKeyEvent(event)
        }

        // En el reproductor no secuestramos el D-Pad: PlayerView necesita
        // recibir los KeyEvent reales para mover el foco por timeline,
        // play/pausa, ajustes, audio y subtítulos.
        if (ControllerInput.passThroughToFocusedView) {
            return dispatchPlayerKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
                else -> super.dispatchKeyEvent(event)
            }
        }

        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> ControllerAction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerAction.Right
            KeyEvent.KEYCODE_DPAD_UP -> ControllerAction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> ControllerAction.Down
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> ControllerAction.Select
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_B -> ControllerAction.Back
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_BUTTON_START -> ControllerAction.PlayPause
            else -> null
        }

        if (action != null) {
            ControllerInput.emit(action)
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun dispatchPlayerKeyEvent(event: KeyEvent): Boolean {
        // Si los controles de Media3 se ocultaron por timeout, cualquier
        // nueva interacción del mando debe volver a mostrarlos antes de
        // entregar la tecla al PlayerView. B conserva su salida inmediata.
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            event.keyCode != KeyEvent.KEYCODE_BUTTON_B &&
            event.keyCode != KeyEvent.KEYCODE_BACK
        ) {
            ControllerInput.notifyPlayerControllerInteraction()
        }

        return when (event.keyCode) {
            // Xbox / gamepad A debe comportarse como OK en la UI de Media3.
            KeyEvent.KEYCODE_BUTTON_A -> {
                super.dispatchKeyEvent(
                    KeyEvent(event.action, KeyEvent.KEYCODE_DPAD_CENTER)
                )
            }

            // Xbox / gamepad B debe conservar el comportamiento de Back.
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    onBackPressedDispatcher.onBackPressed()
                }
                true
            }

            // START se usa como Play/Pause aunque el mando no emita una tecla
            // multimedia estándar.
            KeyEvent.KEYCODE_BUTTON_START -> {
                super.dispatchKeyEvent(
                    KeyEvent(event.action, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                )
            }

            // D-Pad, OK/Enter, Back y teclas multimedia llegan sin alterar al
            // PlayerView actualmente enfocado.
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val device = event.device

        if (!isController(device) || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }

        val hatX = centeredAxis(event, device, MotionEvent.AXIS_HAT_X)
        val hatY = centeredAxis(event, device, MotionEvent.AXIS_HAT_Y)
        val stickX = centeredAxis(event, device, MotionEvent.AXIS_X)
        val stickY = centeredAxis(event, device, MotionEvent.AXIS_Y)

        val x = if (hatX != 0f) hatX else stickX
        val y = if (hatY != 0f) hatY else stickY

        val direction = when {
            abs(x) > abs(y) && x < 0f -> ControllerAction.Left
            abs(x) > abs(y) && x > 0f -> ControllerAction.Right
            y < 0f -> ControllerAction.Up
            y > 0f -> ControllerAction.Down
            else -> null
        }

        if (direction == null) {
            lastDirection = null
            return true
        }

        val now = SystemClock.uptimeMillis()
        val changed = direction != lastDirection
        val canRepeat = now - lastDirectionAt >= REPEAT_MS

        if (changed || canRepeat) {
            if (ControllerInput.passThroughToFocusedView) {
                dispatchSyntheticDpad(direction)
            } else {
                ControllerInput.emit(direction)
            }
            lastDirection = direction
            lastDirectionAt = now
        }

        return true
    }

    private fun dispatchSyntheticDpad(direction: ControllerAction) {
        if (ControllerInput.passThroughToFocusedView) {
            ControllerInput.notifyPlayerControllerInteraction()
        }

        val keyCode = when (direction) {
            ControllerAction.Left -> KeyEvent.KEYCODE_DPAD_LEFT
            ControllerAction.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
            ControllerAction.Up -> KeyEvent.KEYCODE_DPAD_UP
            ControllerAction.Down -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> return
        }

        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun isController(device: InputDevice?): Boolean {
        if (device == null) return false

        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)
    }

    private fun centeredAxis(
        event: MotionEvent,
        device: InputDevice,
        axis: Int
    ): Float {
        val value = event.getAxisValue(axis)
        val range = device.getMotionRange(axis, event.source)
        val flat = range?.flat?.coerceAtLeast(FALLBACK_DEAD_ZONE)
            ?: FALLBACK_DEAD_ZONE

        return if (abs(value) > flat) value else 0f
    }
}
