package com.novelstudio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novelstudio.core.designsystem.theme.MD3ETheme
import com.novelstudio.feature.compare.CompareScreen
import com.novelstudio.feature.gallery.GalleryScreen
import com.novelstudio.feature.inpaint.InpaintScreen
import com.novelstudio.feature.swipe.SwipeScreen
import com.novelstudio.feature.workbench.WorkbenchScreen
import org.koin.compose.viewmodel.koinViewModel

/** 五大功能闭环流的路由 */
enum class Destination(val route: String, val label: String, val glyph: String) {
    Workbench("workbench", "工作台", "✦"),
    Gallery("gallery", "图库", "▦"),
    Compare("compare", "对比", "⧉"),
    Swipe("swipe", "筛选", "⇆"),
    Inpaint("inpaint", "重绘", "✎"),
    Settings("settings", "设置", "⚙"),
}

@Composable
fun App() {
    MD3ETheme(darkTheme = isSystemInDarkTheme()) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        val content: @Composable (Modifier) -> Unit = { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Workbench.route,
                modifier = padding,
            ) {
                composable(Destination.Workbench.route) { WorkbenchScreen(viewModel = koinViewModel()) }
                composable(Destination.Gallery.route) { GalleryScreen(viewModel = koinViewModel()) }
                composable(Destination.Compare.route) { CompareScreen(viewModel = koinViewModel()) }
                composable(Destination.Swipe.route) { SwipeScreen(viewModel = koinViewModel()) }
                composable(Destination.Inpaint.route) { InpaintScreen(viewModel = koinViewModel()) }
                composable(Destination.Settings.route) { SettingsScreen() }
            }
        }

        BoxWithAdaptiveNav(
            destinations = Destination.entries,
            currentRoute = currentRoute,
            onSelect = { destination ->
                navController.navigate(destination.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            content = content,
        )
    }
}

/**
 * 自适应断点壳层（MD3E_DESIGN_SPEC.md §1）：
 * Compact（<600dp）→ 底部 NavigationBar；
 * Medium/Expanded（≥600dp）→ 左侧 NavigationRail。
 */
@Composable
private fun BoxWithAdaptiveNav(
    destinations: List<Destination>,
    currentRoute: String?,
    onSelect: (Destination) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { onSelect(destination) },
                            icon = { Text(destination.glyph) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                content(Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { onSelect(destination) },
                                icon = { Text(destination.glyph) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                content(Modifier.padding(padding))
            }
        }
    }
}
