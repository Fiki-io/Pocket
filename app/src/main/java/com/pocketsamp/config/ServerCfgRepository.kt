package com.pocketsamp.config

import androidx.compose.runtime.Immutable
import com.pocketsamp.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class ServerCfgData(
    val hostname: String = "SA-MP Server",
    val port: String = AppConstants.DEFAULT_SAMP_PORT.toString(),
    val maxPlayers: String = "50",
    val rconPassword: String = "",
    val password: String = "",
    val gamemode0: String = "",
    val plugins: String = "",
    val sleepMs: String = "5",
    val streamRateMs: String = "1000",
    val lanMode: Boolean = false,
    val announce: Boolean = false,
    val rawContent: String = ""
)

class ServerCfgRepository {

    suspend fun load(folderPath: String): Result<ServerCfgData> = withContext(Dispatchers.IO) {
        try {
            val cfgFile = File(folderPath, AppConstants.SAMP_CFG_FILE)
            if (!cfgFile.isFile) {
                return@withContext Result.failure(
                    IllegalStateException("Berkas ${AppConstants.SAMP_CFG_FILE} tidak ditemukan")
                )
            }
            val raw = cfgFile.readText(Charsets.UTF_8)
            Result.success(parseRaw(raw))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveStructured(folderPath: String, data: ServerCfgData): Result<ServerCfgData> =
        withContext(Dispatchers.IO) {
            try {
                val cfgFile = File(folderPath, AppConstants.SAMP_CFG_FILE)
                val existingLines = if (cfgFile.isFile) {
                    cfgFile.readLines(Charsets.UTF_8)
                } else {
                    emptyList()
                }
                val updates = linkedMapOf(
                    "hostname" to data.hostname.trim(),
                    "port" to data.port.trim(),
                    "maxplayers" to data.maxPlayers.trim(),
                    "rcon_password" to data.rconPassword.trim(),
                    "password" to data.password.trim(),
                    "gamemode0" to data.gamemode0.trim(),
                    "plugins" to data.plugins.trim(),
                    "sleep" to data.sleepMs.trim(),
                    "stream_rate" to data.streamRateMs.trim(),
                    "lanmode" to if (data.lanMode) "1" else "0",
                    "announce" to if (data.announce) "1" else "0"
                )

                val handledKeys = mutableSetOf<String>()
                val outputLines = mutableListOf<String>()
                for (line in existingLines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                        outputLines.add(line)
                        continue
                    }
                    val tokenEnd = trimmed.indexOfFirst { it == ' ' || it == '\t' }
                    val key = if (tokenEnd > 0) trimmed.substring(0, tokenEnd).lowercase() else trimmed.lowercase()
                    if (updates.containsKey(key)) {
                        handledKeys.add(key)
                        val newVal = updates.getValue(key)
                        if (key == "password" && newVal.isEmpty()) {
                            continue
                        }
                        if (newVal.isNotEmpty()) {
                            outputLines.add("$key $newVal")
                        }
                    } else {
                        outputLines.add(line)
                    }
                }

                for ((key, value) in updates) {
                    if (key !in handledKeys && value.isNotEmpty()) {
                        if (key == "password" && value.isEmpty()) continue
                        outputLines.add("$key $value")
                    }
                }

                val newRaw = outputLines.joinToString(separator = "\n") + "\n"
                cfgFile.writeText(newRaw, Charsets.UTF_8)
                if (!cfgFile.isFile) {
                    return@withContext Result.failure(IllegalStateException("Gagal memverifikasi berkas tersimpan"))
                }
                Result.success(parseRaw(cfgFile.readText(Charsets.UTF_8)))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveRaw(folderPath: String, rawContent: String): Result<ServerCfgData> =
        withContext(Dispatchers.IO) {
            try {
                val cfgFile = File(folderPath, AppConstants.SAMP_CFG_FILE)
                cfgFile.writeText(rawContent, Charsets.UTF_8)
                val verifiedText = cfgFile.readText(Charsets.UTF_8)
                Result.success(parseRaw(verifiedText))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun parseRaw(raw: String): ServerCfgData {
        val map = mutableMapOf<String, String>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            val sepIndex = trimmed.indexOfFirst { it == ' ' || it == '\t' }
            if (sepIndex <= 0) continue
            val key = trimmed.substring(0, sepIndex).lowercase()
            val value = trimmed.substring(sepIndex + 1).trim()
            map[key] = value
        }
        return ServerCfgData(
            hostname = map["hostname"] ?: "SA-MP Server",
            port = map["port"] ?: AppConstants.DEFAULT_SAMP_PORT.toString(),
            maxPlayers = map["maxplayers"] ?: "50",
            rconPassword = map["rcon_password"] ?: "",
            password = map["password"] ?: "",
            gamemode0 = map["gamemode0"] ?: "",
            plugins = map["plugins"] ?: "",
            sleepMs = map["sleep"] ?: "5",
            streamRateMs = map["stream_rate"] ?: "1000",
            lanMode = map["lanmode"] == "1",
            announce = map["announce"] == "1",
            rawContent = raw
        )
    }
}
