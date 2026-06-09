package com.redplus.iptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.player.PlayerScreen
import com.redplus.iptv.ui.theme.RedPlusTheme

@Composable
fun RedPlusApp(container: AppContainer) {
    RedPlusTheme {
        val stored by container.sessionStore.session.collectAsStateWithLifecycle(initialValue = null)
        val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
        val activity = LocalContext.current as? Activity
        LaunchedEffect(settings.forceLandscapeApp) {
            activity?.requestedOrientation = if (settings.forceLandscapeApp) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        var activeSession by remember { mutableStateOf<Session?>(null) }
        LaunchedEffect(stored) { if (stored != null) activeSession = stored }
        val session = activeSession
        PremiumBackground {
            if (session == null) {
                LoginScreen(authRepository = container.authRepository, onLoginSuccess = { activeSession = it })
            } else {
                MainNavigation(container = container, session = session, onLogout = { activeSession = null })
            }
        }
    }
}

@Composable
private fun MainNavigation(container: AppContainer, session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.Dashboard) {
        composable(Routes.Dashboard) { DashboardScreen(session, container, navigate = nav::navigate) }
        composable(Routes.Live) { LiveTvScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Events) { EventsScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Movies) { MoviesScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.MovieDetails) { MovieDetailsScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Series) { SeriesScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.SeriesDetails) { SeriesDetailsScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Favorites) { FavoritesScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.History) { HistoryScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Search) { GlobalSearchScreen(session, container, navigate = nav::navigate, onBack = { nav.popBackStack() }) }
        composable(Routes.Settings) { SettingsScreen(session, container, onLogout = onLogout, onBack = { nav.popBackStack() }) }
        composable(Routes.Epg) { EpgScreen(session, container, onBack = { nav.popBackStack() }) }
        composable(Routes.Player) { PlayerScreen(session = session, container = container, item = SharedSelection.item, onBack = { nav.popBackStack() }) }
    }
}
