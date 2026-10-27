package com.drunkbatya.vunder.core.di

import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.drunkbatya.vunder.core.network.SessionCookieJar
import com.drunkbatya.vunder.core.network.VkClientIdentity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .cookieJar(SessionCookieJar())
        .build()

    @Provides
    @Singleton
    fun streamingDataSource(httpClient: OkHttpClient): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(httpClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).build())
            .setUserAgent(VkClientIdentity.userAgent(VkClientIdentity.AppVersion.General))
}
