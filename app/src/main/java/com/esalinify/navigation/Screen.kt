package com.esalinify.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Keyboard : Screen("keyboard")
    object Camera : Screen("camera")
}
