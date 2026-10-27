package com.drunkbatya.vunder.core.update

class ReleaseVersion(private val components: List<Int>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val width = maxOf(components.size, other.components.size)
        for (i in 0 until width) {
            val difference = components.getOrElse(i) { 0 }.compareTo(other.components.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun toString(): String = components.joinToString(".")

    companion object {
        fun parse(text: String): ReleaseVersion? =
            ReleaseVersion(text.trim().split(".").map { it.toIntOrNull()?.takeIf { number -> number >= 0 } ?: return null })
    }
}
