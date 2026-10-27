package com.drunkbatya.vunder.core.messaging

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.objects
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.json.JsonObject

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Long,
    val name: String,
    val photoUrl: String?,
) {
    companion object {
        fun fromUser(json: JsonObject): Profile? {
            val id = json.long("id") ?: return null
            return Profile(
                id = id,
                name = listOfNotNull(json.string("first_name"), json.string("last_name")).filter { it.isNotEmpty() }.joinToString(" "),
                photoUrl = json.photoUrl(),
            )
        }

        fun fromGroup(json: JsonObject): Profile? {
            val id = json.long("id") ?: return null
            return Profile(id = -id, name = json.string("name").orEmpty(), photoUrl = json.photoUrl())
        }

        fun parseAll(response: JsonObject): List<Profile> =
            response.objects("profiles").mapNotNull(::fromUser) + response.objects("groups").mapNotNull(::fromGroup)

        private fun JsonObject.photoUrl(): String? = string("photo_100") ?: string("photo_50")
    }
}
