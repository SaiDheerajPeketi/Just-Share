package com.invincible.jedishare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.TransferViewModel
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val currentRoute = navController.currentDestination?.route
                val excludedRoutes = listOf(Screen.Splash.route, Screen.Onboarding.route, Screen.Permissions.route)
                if (currentRoute != null && currentRoute !in excludedRoutes) {
                    if (!com.invincible.jedishare.ui.screens.hasRequiredPermissions(context)) {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(0)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            val context = androidx.compose.ui.platform.LocalContext.current
            com.invincible.jedishare.ui.screens.SplashScreen(onNavigateNext = { isFirstLaunch -> 
                if (isFirstLaunch) {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                } else if (com.invincible.jedishare.hasAllRequiredPermissions(context)) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            })
        }
        composable(Screen.Onboarding.route) {
            com.invincible.jedishare.ui.screens.OnboardingScreen(onContinue = { 
                navController.navigate(Screen.Permissions.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Permissions.route) {
            com.invincible.jedishare.ui.screens.PermissionsScreen(onContinue = { 
                navController.navigate(Screen.Home.route) { 
                    popUpTo(Screen.Splash.route) { inclusive = true } 
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
            com.invincible.jedishare.ui.screens.HomeScreen(
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
            com.invincible.jedishare.ui.screens.SelectFilesScreen(
                method = method,
                transferViewModel = transferViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToScreen = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.DiscoverBT.route) {
            com.invincible.jedishare.ui.screens.DiscoverDevicesScreen(
                title = "Bluetooth Devices",
                transferMethod = "bt",
                transferViewModel = transferViewModel,
                btViewModel = btViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToScreen = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.DiscoverWifi.route) {
            com.invincible.jedishare.ui.screens.DiscoverDevicesScreen(
                title = "Wi-Fi Direct Devices",
                transferMethod = "wifi",
                transferViewModel = transferViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToScreen = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.TransferProgress.route) {
            com.invincible.jedishare.ui.screens.TransferProgressScreen(
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
        composable(
            route = Screen.History.route,
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            com.invincible.jedishare.ui.screens.HistoryScreen(
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
            com.invincible.jedishare.ui.screens.SettingsScreen(
                onNavigateToNavRoute = { route -> navController.navigate(route) },
                transferViewModel = transferViewModel
            )
        }
        composable(Screen.ScanQr.route) {
            com.invincible.jedishare.ui.screens.ScanQrScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AlterSend.route) {
            com.invincible.jedishare.ui.screens.AlterSendScreen(
                onBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onSelectFiles = { navController.navigate(Screen.SelectFiles.createRoute("altersend")) }
            )
        }
    }
}
