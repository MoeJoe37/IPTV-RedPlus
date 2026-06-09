package com.redplus.iptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.local.FavoriteEntity
import com.redplus.iptv.data.local.WatchHistoryEntity
import com.redplus.iptv.data.model.CategoryItem
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.data.repository.AuthRepository
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.ui.theme.PremiumRed
import com.redplus.iptv.util.containsNormalized
import com.redplus.iptv.util.msToTime
import com.redplus.iptv.util.unixToLocalTime
import com.redplus.iptv.util.xtreamExpiryToText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Data<T>(val value: T) : LoadState<T>
    data class Error(val message: String, val detail: String? = null) : LoadState<Nothing>
}

@Composable
private fun <T> rememberLoadState(vararg keys: Any?, block: suspend () -> T): State<LoadState<T>> = produceState<LoadState<T>>(initialValue = LoadState.Loading, *keys) {
    value = LoadState.Loading
    value = try {
        LoadState.Data(block())
    } catch (e: Exception) {
        LoadState.Error(e.message ?: "Unknown error", e.stackTraceToString())
    }
}

private fun play(item: ContentItem, navigate: (String) -> Unit) {
    SharedSelection.item = item
    navigate(Routes.Player)
}

private fun details(item: ContentItem, route: String, navigate: (String) -> Unit) {
    SharedSelection.item = item
    navigate(route)
}

private fun epg(item: ContentItem, navigate: (String) -> Unit) {
    SharedSelection.item = item
    navigate(Routes.Epg)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(authRepository: AuthRepository, onLoginSuccess: (Session) -> Unit) {
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RedPlusLogo(Modifier.fillMaxWidth().height(64.dp))
                Text("Legal Xtream IPTV Login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Use only licensed IPTV accounts and content.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Server URL") }, placeholder = { Text("http://example.com:8080") })
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") })
                OutlinedTextField(
                    pass,
                    { pass = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberMe, { rememberMe = it })
                    Text("Remember me")
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        authRepository.login(server, user, pass, rememberMe)
                            .onSuccess { onLoginSuccess(it) }
                            .onFailure { error = it.message ?: "Login failed" }
                        loading = false
                    }
                }) { Text(if (loading) "Validating..." else "Login") }
            }
        }
    }
}

@Composable
fun DashboardScreen(session: Session, container: AppContainer, navigate: (String) -> Unit) {
    val continueWatching by container.libraryRepository.continueWatching(session.accountKey).collectAsStateWithLifecycle(emptyList())
    val recent by container.libraryRepository.recent(session.accountKey).collectAsStateWithLifecycle(emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RedPlusLogo(Modifier.width(94.dp).height(46.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hi, ${session.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${session.status} • Expiry: ${xtreamExpiryToText(session.expDate)}", color = PremiumMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Connections ${session.activeConnections ?: "0"}/${session.maxConnections ?: "?"}", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { navigate(Routes.Search) }) { Text("Search") }
            }
        }
        item { DashboardCards(navigate) }
        item { HistoryRow("Continue Watching", continueWatching, navigate) }
        item { HistoryRow("Recently Watched", recent, navigate) }
    }
}

