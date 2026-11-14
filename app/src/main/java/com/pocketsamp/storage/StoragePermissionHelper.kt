package com.pocketsamp.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pocketsamp.core.AppConstants
import java.io.File

data class FolderEntry(
    val name: String,
    val path: String,
    val hasServerCfg: Boolean,
    val hasSampBinary: Boolean
)

object StoragePermissionHelper {

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createManageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            createAppDetailsIntent(context)
        }
    }

    fun createManageAllFilesFallbackIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun resolveTreeUriToPosixPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            val parts = docId.split(":", limit = 2)
            val volumeId = parts.firstOrNull().orEmpty()
            val relative = parts.getOrNull(1).orEmpty().trimStart('/')
            val root = when {
                volumeId.equals("primary", ignoreCase = true) ->
                    Environment.getExternalStorageDirectory().absolutePath
                volumeId.isNotEmpty() -> "/storage/$volumeId"
                else -> return null
            }
            val resolved = if (relative.isEmpty()) root else "$root/$relative"
            val dir = File(resolved)
            if (dir.exists() && dir.isDirectory) dir.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    fun getInitialBrowserPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    fun listChildFolders(directoryPath: String): List<FolderEntry> {
        val current = File(directoryPath)
        if (!current.exists() || !current.isDirectory || !current.canRead()) {
            return emptyList()
        }
        val children = current.listFiles() ?: return emptyList()
        return children
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
            .map { folder ->
                val cfgExists = File(folder, AppConstants.SAMP_CFG_FILE).isFile
                val binExists = File(folder, AppConstants.DEFAULT_SAMP_EXECUTABLE).isFile ||
                    File(folder, "omp-server").isFile
                FolderEntry(
                    name = folder.name,
                    path = folder.absolutePath,
                    hasServerCfg = cfgExists,
                    hasSampBinary = binExists
                )
            }
    }
}
