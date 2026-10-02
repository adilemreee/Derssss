package xyz.adilemree.dersdefteri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.serif

/// Sekme ekranlarının iskeleti: büyük serif başlık, kaydırınca küçülür.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
    spacing: Int = 22,
    content: LazyListScope.() -> Unit,
) {
    val c = AppTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.paper,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = actions,
                navigationIcon = navigationIcon,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper, scrolledContainerColor = c.paper),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                header?.invoke()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(spacing.dp),
                    content = content,
                )
            }
        }
    }
}

/// Üstüne açılan ekranların iskeleti: geri oku, ortada başlık.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    bottomBar: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
    spacing: Int = 12,
    content: LazyListScope.() -> Unit,
) {
    val c = AppTheme.colors
    Scaffold(
        containerColor = c.paper,
        topBar = {
            TopAppBar(
                title = { Text(title, style = Type.headline.serif(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(backIcon, "Geri", tint = c.ink) } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper, scrolledContainerColor = c.paper),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                header?.invoke()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(spacing.dp),
                    content = content,
                )
            }
        }
    }
}
