package com.noikonomist.slapme.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noikonomist.slapme.ui.apps.AppPickerScreen
import com.noikonomist.slapme.ui.home.HomeScreen
import com.noikonomist.slapme.ui.schedules.BlockEditorScreen
import com.noikonomist.slapme.ui.schedules.SchedulesScreen
import com.noikonomist.slapme.ui.settings.AppListKind
import com.noikonomist.slapme.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val BLOCK = "schedules/{id}"
    const val SETTINGS = "settings"
    const val APPS = "apps/{kind}"

    fun block(id: Long) = "schedules/$id"
    fun apps(kind: AppListKind) = "apps/${kind.name}"
}

@Composable
fun SlapMeNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSchedules = { nav.navigate(Routes.SCHEDULES) }, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SCHEDULES) {
            SchedulesScreen(onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate(Routes.block(id)) })
        }
        composable(Routes.BLOCK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            BlockEditorScreen(blockId = entry.arguments?.getLong("id") ?: 0, onDone = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onPickApps = { kind -> nav.navigate(Routes.apps(kind)) })
        }
        composable(Routes.APPS) { entry ->
            val kind = entry.arguments?.getString("kind")?.let { name -> AppListKind.entries.firstOrNull { it.name == name } }
                ?: AppListKind.DISTRACTING
            AppPickerScreen(kind = kind, onBack = { nav.popBackStack() })
        }
    }
}
