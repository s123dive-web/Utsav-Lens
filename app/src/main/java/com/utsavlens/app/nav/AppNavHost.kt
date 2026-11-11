package com.utsavlens.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.utsavlens.app.ui.home.HomeScreen
import com.utsavlens.app.ui.themes.ThemesScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onMediaChosen = { uri, kind -> nav.navigate(Routes.themes(uri, kind)) },
                onHistory = {},
                onSettings = {},
            )
        }
        composable(
            Routes.THEMES,
            arguments = listOf(
                navArgument(Routes.ARG_SRC) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_KIND) { type = NavType.StringType; defaultValue = Routes.KIND_PHOTO },
            ),
        ) {
            ThemesScreen(onBack = { nav.popBackStack() })
        }
    }
}
