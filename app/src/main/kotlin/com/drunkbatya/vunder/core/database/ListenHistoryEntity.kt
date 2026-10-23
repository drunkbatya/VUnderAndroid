package com.drunkbatya.vunder.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "listen_history",
    primaryKeys = ["trackOwnerId", "trackId"],
    indices = [Index("listenedAt")],
)
data class ListenHistoryEntity(
    val trackOwnerId: Long,
    val trackId: Long,
    val listenedAt: Long,
)
