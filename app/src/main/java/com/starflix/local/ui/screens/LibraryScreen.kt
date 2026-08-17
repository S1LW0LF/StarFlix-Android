package com.starflix.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starflix.local.data.MediaLibraryFolder
import com.starflix.local.data.MediaLibraryVolume
import com.starflix.local.ui.LibraryBrowserUiState
import com.starflix.local.ui.components.controllerFocus
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    permissionGranted: Boolean,
    browser: LibraryBrowserUiState,
    contentFocusRequester: FocusRequester,
    onRequestPermission: () -> Unit,
    onRefreshVolumes: () -> Unit,
    onOpenVolume: (MediaLibraryVolume) -> Unit,
    onOpenFolder: (MediaLibraryFolder) -> Unit,
    onUseCurrentFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Conectar biblioteca",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (browser.selectedVolume == null) {
                "Elige el almacenamiento donde están tus películas. StarFlix detecta el almacenamiento interno y las unidades USB conectadas."
            } else {
                libraryBreadcrumb(browser.selectedVolume, browser.currentPath)
            },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when {
            !permissionGranted -> PermissionPanel(
                contentFocusRequester = contentFocusRequester,
                onRequestPermission = onRequestPermission
            )

            browser.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            browser.selectedVolume == null -> VolumeList(
                volumes = browser.volumes,
                error = browser.error,
                contentFocusRequester = contentFocusRequester,
                onRefresh = onRefreshVolumes,
                onOpenVolume = onOpenVolume
            )

            else -> FolderBrowser(
                browser = browser,
                contentFocusRequester = contentFocusRequester,
                onUseCurrentFolder = onUseCurrentFolder,
                onOpenFolder = onOpenFolder
            )
        }
    }
}

@Composable
private fun PermissionPanel(
    contentFocusRequester: FocusRequester,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF151312),
        border = BorderStroke(1.dp, Color(0xFF3B3734))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF282421)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(18.dp).size(30.dp),
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(22.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Permiso para leer videos",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Android necesita autorizar a StarFlix para consultar las películas guardadas en la TV y en unidades USB. No necesitas instalar una app de archivos.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(26.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .focusRequester(contentFocusRequester)
                    .controllerFocus(
                        shape = RoundedCornerShape(14.dp),
                        focusedScale = 1.05f,
                        strong = true
                    ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 15.dp)
            ) {
                Text("Permitir acceso", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun VolumeList(
    volumes: List<MediaLibraryVolume>,
    error: String?,
    contentFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onOpenVolume: (MediaLibraryVolume) -> Unit
) {
    if (volumes.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF151312)
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        error ?: "No se encontró almacenamiento disponible.",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Si acabas de conectar un USB, espera unos segundos y vuelve a buscar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .focusRequester(contentFocusRequester)
                        .controllerFocus(
                            shape = RoundedCornerShape(14.dp),
                            focusedScale = 1.05f,
                            strong = true
                        ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver a buscar")
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        itemsIndexed(
            items = volumes,
            key = { _, volume -> volume.mediaStoreName }
        ) { index, volume ->
            StorageCard(
                volume = volume,
                modifier = Modifier
                    .then(
                        if (index == 0) Modifier.focusRequester(contentFocusRequester)
                        else Modifier
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    },
                onClick = { onOpenVolume(volume) }
            )
        }
    }
}

@Composable
private fun StorageCard(
    volume: MediaLibraryVolume,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .controllerFocus(shape = shape, focusedScale = 1.025f, strong = true),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF171513),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 22.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = Color(0xFF292522)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.padding(15.dp).size(28.dp),
                tint = Color.White
            )
        }

        Spacer(Modifier.width(20.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = volume.title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = volume.detail,
                fontSize = 14.sp,
                color = Color(0xFFB5ADA7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (volume.totalBytes > 0L) {
            Text(
                text = "${formatBytes(volume.freeBytes)} libres de ${formatBytes(volume.totalBytes)}",
                fontSize = 14.sp,
                color = Color(0xFFB5ADA7)
            )
        }
    }
}

@Composable
private fun FolderBrowser(
    browser: LibraryBrowserUiState,
    contentFocusRequester: FocusRequester,
    onUseCurrentFolder: () -> Unit,
    onOpenFolder: (MediaLibraryFolder) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canUse = browser.totalVideoCount > 0

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 44.dp)
    ) {
        item(key = "use-current") {
            val shape = RoundedCornerShape(18.dp)
            Button(
                onClick = onUseCurrentFolder,
                enabled = canUse,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(contentFocusRequester)
                    .controllerFocus(shape = shape, focusedScale = 1.025f, strong = true),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF2EEE9),
                    contentColor = Color(0xFF11100F),
                    disabledContainerColor = Color(0xFF24211F),
                    disabledContentColor = Color(0xFF77716D)
                ),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        if (browser.currentPath.isBlank()) "Usar todo este almacenamiento" else "Usar esta carpeta",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (canUse) {
                            "${browser.totalVideoCount} video${if (browser.totalVideoCount == 1) "" else "s"} detectado${if (browser.totalVideoCount == 1) "" else "s"}"
                        } else {
                            "No hay videos indexados en esta ubicación"
                        },
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (browser.folders.isNotEmpty()) {
            item(key = "divider") {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CARPETAS",
                        color = Color(0xFF8E8782),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF2B2826))
                }
            }
        }

        itemsIndexed(
            items = browser.folders,
            key = { _, folder -> folder.relativePath }
        ) { index, folder ->
            FolderCard(
                folder = folder,
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused) {
                        // +2: selector actual y divisor/encabezado.
                        val target = index + 2
                        scope.launch {
                            listState.animateScrollToItem(target)
                        }
                    }
                },
                onClick = { onOpenFolder(folder) }
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: MediaLibraryFolder,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(17.dp)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .controllerFocus(shape = shape, focusedScale = 1.02f, strong = true),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF171513),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(17.dp))
        Text(
            text = folder.name,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${folder.videoCount} video${if (folder.videoCount == 1) "" else "s"}",
            color = Color(0xFFB5ADA7),
            fontSize = 14.sp
        )
    }
}

private fun libraryBreadcrumb(
    volume: MediaLibraryVolume,
    path: String
): String {
    if (path.isBlank()) return volume.title
    val prettyPath = path.trim('/').replace("/", " › ")
    return "${volume.title}  ›  $prettyPath"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1024.0) {
        String.format("%.1f TB", gb / 1024.0)
    } else {
        String.format("%.0f GB", gb)
    }
}
