package com.drunkbatya.vunder.core.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class PersistentCookieJar @Inject constructor(@ApplicationContext context: Context) : CookieJar {
    private val file = File(context.noBackupFilesDir, "cookies.txt")
    private val cookies: MutableList<Cookie> by lazy { load() }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            this.cookies += cookie
        }
        store()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        if (cookies.removeAll { it.expiresAt < now }) store()
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        file.delete()
        Log.i(TAG, "cookies cleared")
    }

    private fun load(): MutableList<Cookie> {
        if (!file.exists()) return mutableListOf()
        return try {
            file.readLines().mapNotNullTo(mutableListOf()) { line ->
                val fields = line.split('\t', limit = 2)
                if (fields.size != 2) return@mapNotNullTo null
                fields[0].toHttpUrlOrNull()?.let { Cookie.parse(it, fields[1]) }
            }
        } catch (failure: IOException) {
            Log.e(TAG, "read ${file.path} failed: ${failure.message}")
            mutableListOf()
        }
    }

    private fun store() {
        val lines = cookies.filter { it.persistent }.joinToString("\n") { "https://${it.domain}${it.path}\t$it" }
        val partFile = File(file.path + ".part")
        try {
            partFile.writeText(lines)
            if (!partFile.renameTo(file)) throw IOException("rename ${partFile.path} to ${file.path} failed")
        } catch (failure: IOException) {
            Log.e(TAG, "write ${file.path} failed: ${failure.message}")
        }
    }

    private companion object {
        const val TAG = "PersistentCookieJar"
    }
}
