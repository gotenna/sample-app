package com.gotenna.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.ui.NavigationUI
import com.gotenna.app.ui.compose.detailScreen
import com.gotenna.app.ui.compose.homeScreen

@Composable
fun MainActivity.MainScreen() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        homeScreen(
            viewModel = viewModel,
            onNavigateToDetails = {
                viewModel.setSelectedRadio(it)
                navController.navigate("detail")
            }
        )
        detailScreen(
            viewModel = viewModel,
            onNavigateToVoice = {
                navController.navigate("voice")
            }
        )
    }

    NavigationUI.setupActionBarWithNavController(this, navController)
    supportActionBar?.hide()
}