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
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.CategoryItem
import com.redplus.iptv.data.model.ContentLoadingStrategy
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
import kotlinx.coroutines.CancellationException
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
    } catch (e: CancellationException) {
        throw e
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val categoriesState by rememberLoadState(session.accountKey, refresh) { container.contentRepository.liveCategories(session, refresh > 0) }
    val rawLiveCategories = (categoriesState as? LoadState.Data<List<CategoryItem>>)?.value.orEmpty()
    val rawLiveCategoryKey = rawLiveCategories.joinToString("|") { it.id }
    val liveRealIds = realCategoryIds("live", rawLiveCategories, settings, selectedCategory)
    val liveApiCategory = if (selectedCategory == "all" || selectedCategory.startsWith("group::")) null else selectedCategory
    val streamsState by rememberLoadState(session.accountKey, selectedCategory, refresh, rawLiveCategoryKey, settings.hiddenCategoryKeys, settings.categoryGroupMap) {
        val base = container.contentRepository.liveStreams(session, liveApiCategory, refresh > 0)
        when {
            selectedCategory == "all" -> base.filter { it.visibleForCategorySettings("live", settings) }
            selectedCategory.startsWith("group::") -> base.filter { it.categoryId != null && it.categoryId in liveRealIds }
            else -> base.filter { it.visibleForCategorySettings("live", settings) }
        }
    }

    ContentChrome("Live TV", onBack, onRefresh = { refresh++ }) {
        when {
            categoriesState is LoadState.Error -> ErrorState((categoriesState as LoadState.Error).message, (categoriesState as LoadState.Error).detail, { refresh++ }, onBack)
            streamsState is LoadState.Loading -> LoadingState("Loading channels...")
            streamsState is LoadState.Error -> ErrorState((streamsState as LoadState.Error).message, (streamsState as LoadState.Error).detail, { refresh++ }, onBack)
            categoriesState is LoadState.Data && streamsState is LoadState.Data -> {
                val categoryOptions = managedCategories("live", (categoriesState as LoadState.Data<List<CategoryItem>>).value, settings, includeAll = true)
                val categories = categoryOptions.map { it.id to repairText(it.name, settings.useUtf8Decode) }
                if (categories.none { it.first == selectedCategory } && categories.isNotEmpty()) selectedCategory = categories.first().first
                val streams = (streamsState as LoadState.Data<List<ContentItem>>).value.map { it.repairText(settings.useUtf8Decode) }.filter { query.isBlank() || it.title.containsNormalized(query) }
                if (selected == null && streams.isNotEmpty()) selected = streams.first()
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (settings.tvViewMode) {
                        TvContentList(
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
                    } else if (maxWidth < 700.dp) {
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val state by rememberLoadState(session.accountKey, refresh, settings.hiddenCategoryKeys) { container.contentRepository.eventStreams(session, refresh > 0).filter { it.visibleForCategorySettings("live", settings) } }
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val runtimeSection = settings.contentLoadingStrategy == ContentLoadingStrategy.RUNTIME && if (movies) settings.useRuntimeMovies else settings.useRuntimeSeries
    val categoryKind = if (movies) "vod" else "series"
    val categoriesState by rememberLoadState(session.accountKey, movies, refresh) { if (movies) container.contentRepository.vodCategories(session, refresh > 0) else container.contentRepository.seriesCategories(session, refresh > 0) }
    val rawCatalogCategories = (categoriesState as? LoadState.Data<List<CategoryItem>>)?.value.orEmpty()
    val rawCatalogCategoryKey = rawCatalogCategories.joinToString("|") { it.id }
    val catalogRealIds = realCategoryIds(categoryKind, rawCatalogCategories, settings, selectedCategory)
    val catalogApiCategory = if (selectedCategory == "all" || selectedCategory.startsWith("group::")) null else selectedCategory
    val skipRuntimeAll = runtimeSection && selectedCategory == "all"
    val itemsState by rememberLoadState(session.accountKey, movies, selectedCategory, refresh, rawCatalogCategoryKey, settings.hiddenCategoryKeys, settings.categoryGroupMap) {
        if (skipRuntimeAll) emptyList()
        else {
            val base = if (movies) container.contentRepository.vodStreams(session, catalogApiCategory, refresh > 0) else container.contentRepository.series(session, catalogApiCategory, refresh > 0)
            when {
                selectedCategory == "all" -> base.filter { it.visibleForCategorySettings(categoryKind, settings) }
                selectedCategory.startsWith("group::") -> base.filter { it.categoryId != null && it.categoryId in catalogRealIds }
                else -> base.filter { it.visibleForCategorySettings(categoryKind, settings) }
            }
        }
    }
    LaunchedEffect(categoriesState, runtimeSection, settings.hiddenCategoryKeys, settings.categoryGroupMap) {
        if (categoriesState is LoadState.Data) {
            val options = managedCategories(categoryKind, (categoriesState as LoadState.Data<List<CategoryItem>>).value, settings, includeAll = !runtimeSection)
            if ((runtimeSection && selectedCategory == "all") || options.none { it.id == selectedCategory }) {
                options.firstOrNull()?.id?.let { selectedCategory = it }
            }
        }
    }

    ContentChrome(title, onBack, onRefresh = { refresh++ }) {
        when {
            categoriesState is LoadState.Loading || itemsState is LoadState.Loading -> LoadingState("Loading catalog...")
            categoriesState is LoadState.Error -> ErrorState((categoriesState as LoadState.Error).message, (categoriesState as LoadState.Error).detail, { refresh++ }, onBack)
            itemsState is LoadState.Error -> ErrorState((itemsState as LoadState.Error).message, (itemsState as LoadState.Error).detail, { refresh++ }, onBack)
            else -> {
                val categories = managedCategories(categoryKind, (categoriesState as LoadState.Data<List<CategoryItem>>).value, settings, includeAll = !runtimeSection).map { it.id to repairText(it.name, settings.useUtf8Decode) }
                var filtered = (itemsState as LoadState.Data<List<ContentItem>>).value.map { it.repairText(settings.useUtf8Decode) }.filter { query.isBlank() || it.title.containsNormalized(query) }
                filtered = when (sort) {
                    "Year" -> filtered.sortedByDescending { it.year }
                    "Rating" -> filtered.sortedByDescending { it.rating }
                    else -> filtered.sortedBy { it.title }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (settings.tvViewMode) {
                        TvCatalogView(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategory = { selectedCategory = it },
                            query = query,
                            onQuery = { query = it },
                            title = title,
                            sort = sort,
                            onSort = { sort = it },
                            items = filtered,
                            onItemClick = { details(it, if (movies) Routes.MovieDetails else Routes.SeriesDetails, navigate) }
                        )
                    } else if (maxWidth < 700.dp) {
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
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
                    val results = (state as LoadState.Data<List<ContentItem>>).value.filter { it.title.containsNormalized(debounced) }.filter {
                        when (it.type) {
                            ContentType.LIVE, ContentType.EVENT -> it.visibleForCategorySettings("live", settings)
                            ContentType.MOVIE -> it.visibleForCategorySettings("vod", settings)
                            ContentType.SERIES, ContentType.EPISODE -> it.visibleForCategorySettings("series", settings)
                        }
                    }
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val state by rememberLoadState(session.accountKey, item.id, settings.liveTvEpg, settings.externalXmlTvUrl) { container.contentRepository.timeline(session, item, settings, 30) }
    ContentChrome("EPG", onBack) {
        when (state) {
            LoadState.Loading -> LoadingState("Loading guide...")
            is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail, onBack = onBack)
            is LoadState.Data -> {
                val rows = (state as LoadState.Data<List<com.redplus.iptv.data.model.EpgProgram>>).value
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (settings.liveTvEpg) "Source: provider Xtream EPG or optional XMLTV URL" else "Live TV EPG is disabled in Settings > Runtimes", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rows.isEmpty()) {
                        item { SettingsSection("No schedule found", listOf("This provider did not return EPG for this channel, or the optional XMLTV feed could not match its channel name/EPG ID.")) }
                    }
                    items(rows) { epg ->
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${unixToLocalTime(epg.startTimestamp)} - ${unixToLocalTime(epg.stopTimestamp)} • ${epg.source}", color = PremiumRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var tab by remember { mutableIntStateOf(0) }
    fun save(next: AppSettings) { scope.launch { container.sessionStore.updateSettings(next) } }

    ContentChrome("Settings", onBack) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Account", "Subtitles", "Player", "Runtimes", "Categories").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            if (tab == 4) {
                CategoryCustomizationPanel(session, container, settings, ::save, Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    when (tab) {
                        0 -> {
                            item { SettingsSection("Account", listOf("Server: ${session.serverUrl}", "Username: ${session.username}", "Status: ${session.status}", "Expiry: ${xtreamExpiryToText(session.expDate)}")) }
                            item { SettingsSection("Data", listOf("Cache is local", "Favorites and history are stored per account", "No IPTV links or playlists are bundled with this app")) }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { scope.launch { container.libraryRepository.clearCache() } }, modifier = Modifier.fillMaxWidth()) { Text("Clear cache") }
                                    Button(onClick = { scope.launch { container.libraryRepository.clearFavorites(session.accountKey) } }, modifier = Modifier.fillMaxWidth()) { Text("Clear favorites") }
                                    Button(onClick = { scope.launch { container.libraryRepository.clearHistory(session.accountKey) } }, modifier = Modifier.fillMaxWidth()) { Text("Clear history") }
                                    Button(onClick = { scope.launch { container.authRepository.logout(); onLogout() } }, modifier = Modifier.fillMaxWidth()) { Text("Logout / Clear saved account") }
                                }
                            }
                            item { Text("RedPlus IPTV v1.2.1 • Legal player only. No content, playlists, or IPTV sources are included.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                        1 -> {
                            item {
                                SettingsPanel("Subtitles") {
                                    SettingsCheckboxRow("Enable subtitles", settings.subtitlesEnabled) { save(settings.copy(subtitlesEnabled = it)) }
                                    SettingsDropdown("Subtitle language", settings.preferredSubtitleLanguage, listOf("auto", "off", "en", "ar", "fr", "es", "de", "tr")) { save(settings.copy(preferredSubtitleLanguage = it, subtitlesEnabled = it != "off")) }
                                    OutlinedTextField(
                                        value = settings.externalSubtitleUrl,
                                        onValueChange = { save(settings.copy(externalSubtitleUrl = it)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("External subtitle URL (.srt/.vtt/.ass)") }
                                    )
                                    Text("Player Options also lets the user switch embedded subtitle tracks while watching.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        2 -> {
                            item {
                                SettingsPanel("Playback") {
                                    SettingsCheckboxRow("Always start the whole app horizontally", settings.forceLandscapeApp) { save(settings.copy(forceLandscapeApp = it)) }
                                    SettingsCheckboxRow("TV view menus for channels and VOD", settings.tvViewMode) { save(settings.copy(tvViewMode = it)) }
                                    SettingsCheckboxRow("Use external Live TV player", settings.useExternalLivePlayer) { save(settings.copy(useExternalLivePlayer = it)) }
                                    SettingsCheckboxRow("Use external VOD/Series player", settings.useExternalVodPlayer) { save(settings.copy(useExternalVodPlayer = it)) }
                                    SettingsCheckboxRow("Show time-skip arrows beside play/pause", settings.showTimeSkipButtons) { save(settings.copy(showTimeSkipButtons = it)) }
                                    SettingsNumberField("Skip forward seconds", settings.forwardSkipSeconds) { save(settings.copy(forwardSkipSeconds = it)) }
                                    SettingsNumberField("Rewind seconds", settings.rewindSkipSeconds) { save(settings.copy(rewindSkipSeconds = it)) }
                                    Text("Skip arrows appear in the middle of the player beside the play/pause button. Values are clamped between 1 and 600 seconds.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                                    Text("When external player is enabled, Android opens a player chooser for that stream type. Internal playback remains available by turning it off.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        3 -> {
                            item {
                                SettingsPanel("Runtimes") {
                                    SettingsCheckboxRow("Stream URL redirection check", settings.streamUrlRedirectionCheck) { save(settings.copy(streamUrlRedirectionCheck = it)) }
                                    SettingsCheckboxRow("Live TV EPG", settings.liveTvEpg) { save(settings.copy(liveTvEpg = it)) }
                                    SettingsCheckboxRow("Use extension in stream URL", settings.useExtensionInStreamUrl) { save(settings.copy(useExtensionInStreamUrl = it)) }
                                    SettingsCheckboxRow("Use m3u8 format in Live TV stream URL", settings.useM3uFormatInStreamUrl) { save(settings.copy(useM3uFormatInStreamUrl = it)) }
                                    SettingsCheckboxRow("Use runtime function in Movies section", settings.useRuntimeMovies) { save(settings.copy(useRuntimeMovies = it)) }
                                    SettingsCheckboxRow("Use runtime function in Series section", settings.useRuntimeSeries) { save(settings.copy(useRuntimeSeries = it)) }
                                    SettingsCheckboxRow("Use UTF-8 decode / text repair", settings.useUtf8Decode) { save(settings.copy(useUtf8Decode = it)) }
                                    SettingsDropdown("Content loading strategy", settings.contentLoadingStrategy.name, ContentLoadingStrategy.entries.map { it.name }) { save(settings.copy(contentLoadingStrategy = ContentLoadingStrategy.valueOf(it))) }
                                    OutlinedTextField(
                                        value = settings.externalXmlTvUrl,
                                        onValueChange = { save(settings.copy(externalXmlTvUrl = it)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("Optional XMLTV EPG URL") }
                                    )
                                    Text("For schedule data, the app first uses the provider Xtream EPG. If you enter a legal XMLTV feed URL, it tries to match channel names/EPG IDs and show that timeline.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
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
private fun TvContentList(
    categories: List<Pair<String, String>>,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    rows: List<ContentItem>,
    row: @Composable (ContentItem) -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPanel(Modifier.width(132.dp).fillMaxSize()) {
            CategorySidebar(categories, selectedCategory, onCategory, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBox(query, onQuery, Modifier.fillMaxWidth(), placeholder)
            Text("TV view • ${rows.size} item(s)", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(rows, key = { it.type.name + it.id }) { item -> row(item) }
            }
        }
    }
}

@Composable
private fun TvCatalogView(
    categories: List<Pair<String, String>>,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    title: String,
    sort: String,
    onSort: (String) -> Unit,
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPanel(Modifier.width(132.dp).fillMaxSize()) {
            CategorySidebar(categories, selectedCategory, onCategory, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchBox(query, onQuery, Modifier.weight(1f), "Search $title")
                SortButton(sort, onSort)
            }
            Text("TV view • ${items.size} item(s)", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
            PosterGrid(items, onClick = onItemClick, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryCustomizationPanel(session: Session, container: AppContainer, settings: AppSettings, save: (AppSettings) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val state by rememberLoadState(session.accountKey, "category-customization") {
        listOf(
            CategorySection("live", "Live TV", container.contentRepository.liveCategories(session)),
            CategorySection("vod", "Movies / VOD", container.contentRepository.vodCategories(session)),
            CategorySection("series", "Series / Shows", container.contentRepository.seriesCategories(session))
        )
    }

    when (state) {
        LoadState.Loading -> LoadingState("Loading categories...")
        is LoadState.Error -> ErrorState((state as LoadState.Error).message, (state as LoadState.Error).detail)
        is LoadState.Data -> {
            val sections = (state as LoadState.Data<List<CategorySection>>).value
            LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    SettingsPanel("Category customization") {
                        SettingsCheckboxRow("Use TV-style see-through menus", settings.tvViewMode) { save(settings.copy(tvViewMode = it)) }
                        SearchBox(query, { query = it }, Modifier.fillMaxWidth(), "Search categories")
                        Text("Only visible rows are rendered, so this page stays stable even with very large provider category lists.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Hide categories you do not want to see, or type the same group name on several categories to merge them into one menu chip.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                sections.forEach { section ->
                    val rows = section.categories.filter { query.isBlank() || it.name.containsNormalized(query) }
                    item(key = section.kind + "_header") {
                        SettingsPanel("${section.title} categories") {
                            Text("${rows.size} shown from ${section.categories.size} total", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val keys = rows.map { categoryKey(section.kind, it.id) }.toSet()
                                    save(settings.copy(hiddenCategoryKeys = settings.hiddenCategoryKeys + keys))
                                }, modifier = Modifier.weight(1f)) { Text("Hide shown") }
                                Button(onClick = {
                                    val keys = rows.map { categoryKey(section.kind, it.id) }.toSet()
                                    save(settings.copy(hiddenCategoryKeys = settings.hiddenCategoryKeys - keys))
                                }, modifier = Modifier.weight(1f)) { Text("Show shown") }
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = section.kind + "_empty") { Text("No ${section.title} categories matched.", color = PremiumMuted) }
                    } else {
                        items(rows, key = { section.kind + ":" + it.id }) { category ->
                            CategoryCustomizationRow(section.kind, category, settings, save)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCustomizationRow(kind: String, category: CategoryItem, settings: AppSettings, save: (AppSettings) -> Unit) {
    val key = categoryKey(kind, category.id)
    val hidden = key in settings.hiddenCategoryKeys
    val groupName = settings.categoryGroupMap[key].orEmpty()
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hidden,
                    onCheckedChange = { checked ->
                        val nextHidden = if (checked) settings.hiddenCategoryKeys + key else settings.hiddenCategoryKeys - key
                        save(settings.copy(hiddenCategoryKeys = nextHidden))
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text(repairText(category.name, settings.useUtf8Decode), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(if (hidden) "Hidden" else "Visible", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = groupName,
                onValueChange = { value ->
                    val clean = value.trim()
                    val nextMap = if (clean.isBlank()) settings.categoryGroupMap - key else settings.categoryGroupMap + (key to clean)
                    save(settings.copy(categoryGroupMap = nextMap))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Group name, optional") }
            )
        }
    }
}

private data class CategorySection(val kind: String, val title: String, val categories: List<CategoryItem>)
private data class ManagedCategoryOption(val id: String, val name: String, val realIds: Set<String>)

private fun categoryKey(kind: String, id: String): String = "$kind:$id"
private fun groupId(kind: String, name: String): String = "group::$kind::$name"

private fun managedCategories(kind: String, raw: List<CategoryItem>, settings: AppSettings, includeAll: Boolean): List<ManagedCategoryOption> {
    val visible = raw.filter { categoryKey(kind, it.id) !in settings.hiddenCategoryKeys }
    val grouped = visible.groupBy { settings.categoryGroupMap[categoryKey(kind, it.id)]?.trim()?.takeIf { name -> name.isNotBlank() } }
    val result = mutableListOf<ManagedCategoryOption>()
    if (includeAll) result += ManagedCategoryOption("all", "All", visible.map { it.id }.toSet())
    grouped[null].orEmpty().forEach { category -> result += ManagedCategoryOption(category.id, category.name, setOf(category.id)) }
    grouped.filterKeys { it != null }.entries.sortedBy { it.key.orEmpty() }.forEach { entry ->
        val groupName = entry.key ?: return@forEach
        result += ManagedCategoryOption(groupId(kind, groupName), "Group: $groupName", entry.value.map { it.id }.toSet())
    }
    return result
}

private fun realCategoryIds(kind: String, raw: List<CategoryItem>, settings: AppSettings, selected: String): Set<String> = managedCategories(kind, raw, settings, includeAll = true).firstOrNull { it.id == selected }?.realIds.orEmpty()

private fun ContentItem.visibleForCategorySettings(kind: String, settings: AppSettings): Boolean {
    val category = categoryId ?: return true
    return categoryKey(kind, category) !in settings.hiddenCategoryKeys
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
private fun SettingsPanel(title: String, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingsCheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsNumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 600)?.let(onChange)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
private fun SettingsDropdown(label: String, value: String, values: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(values.firstOrNull { it == value } ?: value) }
        DropdownMenu(open, { open = false }) {
            values.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); open = false })
            }
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

private fun ContentItem.repairText(enabled: Boolean): ContentItem = if (!enabled) this else copy(
    title = repairText(title, true),
    genre = genre?.let { repairText(it, true) },
    plot = plot?.let { repairText(it, true) },
    year = year?.let { repairText(it, true) },
    rating = rating?.let { repairText(it, true) }
)

private fun repairText(value: String, enabled: Boolean): String {
    if (!enabled || value.isBlank()) return value
    val repaired = runCatching { String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrDefault(value)
    val originalBadness = value.count { it == 'Ã' || it == 'Ø' || it == 'Ù' || it == '�' }
    val repairedBadness = repaired.count { it == 'Ã' || it == 'Ø' || it == 'Ù' || it == '�' }
    return if (repaired.isNotBlank() && repairedBadness < originalBadness) repaired else value
}
