package com.starflix.local.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starflix.local.R
import com.starflix.local.ui.Destination

@Composable
fun StarFlixTopBar(
    compact: Boolean,
    tvMode: Boolean,
    destination: Destination,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSearch: () -> Unit,
    onConnect: () -> Unit,
    homeFocusRequester: FocusRequester,
    moviesFocusRequester: FocusRequester,
    searchFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onNavigationFocusEntered: () -> Unit = {}
) {
    val horizontalSafePadding = when {
        tvMode -> 42.dp
        compact -> 12.dp
        else -> 24.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalSafePadding,
                vertical = if (tvMode) 12.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tvMode) Modifier else Modifier.widthIn(max = 1220.dp)
                ),
            shape = RoundedCornerShape(if (tvMode) 20.dp else 24.dp),
            color = Color(0xFF151312).copy(alpha = if (tvMode) 0.92f else 0.96f),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (tvMode) 68.dp else 62.dp)
                    .padding(if (tvMode) 9.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(if (tvMode) 15.dp else 18.dp),
                    color = Color(0xFF171516),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = when {
                                tvMode -> 13.dp
                                compact -> 10.dp
                                else -> 12.dp
                            },
                            end = when {
                                tvMode -> 20.dp
                                compact -> 14.dp
                                else -> 18.dp
                            },
                            top = if (tvMode) 7.dp else 6.dp,
                            bottom = if (tvMode) 7.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.starflix_nav_mark),
                            contentDescription = "StarFlix",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(
                                when {
                                    tvMode -> 42.dp
                                    compact -> 36.dp
                                    else -> 39.dp
                                }
                            )
                        )
                        Spacer(Modifier.width(if (tvMode) 9.dp else 7.dp))
                        Image(
                            painter = painterResource(R.drawable.starflix_nav_wordmark),
                            contentDescription = "STARFLIX",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(
                                    when {
                                        tvMode -> 146.dp
                                        compact -> 114.dp
                                        else -> 130.dp
                                    }
                                )
                                .height(
                                    when {
                                        tvMode -> 18.dp
                                        compact -> 14.dp
                                        else -> 16.dp
                                    }
                                )
                        )
                    }
                }

                if (!compact) {
                    Spacer(Modifier.width(if (tvMode) 16.dp else 10.dp))

                    NavPill(
                        selected = destination is Destination.Home,
                        icon = { Icon(Icons.Default.Home, null) },
                        label = "Inicio",
                        onClick = onHome,
                        tvMode = tvMode,
                        modifier = Modifier
                            .focusRequester(homeFocusRequester)
                            .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                            .focusProperties {
                                down = contentFocusRequester
                            }
                    )
                    NavPill(
                        selected = destination is Destination.Movies,
                        icon = { Icon(Icons.Default.LocalMovies, null) },
                        label = "Películas",
                        onClick = onMovies,
                        tvMode = tvMode,
                        modifier = Modifier
                            .focusRequester(moviesFocusRequester)
                            .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                            .focusProperties {
                                down = contentFocusRequester
                            }
                    )
                    NavPill(
                        selected = destination is Destination.Search,
                        icon = { Icon(Icons.Default.Search, null) },
                        label = "Buscar",
                        onClick = onSearch,
                        tvMode = tvMode,
                        modifier = Modifier
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                            .focusProperties {
                                down = contentFocusRequester
                            }
                    )
                }

                Spacer(Modifier.weight(1f))

                if (compact) {
                    val connectShape = RoundedCornerShape(50)

                    FilledTonalIconButton(
                        onClick = onConnect,
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                            .focusProperties {
                                down = contentFocusRequester
                            }
                            .controllerFocus(
                                shape = connectShape,
                                focusedScale = 1.018f
                            )
                    ) {
                        Icon(Icons.Default.FolderOpen, "Conectar biblioteca")
                    }
                } else {
                    val connectShape = RoundedCornerShape(if (tvMode) 14.dp else 16.dp)

                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                            .focusProperties {
                                down = contentFocusRequester
                            }
                            .controllerFocus(
                                shape = connectShape,
                                focusedScale = if (tvMode) 1.035f else 1.018f,
                                strong = tvMode
                            ),
                        shape = connectShape,
                        contentPadding = PaddingValues(
                            horizontal = if (tvMode) 20.dp else 18.dp,
                            vertical = if (tvMode) 13.dp else 12.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(if (tvMode) 20.dp else 18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Conectar biblioteca",
                            fontSize = if (tvMode) 15.sp else 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    tvMode: Boolean,
    modifier: Modifier = Modifier
) {
    val navShape = RoundedCornerShape(if (tvMode) 13.dp else 14.dp)

    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (tvMode) 48.dp else 0.dp)
            .controllerFocus(
                shape = navShape,
                focusedScale = if (tvMode) 1.045f else 1.015f,
                strong = tvMode
            ),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color(0xFF2B2826) else Color.Transparent,
            contentColor = if (selected) Color.White else Color(0xFFAAA39E)
        ),
        shape = navShape,
        contentPadding = PaddingValues(
            horizontal = if (tvMode) 15.dp else 12.dp,
            vertical = if (tvMode) 11.dp else 8.dp
        )
    ) {
        icon()
        Spacer(Modifier.width(if (tvMode) 8.dp else 6.dp))
        Text(
            label,
            fontSize = if (tvMode) 15.sp else 14.sp,
            fontWeight = if (tvMode) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun StarFlixBottomBar(
    destination: Destination,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSearch: () -> Unit,
    homeFocusRequester: FocusRequester,
    moviesFocusRequester: FocusRequester,
    searchFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onNavigationFocusEntered: () -> Unit = {}
) {
    NavigationBar(
        containerColor = Color(0xFF151312)
    ) {
        val bottomNavShape = RoundedCornerShape(99.dp)

        NavigationBarItem(
            modifier = Modifier
                .focusRequester(homeFocusRequester)
                .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                .focusProperties {
                    up = contentFocusRequester
                }
                .controllerFocus(
                    shape = bottomNavShape,
                    focusedScale = 1.015f
                ),
            selected = destination is Destination.Home,
            onClick = onHome,
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Inicio") }
        )
        NavigationBarItem(
            modifier = Modifier
                .focusRequester(moviesFocusRequester)
                .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                .focusProperties {
                    up = contentFocusRequester
                }
                .controllerFocus(
                    shape = bottomNavShape,
                    focusedScale = 1.015f
                ),
            selected = destination is Destination.Movies,
            onClick = onMovies,
            icon = { Icon(Icons.Default.LocalMovies, null) },
            label = { Text("Películas") }
        )
        NavigationBarItem(
            modifier = Modifier
                .focusRequester(searchFocusRequester)
                .onFocusChanged { if (it.isFocused) onNavigationFocusEntered() }
                .focusProperties {
                    up = contentFocusRequester
                }
                .controllerFocus(
                    shape = bottomNavShape,
                    focusedScale = 1.015f
                ),
            selected = destination is Destination.Search,
            onClick = onSearch,
            icon = { Icon(Icons.Default.Search, null) },
            label = { Text("Buscar") }
        )
    }
}
