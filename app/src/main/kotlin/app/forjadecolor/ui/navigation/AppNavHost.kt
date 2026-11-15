package app.forjadecolor.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.ui.ask.AskScreen
import app.forjadecolor.ui.inventory.InventoryScreen
import app.forjadecolor.ui.project.ProjectScreen
import app.forjadecolor.ui.projects.ProjectsScreen
import app.forjadecolor.ui.settings.SettingsScreen
import app.forjadecolor.ui.technique.TechniqueScreen

/** Routes mirror the PWA hash router: #/projects, #/project/:id, #/ask/:id, #/inventory, #/settings, #/technique/:id. */
object Routes {
    const val PROJECTS = "projects"
    const val PROJECT = "project/{id}"
    const val ASK = "ask/{id}"
    const val INVENTORY = "inventory"
    const val SETTINGS = "settings"
    const val TECHNIQUE = "technique/{id}"

    fun project(id: String) = "project/${Uri.encode(id)}"
    fun ask(id: String) = "ask/${Uri.encode(id)}"
    fun technique(id: TechniqueCardId) = "technique/${id.wire}"

    /** Like the PWA, an unknown card id is not a route: null sends the user back to projects. */
    fun techniqueId(wire: String?): TechniqueCardId? = TechniqueCardId.entries.firstOrNull { it.wire == wire }
}

private enum class Tab(val route: String, @StringRes val label: Int, val tag: String, val glyph: DrawScope.(Color) -> Unit) {
    PROJECTS(Routes.PROJECTS, R.string.nav_projects, "nav_projects", { c ->
        drawRoundRect(c, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
    }),
    INVENTORY(Routes.INVENTORY, R.string.nav_inventory, "nav_inventory", { c -> drawCircle(c) }),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, "nav_settings", { c ->
        drawCircle(c, style = Stroke(2.dp.toPx()))
        drawCircle(c, radius = size.minDimension / 6)
    }),
}

@Composable
fun AppNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    Scaffold(bottomBar = { BottomNav(navController) }) { padding ->
        NavHost(navController, startDestination = Routes.PROJECTS, modifier = Modifier.padding(padding)) {
            composable(Routes.PROJECTS) {
                ProjectsScreen(container, onOpenProject = { navController.navigate(Routes.project(it)) })
            }
            composable(Routes.PROJECT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ProjectScreen(
                    container,
                    projectId = id,
                    onAsk = { navController.navigate(Routes.ask(id)) },
                    onOpenTechnique = { navController.navigate(Routes.technique(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ASK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                AskScreen(
                    container,
                    projectId = id,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.INVENTORY) { InventoryScreen(container) }
            composable(Routes.SETTINGS) { SettingsScreen(container) }
            composable(Routes.TECHNIQUE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val cardId = Routes.techniqueId(entry.arguments?.getString("id"))
                if (cardId == null) {
                    LaunchedEffect(Unit) { navController.navigateToTab(Routes.PROJECTS) }
                } else {
                    TechniqueScreen(container, cardId = cardId, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun BottomNav(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    NavigationBar {
        Tab.entries.forEach { tab ->
            val selected = current == tab.route
            val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navController.navigateToTab(tab.route) },
                icon = { Canvas(Modifier.size(20.dp)) { tab.glyph(this, tint) } },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag(tab.tag),
            )
        }
    }
}

/**
 * Switches to a section's root, like the PWA's hash links: no tab history is saved or restored,
 * so the Projects tab always lands on the list even when a project was open.
 */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}
