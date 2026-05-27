package org.searchmob.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.searchmob.ui.about.AboutScreen
import org.searchmob.ui.home.HomeScreen
import org.searchmob.ui.search.SearchScreen
import org.searchmob.ui.search.SearchViewModel
import org.searchmob.ui.settings.SettingsScreen
import org.searchmob.ui.settings.SettingsViewModel
import org.searchmob.ui.setup.BrowserSetupScreen

/** Navigation destinations. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val BROWSER_SETUP = "browser_setup"
    const val ABOUT = "about"
}

/**
 * Hosts the Home, Search, and Settings destinations. All ViewModels are built from the shared
 * [factory] so preference/engine/history state is consistent across screens.
 */
@Composable
fun SearchMobNavHost(
    factory: SearchMobViewModelFactory,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(factory = factory)
            SearchScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenBrowserSetup = { navController.navigate(Routes.BROWSER_SETUP) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.BROWSER_SETUP) {
            BrowserSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
