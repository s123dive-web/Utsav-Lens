package com.utsavlens.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.utsavlens.app.ui.history.HistoryScreen
import com.utsavlens.app.ui.home.HomeScreen
import com.utsavlens.app.ui.options.OptionsScreen
import com.utsavlens.app.ui.progress.ProgressScreen
import com.utsavlens.app.ui.result.ResultScreen
import com.utsavlens.app.ui.settings.CloudSettingsSection
import com.utsavlens.app.ui.settings.LocalAiSettingsSection
import com.utsavlens.app.ui.settings.PromptAssistSection
import com.utsavlens.app.ui.settings.SettingsScreen
import com.utsavlens.app.ui.themes.ThemesScreen
import com.utsavlens.app.ui.video.SlideshowScreen

private fun NavHostController.goHome() {
    navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val stringArg = { name: String, default: String -> navArgument(name) { type = NavType.StringType; defaultValue = default } }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onMediaChosen = { uri, kind -> nav.navigate(Routes.themes(uri, kind)) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.THEMES, arguments = listOf(stringArg(Routes.ARG_SRC, ""), stringArg(Routes.ARG_KIND, Routes.KIND_PHOTO))) {
            ThemesScreen(
                onBack = { nav.popBackStack() },
                onThemeChosen = { src, kind, theme -> nav.navigate(Routes.options(src, kind, theme)) },
            )
        }
        composable(
            Routes.OPTIONS,
            arguments = listOf(stringArg(Routes.ARG_SRC, ""), stringArg(Routes.ARG_KIND, Routes.KIND_PHOTO), stringArg(Routes.ARG_THEME, "")),
        ) {
            OptionsScreen(
                onBack = { nav.popBackStack() },
                onStarted = { jobId -> nav.navigate(Routes.progress(jobId)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PROGRESS, arguments = listOf(stringArg(Routes.ARG_JOB, ""))) {
            ProgressScreen(
                onDone = { jobId -> nav.navigate(Routes.result(jobId)) { popUpTo(Routes.PROGRESS) { inclusive = true } } },
                onNewJob = { jobId -> nav.navigate(Routes.progress(jobId)) { popUpTo(Routes.PROGRESS) { inclusive = true } } },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.RESULT, arguments = listOf(stringArg(Routes.ARG_JOB, ""))) {
            ResultScreen(
                onBack = { nav.popBackStack() },
                onHome = { nav.goHome() },
                onMakeVideo = { batchId -> nav.navigate(Routes.slideshow(batchId)) },
            )
        }
        composable(Routes.SLIDESHOW, arguments = listOf(stringArg(Routes.ARG_BATCH, ""))) {
            SlideshowScreen(onBack = { nav.popBackStack() }, onStarted = { jobId -> nav.navigate(Routes.progress(jobId)) })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { batchId -> nav.navigate(Routes.result(batchId)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, extraSections = { CloudSettingsSection(); PromptAssistSection(); LocalAiSettingsSection() })
        }
    }
}
