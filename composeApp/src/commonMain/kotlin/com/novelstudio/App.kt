package com.novelstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List as GalleryList
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import com.novelstudio.core.designsystem.components.StudioIcons
import com.novelstudio.di.SettingsStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.novelstudio.core.designsystem.motion.MD3EMotion
import com.novelstudio.core.designsystem.theme.MD3ETheme
import com.novelstudio.feature.compare.CompareScreen
import com.novelstudio.feature.gallery.GalleryScreen
import com.novelstudio.feature.inpaint.ImageToolsScreen
import com.novelstudio.feature.swipe.SwipeScreen
import com.novelstudio.feature.workbench.WorkbenchScreen
import org.koin.compose.viewmodel.koinViewModel

/** 产品仅保留五个一级目的地；设置与图像工具均是上下文页面。 */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Gallery("gallery", "作品", Icons.AutoMirrored.Rounded.GalleryList),
    Workbench("workbench", "工作台", StudioIcons.Workbench),
    ArtistStrings("artist-strings", "画师串", Icons.Rounded.Edit),
    Prompts("prompts", "Prompt", Icons.Rounded.ThumbUp),
    Tags("tags", "Tag", StudioIcons.Tags),
}

data class NavigationShortcut(val sequence: Long, val destination: Destination)

private const val SETTINGS_ROUTE = "settings"
private const val COMPARE_ROUTE = "gallery/compare"
private const val ORGANIZE_ROUTE = "gallery/organize"
private const val IMAGE_TOOLS_ROUTE = "gallery/tools"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(titleBar: @Composable () -> Unit = {}, navigationShortcut: NavigationShortcut? = null) {
    MD3ETheme(darkTheme = isSystemInDarkTheme()) {
        val settings = koinInject<SettingsStore>()
        var onboardingDone by rememberSaveable { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(Unit) {
            onboardingDone = !settings.readToken().isNullOrBlank()
        }

        when (onboardingDone) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            false -> AnimatedContent(
                targetState = false,
                transitionSpec = { fadeIn(MD3EMotion.StandardEasing) togetherWith fadeOut(MD3EMotion.StandardEasing) },
                label = "onboarding",
            ) {
                OnboardingScreen(onComplete = { onboardingDone = true }, modifier = Modifier.fillMaxSize())
            }
            true -> AnimatedContent(
                targetState = true,
                transitionSpec = { fadeIn(MD3EMotion.StandardEasing) togetherWith fadeOut(MD3EMotion.StandardEasing) },
                label = "main-shell",
            ) {
                MainShell(titleBar = titleBar, navigationShortcut = navigationShortcut)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(titleBar: @Composable () -> Unit, navigationShortcut: NavigationShortcut?) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var imageToolsId by rememberSaveable { mutableStateOf<String?>(null) }
    fun navigate(destination: Destination) {
        navController.navigate(destination.route) { launchSingleTop = true; restoreState = true }
    }
    LaunchedEffect(navigationShortcut) { navigationShortcut?.destination?.let(::navigate) }

    Column(Modifier.fillMaxSize()) {
        titleBar()
        AdaptiveAppShell(
            destinations = Destination.entries,
            currentRoute = currentRoute,
            onSelect = ::navigate,
            onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
            modifier = Modifier.weight(1f),
        ) { contentModifier ->
            NavHost(
                navController = navController,
                startDestination = Destination.Gallery.route,
                modifier = contentModifier,
                enterTransition = {
                    slideIntoContainer(
                        towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1.0f, stiffness = 200f),
                    ) + fadeIn(MD3EMotion.StandardEasing)
                },
                exitTransition = { fadeOut(MD3EMotion.StandardEasing) },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1.0f, stiffness = 200f),
                    ) + fadeIn(MD3EMotion.StandardEasing)
                },
                popExitTransition = { fadeOut(MD3EMotion.StandardEasing) },
            ) {
                composable(Destination.Gallery.route) {
                    GalleryScreen(
                        viewModel = koinViewModel(),
                        onOpenCompare = { navController.navigate(COMPARE_ROUTE) { launchSingleTop = true } },
                        onOpenWorkbench = { navigate(Destination.Workbench) },
                        onOpenOrganize = { navController.navigate(ORGANIZE_ROUTE) { launchSingleTop = true } },
                        onOpenImageTools = { imageId ->
                            imageToolsId = imageId
                            navController.navigate(IMAGE_TOOLS_ROUTE) { launchSingleTop = true }
                        },
                    )
                }
                composable(Destination.Workbench.route) { WorkbenchScreen(viewModel = koinViewModel()) }
                composable(Destination.ArtistStrings.route) { ArtistStringScreen(viewModel = koinViewModel()) }
                composable(Destination.Prompts.route) { PromptAssetScreen(viewModel = koinViewModel()) }
                composable(Destination.Tags.route) { TagLibraryScreen(viewModel = koinViewModel()) }
                composable(SETTINGS_ROUTE) { SettingsScreen() }
                composable(
                    route = COMPARE_ROUTE,
                    enterTransition = { slideInVertically { it } + fadeIn(MD3EMotion.StandardEasing) },
                    exitTransition = { slideOutVertically { it } + fadeOut(MD3EMotion.StandardEasing) },
                ) { CompareScreen(viewModel = koinViewModel()) }
                composable(
                    route = ORGANIZE_ROUTE,
                    enterTransition = { slideInVertically { it } + fadeIn(MD3EMotion.StandardEasing) },
                    exitTransition = { slideOutVertically { it } + fadeOut(MD3EMotion.StandardEasing) },
                ) { SwipeScreen(viewModel = koinViewModel()) }
                composable(
                    route = IMAGE_TOOLS_ROUTE,
                    enterTransition = { slideInVertically { it } + fadeIn(MD3EMotion.StandardEasing) },
                    exitTransition = { slideOutVertically { it } + fadeOut(MD3EMotion.StandardEasing) },
                ) {
                    ImageToolsScreen(
                        imageId = imageToolsId.orEmpty(),
                        viewModel = koinViewModel(),
                        onBack = navController::navigateUp,
                    )
                }
            }
        }
    }
}

