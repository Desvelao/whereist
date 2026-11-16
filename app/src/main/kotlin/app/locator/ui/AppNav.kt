package app.locator.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.locator.LocatorApp

@Composable
fun AppNav() {
    val container = (LocalContext.current.applicationContext as LocatorApp).container
    val repo = container.repository
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm = viewModel<HomeViewModel>(factory = viewModelFactory { initializer { HomeViewModel(repo, container.location) } })
            HomeScreen(
                vm,
                container.photos,
                onOpenItem = { nav.navigate("item/$it") },
                onOpenPlaces = { nav.navigate("places") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val vm = viewModel<DetailViewModel>(
                key = "item-$id",
                factory = viewModelFactory { initializer { DetailViewModel(id, repo, container.location, container.heading) } },
            )
            DetailScreen(vm, container.photos, onBack = { nav.popBackStack() }, onOpenPlace = { nav.navigate("places?id=$it") })
        }
        composable("places?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            val vm = viewModel<PlacesViewModel>(
                key = "places-$id",
                factory = viewModelFactory { initializer { PlacesViewModel(id, repo) } },
            )
            PlacesScreen(
                vm,
                onBack = { nav.popBackStack() },
                onOpenPlace = { nav.navigate("places?id=$it") },
                onOpenItem = { nav.navigate("item/$it") },
            )
        }
        composable("settings") {
            val vm = viewModel<SettingsViewModel>(factory = viewModelFactory { initializer { SettingsViewModel(repo, container.backup, container.quickItem, container::refreshWidget) } })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
