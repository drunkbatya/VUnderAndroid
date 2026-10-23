package com.drunkbatya.vunder.core.di

import com.drunkbatya.vunder.core.network.ChallengeHandler
import com.drunkbatya.vunder.ui.challenge.ChallengeCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ChallengeModule {
    @Binds
    fun challengeHandler(coordinator: ChallengeCoordinator): ChallengeHandler
}
