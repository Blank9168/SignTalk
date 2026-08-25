package com.example.signtalk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.signtalk.di.AppContainer
import com.example.signtalk.ui.auth.AuthViewModel
import com.example.signtalk.ui.auth.LoginScreen
import com.example.signtalk.ui.auth.RegisterScreen
import com.example.signtalk.ui.dictionary.DictionaryDetailScreen
import com.example.signtalk.ui.dictionary.DictionaryListScreen
import com.example.signtalk.ui.dictionary.DictionaryViewModel
import com.example.signtalk.ui.home.HomeScreen
import com.example.signtalk.ui.recognition.RecognitionScreen
import com.example.signtalk.ui.recognition.RecognitionViewModel
import com.example.signtalk.ui.settings.SettingsScreen
import com.example.signtalk.ui.settings.SettingsViewModel
import com.example.signtalk.ui.splash.SplashScreen

@Composable
fun SignTalkNavGraph(
    appContainer: AppContainer,
    applicationForViewModels: android.app.Application,
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(appContainer.authRepository) } }
    )
    val session by appContainer.authRepository.currentSession.collectAsStateWithLifecycle(initialValue = null)

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(isAuthenticated = session != null) {
                val destination = if (session != null) Screen.Home.route else Screen.Login.route
                navController.navigate(destination) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                displayName = session?.displayName ?: "there",
                onOpenRecognition = { navController.navigate(Screen.Recognition.route) },
                onOpenDictionary = { navController.navigate(Screen.Dictionary.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Recognition.route) {
            val recognitionViewModel: RecognitionViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { RecognitionViewModel(applicationForViewModels, appContainer.settingsRepository) }
                }
            )
            RecognitionScreen(viewModel = recognitionViewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.Dictionary.route) {
            val dictionaryViewModel: DictionaryViewModel = viewModel(
                factory = viewModelFactory { initializer { DictionaryViewModel(appContainer.dictionaryRepository) } }
            )
            DictionaryListScreen(
                viewModel = dictionaryViewModel,
                onBack = { navController.popBackStack() },
                onOpenEntry = { id -> navController.navigate(Screen.DictionaryDetail.route(id)) }
            )
        }

        composable(Screen.DictionaryDetail.route) { backStackEntry ->
            val entryId = backStackEntry.arguments
                ?.getString(Screen.DictionaryDetail.ARG_ENTRY_ID)
                ?.toLongOrNull() ?: 0L
            DictionaryDetailScreen(
                entryId = entryId,
                repository = appContainer.dictionaryRepository,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(appContainer.settingsRepository, appContainer.authRepository) }
                }
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
