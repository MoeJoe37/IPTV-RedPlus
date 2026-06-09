package com.redplus.iptv.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class FileCache(context: Context) {
    private val dir: File = File(context.cacheDir, "api_json_cache").apply { mkdirs() }

    suspend fun get(key: String): CachedJson? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching {
            CachedJson(json = file.readText(Charsets.UTF_8), updatedAt = file.lastModified())
        }.getOrNull()
    }

    suspend fun put(key: String, json: String) = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(json, Charsets.UTF_8)
            temp.delete()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun fileFor(key: String): File = File(dir, "${sha256(key)}.json")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

data class CachedJson(val json: String, val updatedAt: Long)
