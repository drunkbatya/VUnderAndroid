package com.drunkbatya.vunder

import android.app.Application
import com.drunkbatya.vunder.core.session.SessionLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VUnderApplication : Application() {
    @Inject
    lateinit var sessionLifecycle: SessionLifecycle

    override fun onCreate() {
        super.onCreate()
        sessionLifecycle.start()
    }
}
