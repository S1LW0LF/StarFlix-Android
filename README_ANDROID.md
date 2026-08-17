# StarFlix Android v0.11.6

- `versionName = 0.11.6`
- `versionCode = 31`
- Base: StarFlix Android v0.10.11
- Incluye las mejoras comunes desarrolladas durante la rama de TV hasta v0.11.9, adaptadas para móvil/tablet.

## Cambios principales

## Branding v0.11.6

- La barra de navegación usa el emblema de estrella/play proporcionado por el usuario.
- El título ya no se renderiza con una fuente aproximada: usa el wordmark **STARFLIX** extraído del arte original, conservando exactamente su tipografía, espaciado y degradado blanco/rosa/morado.
- El wordmark se guarda con fondo transparente y se escala con `ContentScale.Fit`, sin deformación.


## Corrección v0.11.5 — foco del reproductor Android

- Corrige el rectángulo blanco gigante que aparecía al enfocar algunos controles de Media3 con el mando.
- El fondo blanco ahora se aplica únicamente al control enfocado; nunca al contenedor padre de Media3.
- Cuando los controles se ocultan por timeout, cualquier nueva entrada del mando vuelve a mostrarlos y restaura un foco válido.
- Se conserva touch, reproducción, orientación vertical/horizontal y navegación con Xbox.

## Corrección v0.11.3 — reproductor con mando

- En móvil/tablet, un Xbox/gamepad ahora entrega D-Pad y OK directamente a `PlayerView` mientras el reproductor está visible.
- Se puede navegar por Play/Pause, retroceder, adelantar, timeline, subtítulos y ajustes/pistas con el mando.
- Xbox **A** actúa como OK, **B** vuelve y **START** actúa como Play/Pause.
- El stick analógico continúa convirtiéndose a D-Pad.
- Si hay un mando conectado al abrir el reproductor, Play/Pause recibe el foco inicial y se usa el estilo de foco de alto contraste.
- Touch permanece operativo: la ruta especial solo procesa eventos provenientes de dispositivos GAMEPAD/JOYSTICK/DPAD.


- Se conserva **Seguir viendo → reproducir directamente**.
- Mejoras del reproductor Media3: controles de retroceso/avance, subtítulos, scrubbing de la barra y mejor compatibilidad con mando/gamepad.
- Mejor conservación del estado de navegación al abrir Detalle/Reproductor y regresar.
- Restauración por `movieId` en Películas y Buscar; la infraestructura de restauración de Inicio también queda integrada para uso con mando.
- Historial de navegación Detalle → Player → Detalle → pantalla de origen preservado.
- Mejoras acumuladas de estabilidad en carruseles/cuadrículas y navegación con control físico.
- La barra de progreso sigue mostrándose únicamente en **Seguir viendo**.
- Móvil/tablet mantiene **Storage Access Framework (selector de carpetas del sistema)** para Conectar biblioteca.
- Las personalizaciones exclusivas de Android TV (LEANBACK launcher y banner de TV) no se incluyen. El estilo de foco de alto contraste del reproductor sí se activa cuando Android detecta un mando físico.

## Preparar en Windows

Ejecuta:

```bat
PREPARAR_STARFLIX_ANDROID.bat
```

El script configura `local.properties` automáticamente si encuentra el Android SDK en:

```text
%LOCALAPPDATA%\Android\Sdk
```

y descarga `gradle-wrapper.jar` si falta.

## Compilar APK debug

```powershell
.\gradlew.bat clean assembleDebug
```

APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```


## Correcciones acumuladas en v0.11.2

- Se mantiene el comportamiento de reproducción móvil/tablet probado en v0.10.11.
- Las adaptaciones de PlayerView para TV solo se activan cuando el dispositivo realmente es TV.
- La app permite orientación vertical y horizontal (`screenOrientation=unspecified`).

### Navegación y foco

- Xbox/mandos: B/Back desde Inicio, Películas o Buscar devuelve el foco a la pestaña activa de navegación sin cambiar de sección.
- En móvil vertical, la barra inferior se navega con Left/Right y Up vuelve al contenido; en tablet/horizontal, Down vuelve al contenido desde la barra superior.
- Inicio hereda la navegación vertical robusta de TV v0.11.9: Hero ↔ Seguir viendo ↔ Biblioteca, alineando la sección completa para evitar carruseles/textos cortados.
- Se restaura por `movieId` la película enfocada al volver desde detalle/reproductor cuando se está usando mando.
- Conserva reproducción móvil estable de v0.10.11, touch y rotación vertical/horizontal.


## v0.11.5
- Nuevo emblema StarFlix en la barra de navegación usando el arte proporcionado, recortado sin deformación y con fondo transparente.
- El emblema conserva su relación de aspecto y se acompaña del nombre StarFlix para mantener legibilidad en móvil/tablet.
