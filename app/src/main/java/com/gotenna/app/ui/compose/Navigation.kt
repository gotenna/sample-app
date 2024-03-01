package com.gotenna.app.ui.compose

import android.content.Context
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
    viewModel: HomeViewModel,
    onNavigateToVoice: () -> Unit
) {
    composable("detail") {
        DetailScreen(viewModel = viewModel, navigateToVoice = onNavigateToVoice)
    }
}

fun NavGraphBuilder.voiceScreen(viewModel: HomeViewModel, context: Context) {
    composable("voice") {
        VoiceScreen(state = viewModel.voiceState(), viewmodel = viewModel, context = context)
    }
}