package com.buslive.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.buslive.app.R
import com.buslive.app.domain.model.Place
import com.buslive.app.ui.favorites.FavoritesScreen
import com.buslive.app.ui.guidance.GuidanceScreen
import com.buslive.app.ui.line.LineScreen
import com.buslive.app.ui.map.MapCommand
import com.buslive.app.ui.map.MapNav
import com.buslive.app.ui.map.MapScreen
import com.buslive.app.ui.nearby.NearbyScreen
import com.buslive.app.ui.planner.PlannerScreen
import com.buslive.app.ui.search.SearchScreen
import com.buslive.app.ui.settings.SettingsScreen
import com.buslive.app.ui.stop.StopActions
import com.buslive.app.ui.stop.StopScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object MapDest
@Serializable object NearbyDest
@Serializable data class PlannerDest(val toLat: Double? = null, val toLon: Double? = null, val toName: String? = null)
@Serializable object FavoritesDest
@Serializable object SearchDest
@Serializable data class LineDest(val lineRef: Long)
@Serializable data class StopDest(val code: Int)
@Serializable object GuidanceDest
@Serializable object SettingsDest

private data class Tab(
    val route: Any,
    val klass: KClass<*>,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab(MapDest, MapDest::class, R.string.tab_map, Icons.Outlined.Map, Icons.Filled.Map),
    Tab(NearbyDest, NearbyDest::class, R.string.tab_nearby, Icons.Outlined.NearMe, Icons.Filled.NearMe),
    Tab(PlannerDest(), PlannerDest::class, R.string.tab_plan, Icons.Outlined.Directions, Icons.Filled.Directions),
    Tab(FavoritesDest, FavoritesDest::class, R.string.tab_favorites, Icons.Outlined.StarBorder, Icons.Filled.Star),
)

@Composable
fun AppRoot(openGuidanceRequest: Int, rootVm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = tabs.any { t -> destination?.hierarchy?.any { it.hasRoute(t.klass) } == true }

    LaunchedEffect(openGuidanceRequest) {
        if (openGuidanceRequest > 0) nav.navigate(GuidanceDest) { launchSingleTop = true }
    }

    // "Directions to X" from anywhere: hand X to the single Plan tab, then open that tab.
    fun planTo(place: Place) {
        rootVm.planTo(place)
        nav.navigateTab(PlannerDest())
    }

    fun goToMap(command: MapCommand?) {
        command?.let(rootVm::sendMapCommand)
        nav.navigate(MapDest) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
    }

    val stopActions = StopActions(
        onLineSchedule = { nav.navigate(LineDest(it)) },
        onShowLineOnMap = { goToMap(MapCommand.ShowLine(it)) },
        onDirections = { s -> planTo(Place(s.name, s.city, s.lat, s.lon)) },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(tab.klass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = MapDest,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<MapDest> {
                MapScreen(
                    MapNav(
                        openSearch = { nav.navigate(SearchDest) },
                        openSettings = { nav.navigate(SettingsDest) },
                        openLine = { nav.navigate(LineDest(it)) },
                        planTo = { planTo(it) },
                        openGuidance = { nav.navigate(GuidanceDest) { launchSingleTop = true } },
                    )
                )
            }
            composable<NearbyDest> {
                NearbyScreen(
                    onStop = { nav.navigate(StopDest(it)) },
                    onLine = { nav.navigate(LineDest(it)) },
                )
            }
            composable<PlannerDest> { back ->
                val args = back.toRoute<PlannerDest>()
                PlannerScreen(
                    initialTo = if (args.toLat != null && args.toLon != null) Place(args.toName.orEmpty(), "", args.toLat, args.toLon) else null,
                    onStartGuidance = { nav.navigate(GuidanceDest) { launchSingleTop = true } },
                )
            }
            composable<FavoritesDest> {
                FavoritesScreen(
                    onStop = { nav.navigate(StopDest(it)) },
                    onLine = { nav.navigate(LineDest(it)) },
                    onPlace = { planTo(it) },
                    onSettings = { nav.navigate(SettingsDest) },
                    onSearch = { nav.navigate(SearchDest) },
                )
            }
            composable<SearchDest> {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onLine = { nav.navigate(LineDest(it)) { popUpTo<SearchDest> { inclusive = true } } },
                    onStop = { nav.navigate(StopDest(it)) { popUpTo<SearchDest> { inclusive = true } } },
                    onPlace = { nav.popBackStack(); planTo(it) },
                    onPlaceOnMap = { p -> nav.popBackStack(); rootVm.sendMapCommand(MapCommand.FocusPoint(p.point)) },
                )
            }
            composable<LineDest> { back ->
                val args = back.toRoute<LineDest>()
                LineScreen(
                    lineRef = args.lineRef,
                    onBack = { nav.popBackStack() },
                    onStop = { nav.navigate(StopDest(it)) },
                    onShowOnMap = { goToMap(MapCommand.ShowLine(it)) },
                    onStartGuidance = { nav.navigate(GuidanceDest) { launchSingleTop = true } },
                )
            }
            composable<StopDest> { back ->
                val args = back.toRoute<StopDest>()
                StopScreen(
                    code = args.code,
                    onBack = { nav.popBackStack() },
                    actions = stopActions.copy(onShowOnMap = { goToMap(MapCommand.FocusStop(args.code)) }),
                )
            }
            composable<GuidanceDest> {
                GuidanceScreen(
                    onBack = { nav.popBackStack() },
                    onReplan = { dest ->
                        rootVm.replanFromHere(dest)
                        nav.popBackStack()
                        nav.navigateTab(PlannerDest())
                    },
                )
            }
            composable<SettingsDest> {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

/** Switch tabs like the bottom bar does: one back stack per tab, Back from a tab returns to the map. */
private fun NavController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
