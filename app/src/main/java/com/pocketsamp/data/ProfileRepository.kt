package com.pocketsamp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketsamp.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.pocketSampDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConstants.DATASTORE_NAME
)

data class AppPreferencesState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
    val mariaDbBufferPoolMb: Int = AppConstants.DEFAULT_MARIADB_BUFFER_POOL_MB,
    val normalizeMySql8Import: Boolean = true
) {
    val activeProfile: ServerProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
}

class ProfileRepository(private val context: Context) {

    private val profilesKey = stringPreferencesKey(AppConstants.KEY_PROFILES_JSON)
    private val activeProfileIdKey = stringPreferencesKey(AppConstants.KEY_ACTIVE_PROFILE_ID)
    private val bufferPoolKey = intPreferencesKey(AppConstants.KEY_MARIADB_BUFFER_POOL_MB)
    private val normalizeMySql8Key = booleanPreferencesKey(AppConstants.KEY_NORMALIZE_MYSQL8_IMPORT)

    val preferencesFlow: Flow<AppPreferencesState> = context.pocketSampDataStore.data.map { prefs ->
        val profiles = parseProfiles(prefs[profilesKey])
        val activeId = prefs[activeProfileIdKey]
        AppPreferencesState(
            profiles = profiles,
            activeProfileId = activeId,
            mariaDbBufferPoolMb = (prefs[bufferPoolKey] ?: AppConstants.DEFAULT_MARIADB_BUFFER_POOL_MB)
                .coerceIn(8, 128),
            normalizeMySql8Import = prefs[normalizeMySql8Key] ?: true
        )
    }

    suspend fun saveProfile(
        id: String?,
        name: String,
        folderPath: String,
        executableName: String,
        lowPriorityCpu: Boolean,
        autoStartMariaDb: Boolean
    ): ServerProfile {
        val cleanName = name.trim()
        val cleanPath = folderPath.trim().trimEnd('/')
        val cleanExec = executableName.trim().ifEmpty { AppConstants.DEFAULT_SAMP_EXECUTABLE }
        val targetId = id ?: UUID.randomUUID().toString()
        val profile = ServerProfile(
            id = targetId,
            name = cleanName,
            folderPath = cleanPath,
            executableName = cleanExec,
            lowPriorityCpu = lowPriorityCpu,
            autoStartMariaDb = autoStartMariaDb
        )

        context.pocketSampDataStore.edit { prefs ->
            val current = parseProfiles(prefs[profilesKey]).toMutableList()
            val index = current.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                current[index] = profile
            } else {
                current.add(profile)
            }
            prefs[profilesKey] = serializeProfiles(current)
            prefs[activeProfileIdKey] = targetId
        }
        return profile
    }

    suspend fun setActiveProfile(profileId: String) {
        context.pocketSampDataStore.edit { prefs ->
            prefs[activeProfileIdKey] = profileId
        }
    }

    suspend fun updateProfileTuning(
        profileId: String,
        lowPriorityCpu: Boolean? = null,
        autoStartMariaDb: Boolean? = null
    ) {
        context.pocketSampDataStore.edit { prefs ->
            val current = parseProfiles(prefs[profilesKey]).map { profile ->
                if (profile.id == profileId) {
                    profile.copy(
                        lowPriorityCpu = lowPriorityCpu ?: profile.lowPriorityCpu,
                        autoStartMariaDb = autoStartMariaDb ?: profile.autoStartMariaDb
                    )
                } else {
                    profile
                }
            }
            prefs[profilesKey] = serializeProfiles(current)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.pocketSampDataStore.edit { prefs ->
            val remaining = parseProfiles(prefs[profilesKey]).filterNot { it.id == profileId }
            prefs[profilesKey] = serializeProfiles(remaining)
            if (prefs[activeProfileIdKey] == profileId) {
                if (remaining.isNotEmpty()) {
                    prefs[activeProfileIdKey] = remaining.first().id
                } else {
                    prefs.remove(activeProfileIdKey)
                }
            }
        }
    }

    suspend fun setMariaDbBufferPoolMb(mb: Int) {
        context.pocketSampDataStore.edit { prefs ->
            prefs[bufferPoolKey] = mb.coerceIn(8, 128)
        }
    }

    suspend fun setNormalizeMySql8Import(enabled: Boolean) {
        context.pocketSampDataStore.edit { prefs ->
            prefs[normalizeMySql8Key] = enabled
        }
    }

    suspend fun inspectFolder(folderPath: String, executableName: String): FolderInspection =
        withContext(Dispatchers.IO) {
            val dir = File(folderPath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext FolderInspection(
                    folderExists = false,
                    executableExists = false,
                    serverCfgExists = false,
                    gamemodeFiles = emptyList(),
                    pluginFiles = emptyList()
                )
            }
            val execFile = File(dir, executableName)
            val cfgFile = File(dir, AppConstants.SAMP_CFG_FILE)
            val gamemodes = File(dir, "gamemodes")
                .listFiles { f -> f.isFile && f.name.endsWith(".amx", ignoreCase = true) }
                ?.map { it.name.removeSuffix(".amx") }
                ?.sorted()
                ?: emptyList()
            val plugins = File(dir, "plugins")
                .listFiles { f -> f.isFile && f.name.endsWith(".so", ignoreCase = true) }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()

            FolderInspection(
                folderExists = true,
                executableExists = execFile.isFile,
                serverCfgExists = cfgFile.isFile,
                gamemodeFiles = gamemodes,
                pluginFiles = plugins
            )
        }

    private fun parseProfiles(json: String?): List<ServerProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val folderPath = obj.optString("folderPath")
                    if (id.isBlank() || name.isBlank() || folderPath.isBlank()) continue
                    add(
                        ServerProfile(
                            id = id,
                            name = name,
                            folderPath = folderPath,
                            executableName = obj.optString(
                                "executableName",
                                AppConstants.DEFAULT_SAMP_EXECUTABLE
                            ).ifBlank { AppConstants.DEFAULT_SAMP_EXECUTABLE },
                            lowPriorityCpu = obj.optBoolean("lowPriorityCpu", true),
                            autoStartMariaDb = obj.optBoolean("autoStartMariaDb", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeProfiles(profiles: List<ServerProfile>): String {
        val array = JSONArray()
        for (profile in profiles) {
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("folderPath", profile.folderPath)
                    put("executableName", profile.executableName)
                    put("lowPriorityCpu", profile.lowPriorityCpu)
                    put("autoStartMariaDb", profile.autoStartMariaDb)
                }
            )
        }
        return array.toString()
    }
}
