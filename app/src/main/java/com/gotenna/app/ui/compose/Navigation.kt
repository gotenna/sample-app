package com.gotenna.app.ui.compose

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.gotenna.app.home.HomeViewModel
import com.gotenna.app.model.RadioListItem


fun NavGraphBuilder.homeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (RadioListItem) -> Unit
) {
    composable("home") {
        HomeScreen(
            state = viewModel.toState(),
            onNavigateToDetail = { onNavigateToDetails(it) }
        )
    }
}

fun NavGraphBuilder.detailScreen(
    viewModel: HomeViewModel
) {
    composable("detail") {
        DetailScreen(viewModel = viewModel)
    }
}