@Composable
private fun DashboardCards(navigate: (String) -> Unit) {
    val cards = listOf(
        "Live TV" to Routes.Live,
        "Events" to Routes.Events,
        "Movies" to Routes.Movies,
        "Series" to Routes.Series,
        "Favorites" to Routes.Favorites,
        "History" to Routes.History,
        "Settings" to Routes.Settings
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (title, route) ->
                    GlassPanel(Modifier.weight(1f).height(78.dp), onClick = { navigate(route) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Open", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryRow(title: String, rows: List<WatchHistoryEntity>, navigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, if (rows.isEmpty()) "Nothing yet" else "${rows.size} item(s)")
        if (rows.isEmpty()) {
            Text("Your watched channels and resume positions will appear here.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows) { h ->
                    val item = h.toItem()
                    GlassPanel(Modifier.width(126.dp), onClick = { play(item, navigate) }) {
                        Column(Modifier.padding(8.dp)) {
                            PosterImage(h.image, h.title, Modifier.fillMaxWidth().height(138.dp), false)
                            Spacer(Modifier.height(6.dp))
                            Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("${msToTime(h.positionMs)}", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTvScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ContentItem?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val favs by container.libraryRepository.favoritesByType(session.accountKey, ContentType.LIVE).collectAsStateWithLifecycle(emptyList())
    val categoriesState by rememberLoadState(session.accountKey, refresh) { container.contentRepository.liveCategories(session, refresh > 0) }
    val streamsState by rememberLoadState(session.accountKey, selectedCategory, refresh) { container.contentRepository.liveStreams(session, selectedCategory, refresh > 0) }

    ContentChrome("Live TV", onBack, onRefresh = { refresh++ }) {
        when {
            categoriesState is LoadState.Error -> ErrorState((categoriesState as LoadState.Error).message, (categoriesState as LoadState.Error).detail, { refresh++ }, onBack)
            streamsState is LoadState.Loading -> LoadingState("Loading channels...")
            streamsState is LoadState.Error -> ErrorState((streamsState as LoadState.Error).message, (streamsState as LoadState.Error).detail, { refresh++ }, onBack)
            categoriesState is LoadState.Data && streamsState is LoadState.Data -> {
                val categories = listOf("all" to "All") + (categoriesState as LoadState.Data<List<CategoryItem>>).value.map { it.id to it.name }
                val streams = (streamsState as LoadState.Data<List<ContentItem>>).value.filter { query.isBlank() || it.title.containsNormalized(query) }
                if (selected == null && streams.isNotEmpty()) selected = streams.first()
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth < 700.dp) {
                        PhoneContentList(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategory = { selectedCategory = it; selected = null },
                            query = query,
                            onQuery = { query = it },
                            placeholder = "Search channels",
                            rows = streams,
                            row = { item ->
                                ChannelRow(
                                    item = item,
                                    selected = item.id == selected?.id,
                                    favorite = favs.any { it.itemId == item.id },
                                    onFavorite = { scope.launch { container.libraryRepository.toggleFavorite(session.accountKey, item) } },
                                    onClick = { selected = item; play(item, navigate) },
                                    onLongClick = { epg(item, navigate) }
                                )
                            }
                        )
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategorySidebar(categories, selectedCategory, { selectedCategory = it; selected = null }, Modifier.width(220.dp))
                            Column(Modifier.weight(1f)) {
                                SearchBox(query, { query = it }, Modifier.fillMaxWidth(), "Search channels")
                                Spacer(Modifier.height(10.dp))
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(streams, key = { it.id }) { item ->
                                        ChannelRow(
                                            item = item,
                                            selected = item.id == selected?.id,
                                            favorite = favs.any { it.itemId == item.id },
                                            onFavorite = { scope.launch { container.libraryRepository.toggleFavorite(session.accountKey, item) } },
                                            onClick = { selected = item; play(item, navigate) },
                                            onLongClick = { epg(item, navigate) }
                                        )
                                    }
                                }
                            }
                            SelectedChannelPanel(selected, navigate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedChannelPanel(selected: ContentItem?, navigate: (String) -> Unit) {
    GlassPanel(Modifier.width(260.dp).fillMaxSize()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PosterImage(selected?.image, selected?.title ?: "Channel", Modifier.fillMaxWidth().height(140.dp), true)
            Text(selected?.title ?: "Select a channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Long press a channel to open EPG.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
            Button(enabled = selected != null, onClick = { selected?.let { play(it, navigate) } }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
            Button(enabled = selected != null, onClick = { selected?.let { epg(it, navigate) } }, modifier = Modifier.fillMaxWidth()) { Text("EPG") }
        }
    }
}

@Composable
private fun PhoneContentList(
    categories: List<Pair<String, String>>,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    rows: List<ContentItem>,
    row: @Composable (ContentItem) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryStrip(categories, selectedCategory, onCategory)
        SearchBox(query, onQuery, Modifier.fillMaxWidth(), placeholder)
        Text("${rows.size} result(s)", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(rows, key = { it.type.name + it.id }) { item -> row(item) }
        }
    }
}

@Composable
private fun CategoryStrip(categories: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(categories) { (id, name) ->
            AssistChip(
                onClick = { onSelect(id) },
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                border = BorderStroke(1.dp, if (selected == id) PremiumRed else Color.White.copy(alpha = .16f))
            )
        }
    }
}

@Composable
fun EventsScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val state by rememberLoadState(session.accountKey, refresh) { container.contentRepository.eventStreams(session, refresh > 0) }
    ContentChrome("PPV / Events", onBack, onRefresh = { refresh++ }) {
        when (state) {
            LoadState.Loading -> LoadingState("Detecting event channels...")
            is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail, { refresh++ }, onBack)
            is LoadState.Data -> {
                val items = (state as LoadState.Data<List<ContentItem>>).value.filter { query.isBlank() || it.title.containsNormalized(query) }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBox(query, { query = it }, Modifier.fillMaxWidth(), "Search events")
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(items, key = { it.id }) { item ->
                            GlassPanel(Modifier.fillMaxWidth(), onClick = { play(item, navigate) }) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PosterImage(item.image, item.title, Modifier.size(58.dp), true)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Detected event channel", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { play(item, navigate) }) { Text("Watch") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviesScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) = CatalogScreen("Movies", session, container, navigate, onBack, movies = true)

@Composable
fun SeriesScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) = CatalogScreen("Series", session, container, navigate, onBack, movies = false)

@Composable
private fun CatalogScreen(title: String, session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit, movies: Boolean) {
    var selectedCategory by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("A-Z") }
    var refresh by remember { mutableIntStateOf(0) }
    val categoriesState by rememberLoadState(session.accountKey, movies, refresh) { if (movies) container.contentRepository.vodCategories(session, refresh > 0) else container.contentRepository.seriesCategories(session, refresh > 0) }
    val itemsState by rememberLoadState(session.accountKey, movies, selectedCategory, refresh) { if (movies) container.contentRepository.vodStreams(session, selectedCategory, refresh > 0) else container.contentRepository.series(session, selectedCategory, refresh > 0) }

    ContentChrome(title, onBack, onRefresh = { refresh++ }) {
        when {
            categoriesState is LoadState.Loading || itemsState is LoadState.Loading -> LoadingState("Loading catalog...")
            categoriesState is LoadState.Error -> ErrorState((categoriesState as LoadState.Error).message, (categoriesState as LoadState.Error).detail, { refresh++ }, onBack)
            itemsState is LoadState.Error -> ErrorState((itemsState as LoadState.Error).message, (itemsState as LoadState.Error).detail, { refresh++ }, onBack)
            else -> {
                val categories = listOf("all" to "All") + (categoriesState as LoadState.Data<List<CategoryItem>>).value.map { it.id to it.name }
                var filtered = (itemsState as LoadState.Data<List<ContentItem>>).value.filter { query.isBlank() || it.title.containsNormalized(query) }
                filtered = when (sort) {
                    "Year" -> filtered.sortedByDescending { it.year }
                    "Rating" -> filtered.sortedByDescending { it.rating }
                    else -> filtered.sortedBy { it.title }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth < 700.dp) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryStrip(categories, selectedCategory) { selectedCategory = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchBox(query, { query = it }, Modifier.weight(1f), "Search $title")
                                SortButton(sort, { sort = it })
                            }
                            Text("${filtered.size} item(s)", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                            PosterGrid(filtered, onClick = { details(it, if (movies) Routes.MovieDetails else Routes.SeriesDetails, navigate) }, modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategorySidebar(categories, selectedCategory, { selectedCategory = it }, Modifier.width(220.dp))
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SearchBox(query, { query = it }, Modifier.weight(1f), "Search $title")
                                    SortButton(sort, { sort = it })
                                }
                                PosterGrid(filtered, onClick = { details(it, if (movies) Routes.MovieDetails else Routes.SeriesDetails, navigate) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortButton(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Button(onClick = { open = true }) { Text(value) }
        DropdownMenu(open, { open = false }) {
            listOf("A-Z", "Recently added", "Year", "Rating").forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { onChange(it); open = false })
            }
        }
    }
}

@Composable
fun MovieDetailsScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    val item = SharedSelection.item ?: return ErrorState("No movie selected.", onBack = onBack)
    val state by rememberLoadState(session.accountKey, item.id) { container.contentRepository.vodInfo(session, item.id) }
    ContentChrome("Movie Details", onBack) {
        when (state) {
            LoadState.Loading -> LoadingState("Loading movie details...")
            is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail, onBack = onBack)
            is LoadState.Data -> {
                val info = (state as LoadState.Data<com.redplus.iptv.data.remote.VodInfoResponse>).value.info
                DetailLayout(
                    item.copy(plot = info?.plot ?: item.plot, genre = info?.genre ?: item.genre, rating = info?.rating ?: item.rating, image = info?.movieImage ?: item.image),
                    onPlay = { play(item, navigate) },
                    extra = listOfNotNull(info?.releaseDate, info?.duration, info?.cast).joinToString("\n")
                )
            }
        }
    }
}

@Composable
fun SeriesDetailsScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    val item = SharedSelection.item ?: return ErrorState("No series selected.", onBack = onBack)
    val state by rememberLoadState(session.accountKey, item.id) { container.contentRepository.seriesInfo(session, item.id) }
    ContentChrome("Series Details", onBack) {
        when (state) {
            LoadState.Loading -> LoadingState("Loading seasons and episodes...")
            is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail, onBack = onBack)
            is LoadState.Data -> {
                val info = (state as LoadState.Data<com.redplus.iptv.data.remote.SeriesInfoResponse>).value
                val episodesState by rememberLoadState(item.id, info) { container.contentRepository.episodeItems(info) }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item {
                        DetailLayout(
                            item.copy(plot = info.info?.plot ?: item.plot, genre = info.info?.genre ?: item.genre, rating = info.info?.rating ?: item.rating, image = info.info?.cover ?: item.image),
                            onPlay = { if (episodesState is LoadState.Data && (episodesState as LoadState.Data<List<ContentItem>>).value.isNotEmpty()) play((episodesState as LoadState.Data<List<ContentItem>>).value.first(), navigate) },
                            extra = "Seasons: ${info.seasons?.size ?: 0}"
                        )
                    }
                    if (episodesState is LoadState.Data) {
                        items((episodesState as LoadState.Data<List<ContentItem>>).value, key = { it.id }) { ep ->
                            ChannelRow(ep, false, onClick = { play(ep, navigate) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLayout(item: ContentItem, onPlay: () -> Unit, extra: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 650.dp) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterImage(item.image, item.title, Modifier.width(156.dp).height(228.dp), false)
                    Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(listOfNotNull(item.year, item.rating, item.genre).joinToString(" • "), color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    Text(item.plot ?: "No description available.", maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (extra.isNotBlank()) Text(extra, color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play / Resume") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PosterImage(item.image, item.title, Modifier.width(220.dp).height(330.dp), false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(listOfNotNull(item.year, item.rating, item.genre).joinToString(" • "), color = PremiumMuted)
                    Text(item.plot ?: "No description available.")
                    if (extra.isNotBlank()) Text(extra, color = PremiumMuted)
                    Button(onClick = onPlay) { Text("Play / Resume") }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Channels" to ContentType.LIVE, "Events" to ContentType.EVENT, "Movies" to ContentType.MOVIE, "Shows" to ContentType.SERIES)
    val rows by container.libraryRepository.favoritesByType(session.accountKey, tabs[tab].second).collectAsStateWithLifecycle(emptyList())
    ContentChrome("Favorites", onBack) {
        Column(Modifier.fillMaxSize()) {
            TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(selected = i == tab, onClick = { tab = i }, text = { Text(t.first) }) } }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(rows) { f ->
                    ChannelRow(f.toItem(), false, favorite = true, onClick = {
                        val item = f.toItem()
                        if (item.type == ContentType.MOVIE) details(item, Routes.MovieDetails, navigate)
                        else if (item.type == ContentType.SERIES) details(item, Routes.SeriesDetails, navigate)
                        else play(item, navigate)
                    })
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    val rows by container.libraryRepository.recent(session.accountKey).collectAsStateWithLifecycle(emptyList())
    ContentChrome("Recently Watched", onBack) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(rows) { h -> ChannelRow(h.toItem(), false, onClick = { play(h.toItem(), navigate) }) }
        }
    }
}

@Composable
fun GlobalSearchScreen(session: Session, container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(350); debounced = query }
    val state by rememberLoadState(session.accountKey, debounced) {
        if (debounced.isBlank()) emptyList() else
            container.contentRepository.liveStreams(session).take(2000) +
                container.contentRepository.eventStreams(session).take(400) +
                container.contentRepository.vodStreams(session).take(2000) +
                container.contentRepository.series(session).take(2000)
    }
    val scope = rememberCoroutineScope()
    ContentChrome("Search", onBack) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBox(query, { query = it }, Modifier.fillMaxWidth(), "Search everything")
            when (state) {
                LoadState.Loading -> LoadingState("Searching...")
                is LoadState.Error -> ErrorState((state as LoadState.Error).message)
                is LoadState.Data -> {
                    val results = (state as LoadState.Data<List<ContentItem>>).value.filter { it.title.containsNormalized(debounced) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(results.take(200), key = { it.type.name + it.id }) { item ->
                            ChannelRow(item, false, onClick = {
                                scope.launch { container.libraryRepository.saveSearch(session.accountKey, debounced) }
                                if (item.type == ContentType.MOVIE) details(item, Routes.MovieDetails, navigate)
                                else if (item.type == ContentType.SERIES) details(item, Routes.SeriesDetails, navigate)
                                else play(item, navigate)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpgScreen(session: Session, container: AppContainer, onBack: () -> Unit) {
    val item = SharedSelection.item ?: return ErrorState("No channel selected.", onBack = onBack)
    val state by rememberLoadState(session.accountKey, item.id) { container.contentRepository.epg(session, item.id, 20) }
    ContentChrome("EPG", onBack) {
        when (state) {
            LoadState.Loading -> LoadingState("Loading guide...")
            is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail, onBack = onBack)
            is LoadState.Data -> {
                val rows = (state as LoadState.Data<com.redplus.iptv.data.remote.EpgResponse>).value.listings.orEmpty()
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(rows) { epg ->
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${unixToLocalTime(epg.startTimestamp)} - ${unixToLocalTime(epg.stopTimestamp)}", color = PremiumRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(epg.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(epg.description.ifBlank { "No description." }, color = PremiumMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(session: Session, container: AppContainer, onLogout: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    ContentChrome("Settings", onBack) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SettingsSection("Account", listOf("Server: ${session.serverUrl}", "Username: ${session.username}", "Status: ${session.status}", "Expiry: ${xtreamExpiryToText(session.expDate)}")) }
            item { SettingsSection("Player", listOf("Resize mode: Fit / Fill / Zoom / Stretch", "HTTP and HTTPS streams supported", "Audio/subtitle tracks show when provided by the stream", "Brightness and volume gestures enabled")) }
            item { SettingsSection("UI", listOf("Compact phone layout", "Dark glass panels", "Fast lazy lists and cached metadata")) }
            item { SettingsSection("Data", listOf("Cache is local", "Favorites and history are stored per account")) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { container.libraryRepository.clearCache() } }, modifier = Modifier.fillMaxWidth()) { Text("Clear cache") }
                    Button(onClick = { scope.launch { container.libraryRepository.clearFavorites(session.accountKey) } }, modifier = Modifier.fillMaxWidth()) { Text("Clear favorites") }
                    Button(onClick = { scope.launch { container.libraryRepository.clearHistory(session.accountKey) } }, modifier = Modifier.fillMaxWidth()) { Text("Clear history") }
                }
            }
            item { Button(onClick = { scope.launch { container.authRepository.logout(); onLogout() } }, modifier = Modifier.fillMaxWidth()) { Text("Logout / Clear saved account") } }
            item { Text("RedPlus IPTV v1.0.1 • Legal player only. No content, playlists, or IPTV sources are included.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, lines: List<String>) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEach { Text(it, color = PremiumMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ContentChrome(title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onRefresh != null) TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        content()
    }
}

private fun FavoriteEntity.toItem(): ContentItem = ContentItem(itemId, runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.LIVE), title, image, categoryId, streamExtension = streamExtension)
private fun WatchHistoryEntity.toItem(): ContentItem = ContentItem(itemId, runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.LIVE), title, image, categoryId, streamExtension = streamExtension)
