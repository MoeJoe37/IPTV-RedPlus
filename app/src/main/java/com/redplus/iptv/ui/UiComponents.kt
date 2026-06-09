package com.redplus.iptv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import com.redplus.iptv.R
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.ui.theme.PremiumBg
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.ui.theme.PremiumPanel
import com.redplus.iptv.ui.theme.PremiumPanelSoft
import com.redplus.iptv.ui.theme.PremiumRed

@Composable
fun PremiumBackground(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides com.redplus.iptv.ui.theme.PremiumText) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(Color(0xFF181B2A), PremiumBg, Color.Black), radius = 1200f))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
            content()
        }
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, focused: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    val active = focused || hasFocus
    Card(
        modifier = modifier.shadow(if (active) 10.dp else 2.dp, RoundedCornerShape(18.dp)).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).onFocusChanged { hasFocus = it.isFocused }.focusable(),
        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xCC2A3044) else PremiumPanel, contentColor = com.redplus.iptv.ui.theme.PremiumText),
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) PremiumRed else Color.White.copy(alpha = .10f)),
        shape = RoundedCornerShape(18.dp)
    ) { content() }
}

@Composable
fun RedPlusLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.redplus_logo),
            contentDescription = "RedPlus IPTV",
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = PremiumMuted, style = MaterialTheme.typography.bodyMedium)
        }
        action?.invoke()
    }
}

@Composable
fun LoadingState(message: String = "Loading...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = PremiumRed); Spacer(Modifier.height(14.dp)); Text(message, color = PremiumMuted) } }
}

@Composable
fun ErrorState(message: String, detail: String? = null, onRetry: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Something went wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.redplus.iptv.ui.theme.PremiumText)
                Spacer(Modifier.height(8.dp))
                Text(message, color = PremiumMuted)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onRetry != null) Button(onClick = onRetry) { Text("Retry") }
                    if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
                }
                if (!detail.isNullOrBlank()) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide technical info" else "More info") }
                    AnimatedVisibility(expanded) {
                        Text(
                            detail,
                            color = PremiumMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBox(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search") {
    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = modifier, singleLine = true, placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(item: ContentItem, selected: Boolean, favorite: Boolean = false, onFavorite: (() -> Unit)? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), focused = selected, onClick = onClick) {
        Row(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterImage(url = item.image, title = item.title, modifier = Modifier.size(58.dp), square = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(item.channelNumber?.let { "$it. ${item.title}" } ?: item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(item.genre ?: item.year ?: "Tap to play", color = PremiumMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            if (onFavorite != null) IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = if (favorite) PremiumRed else PremiumMuted) }
        }
    }
}

@Composable
fun PosterCard(item: ContentItem, onClick: () -> Unit, onFavorite: (() -> Unit)? = null, favorite: Boolean = false) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(10.dp)) {
            Box { PosterImage(url = item.image, title = item.title, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), square = false)
                if (onFavorite != null) IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .45f), CircleShape)) { Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = if (favorite) PremiumRed else Color.White) }
            }
            Spacer(Modifier.height(8.dp)); Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(listOfNotNull(item.year, item.rating).joinToString(" • ").ifBlank { item.genre ?: "" }, color = PremiumMuted, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PosterImage(url: String?, title: String, modifier: Modifier, square: Boolean) {
    Box(modifier.clip(RoundedCornerShape(if (square) 16.dp else 18.dp)).background(PremiumPanelSoft), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = .65f), fontWeight = FontWeight.Black)
    }
}

@Composable
fun CategorySidebar(categories: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
        items(categories) { (id, name) -> AssistChip(onClick = { onSelect(id) }, label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, border = BorderStroke(1.dp, if (selected == id) PremiumRed else Color.White.copy(alpha = .15f)), modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun PosterGrid(items: List<ContentItem>, onClick: (ContentItem) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(columns = GridCells.Adaptive(118.dp), modifier = modifier, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.type.name + it.id }) { item -> PosterCard(item = item, onClick = { onClick(item) }) }
    }
}
