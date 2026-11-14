package com.pocketsamp.data

import androidx.compose.runtime.Immutable
import com.pocketsamp.core.AppConstants

@Immutable
data class ServerProfile(
    val id: String,
    val name: String,
    val folderPath: String,
    val executableName: String = AppConstants.DEFAULT_SAMP_EXECUTABLE,
    val lowPriorityCpu: Boolean = true,
    val autoStartMariaDb: Boolean = false
)

@Immutable
data class FolderInspection(
    val folderExists: Boolean,
    val executableExists: Boolean,
    val serverCfgExists: Boolean,
    val gamemodeFiles: List<String>,
    val pluginFiles: List<String>
)