/** PC 全局导航快捷键 Ctrl+1…5 与五个一级目的地一一对应。 */
internal fun destinationForShortcut(key: Key): Destination? = when (key) {
    Key.One -> Destination.Gallery
    Key.Two -> Destination.Workbench
    Key.Three -> Destination.ArtistStrings
    Key.Four -> Destination.Prompts
    Key.Five -> Destination.Tags
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveAppShell(
    destinations: List<Destination>,
    currentRoute: String?,
    onSelect: (Destination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val scaffold: @Composable (Modifier) -> Unit = { shellModifier ->
            var menuExpanded by remember { mutableStateOf(false) }
            Scaffold(
                modifier = shellModifier,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(destinations.firstOrNull { it.route == currentRoute }?.label ?: "作品工具") },
                        actions = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "应用菜单")
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("设置") },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                        onClick = { menuExpanded = false; onOpenSettings() },
                                    )
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!expanded) {
                        NavigationBar {
                            destinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { onSelect(destination) },
                                    icon = { Icon(destination.icon, destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> content(Modifier.padding(padding)) }
        }

        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(MD3EMotion.StandardEasing) togetherWith fadeOut(MD3EMotion.StandardEasing)
            },
            label = "adaptive-shell",
        ) { isExpanded ->
            if (isExpanded) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.width(96.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        header = { StudioBrandMark() },
                    ) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { onSelect(destination) },
                                icon = { Icon(destination.icon, destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                    scaffold(Modifier.weight(1f).background(MaterialTheme.colorScheme.background))
                }
            } else {
                scaffold(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StudioBrandMark() {
    Surface(
        modifier = Modifier.padding(vertical = 16.dp).size(44.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(StudioIcons.Brand, contentDescription = "Novel Studio") }
    }
}
