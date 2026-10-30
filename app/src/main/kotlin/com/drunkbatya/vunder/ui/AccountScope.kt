package com.drunkbatya.vunder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@Composable
fun AccountScope(store: ViewModelStore, content: @Composable () -> Unit) {
    val activity = checkNotNull(LocalViewModelStoreOwner.current)
    val owner = remember(store, activity) { AccountViewModelStoreOwner(store, activity as HasDefaultViewModelProviderFactory) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class AccountViewModelStoreOwner(
    override val viewModelStore: ViewModelStore,
    activity: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory by activity
