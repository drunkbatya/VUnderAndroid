package com.drunkbatya.vunder.ui.update

import androidx.lifecycle.ViewModel
import com.drunkbatya.vunder.core.update.AppRelease
import com.drunkbatya.vunder.core.update.UpdateState
import com.drunkbatya.vunder.core.update.Updater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: Updater) : ViewModel() {
    val state: StateFlow<UpdateState> = updater.state

    init {
        updater.checkOnLaunch()
    }

    fun skip(release: AppRelease) = updater.skip(release)

    fun dismiss() = updater.dismiss()

    fun download(release: AppRelease) = updater.download(release)
}
