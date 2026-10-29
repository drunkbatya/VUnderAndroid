package com.drunkbatya.vunder.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
fun <T : Any> StackNavDisplay(
    backStack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entryProvider: (T) -> NavEntry<T>,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { push(FastOutSlowInEasing) },
        popTransitionSpec = { pop(FastOutSlowInEasing) },
        predictivePopTransitionSpec = { pop(LinearEasing) },
        entryProvider = entryProvider,
    )
}

private const val TRANSITION_MILLIS = 300

private fun push(easing: Easing): ContentTransform =
    slideInHorizontally(tween(TRANSITION_MILLIS, easing = easing)) { it } togetherWith
        slideOutHorizontally(tween(TRANSITION_MILLIS, easing = easing)) { -it / 4 }

private fun pop(easing: Easing): ContentTransform =
    (slideInHorizontally(tween(TRANSITION_MILLIS, easing = easing)) { -it / 4 } togetherWith
        slideOutHorizontally(tween(TRANSITION_MILLIS, easing = easing)) { it })
        .apply { targetContentZIndex = -1f }
