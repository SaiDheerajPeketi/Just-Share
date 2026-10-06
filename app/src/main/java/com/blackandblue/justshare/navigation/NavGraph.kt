package com.blackandblue.justshare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.blackandblue.justshare.presentation.BluetoothViewModel
import com.blackandblue.justshare.presentation.TransferViewModel
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.ui.screens.LocalTransferPermissionGate
import com.blackandblue.justshare.ui.screens.NearbyPermissionAccess
import com.blackandblue.justshare.ui.screens.rememberPermissionAccess
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Permissions : Screen("permissions")
    object Home : Screen("home")
    object SelectFiles : Screen("select-files/{method}") {
        fun createRoute(method: String) = "select-files/$method"
    }
    object DiscoverBT : Screen("discover-bt")
    object DiscoverWifi : Screen("discover-wifi")
    object TransferProgress : Screen("transfer-progress")
    object RemoteTransferProgress : Screen("remote-transfer-progress")
    object History : Screen("history")
    object Settings : Screen("settings")
    object ScanQr : Screen("scan-qr")
    object AlterSend : Screen("altersend")
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route,
    transferViewModel: TransferViewModel = hiltViewModel(),
    btViewModel: BluetoothViewModel = hiltViewModel(),
    initialUris: List<Uri> = emptyList(),
    initialMethod: String? = null
) {
    // Initial state is now populated directly in MainActivity before AppNavGraph is composed.

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        sizeTransform = { null }
    ) {
        composable(Screen.Splash.route) {
            com.blackandblue.justshare.ui.screens.SplashScreen(onNavigateNext = { isFirstLaunch -> 
                if (isFirstLaunch) {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            })
        }
        composable(Screen.Onboarding.route) {
            com.blackandblue.justshare.ui.screens.OnboardingScreen(onContinue = { 
                navController.navigate(Screen.Permissions.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Permissions.route) {
            com.blackandblue.justshare.ui.screens.PermissionsScreen(onContinue = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Permissions.route) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(
            route = Screen.Home.route,
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            com.blackandblue.justshare.ui.screens.HomeScreen(
                transferViewModel = transferViewModel,
                onNavigateToNavRoute = { route -> navController.navigate(route) },
                onNavigateToScreen = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.SelectFiles.route,
            arguments = listOf(androidx.navigation.navArgument("method") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val method = backStackEntry.arguments?.getString("method") ?: "bt"
            com.blackandblue.justshare.ui.screens.SelectFilesScreen(
                method = method,
                transferViewModel = transferViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToScreen = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.DiscoverBT.route) {
            LocalDiscoveryDestination(
                method = LocalTransferMethod.BLUETOOTH,
                navController = navController
            ) {
                com.blackandblue.justshare.ui.screens.DiscoverDevicesScreen(
                    title = "Bluetooth Devices",
                    transferMethod = "bt",
                    transferViewModel = transferViewModel,
                    btViewModel = btViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToScreen = { route -> navController.navigate(route) }
                )
            }
        }
        composable(Screen.DiscoverWifi.route) {
            LocalDiscoveryDestination(
                method = LocalTransferMethod.WIFI,
                navController = navController
            ) {
                com.blackandblue.justshare.ui.screens.DiscoverDevicesScreen(
                    title = "Wi-Fi Direct Devices",
                    transferMethod = "wifi",
                    transferViewModel = transferViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToScreen = { route -> navController.navigate(route) }
                )
            }
        }
        composable(Screen.TransferProgress.route) {
            com.blackandblue.justshare.ui.screens.TransferProgressScreen(
                transferViewModel = transferViewModel,
                btViewModel = btViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToScreen = { route ->
                    if (route == Screen.Home.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
        composable(Screen.RemoteTransferProgress.route) {
            com.blackandblue.justshare.ui.screens.RemoteTransferProgressScreen(
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.History.route,
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            com.blackandblue.justshare.ui.screens.HistoryScreen(
                onNavigateToNavRoute = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            com.blackandblue.justshare.ui.screens.SettingsScreen(
                onNavigateToNavRoute = { route -> navController.navigate(route) },
                transferViewModel = transferViewModel
            )
        }
        composable(Screen.ScanQr.route) {
            com.blackandblue.justshare.ui.screens.ScanQrScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AlterSend.route) {
            com.blackandblue.justshare.ui.screens.AlterSendScreen(
                onBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onSelectFiles = { navController.navigate(Screen.SelectFiles.createRoute("altersend")) },
                onNavigateProgress = { navController.navigate(Screen.RemoteTransferProgress.route) }
            )
        }
    }
}

/** A direct share-sheet entry has no previous page to pop back to. */
internal fun NavHostController.leaveLocalPermissionStep() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Screen.Home.route) {
            popUpTo(graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
}

/** Shared by both real local routes; no navigation to a replacement permission
 * destination can discard their pending transfer context. */
@Composable
internal fun LocalDiscoveryDestination(
    method: LocalTransferMethod,
    navController: NavHostController,
    permissionAccess: NearbyPermissionAccess = rememberPermissionAccess(),
    content: @Composable () -> Unit
) {
    LocalTransferPermissionGate(method, { navController.leaveLocalPermissionStep() }, permissionAccess, content)
}
