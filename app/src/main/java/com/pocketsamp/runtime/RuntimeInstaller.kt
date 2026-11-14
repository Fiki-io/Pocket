package com.pocketsamp.runtime

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import com.pocketsamp.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

sealed interface RuntimeInstallState {
    data object Checking : RuntimeInstallState
    data class Installing(val stageName: String, val extractedCount: Int) : RuntimeInstallState
    data object Ready : RuntimeInstallState
    data class Error(val detail: String) : RuntimeInstallState
}

data class RuntimePaths(
    val qemuBinary: File,
    val mariadbdBinary: File,
    val mariadbCliBinary: File,
    val mariadbDumpBinary: File,
    val sysrootDir: File,
    val shimLibrary: File,
    val mariadbAssetsDir: File
)

class RuntimeInstaller(private val context: Context) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<RuntimeInstallState>(RuntimeInstallState.Checking)
    val state: StateFlow<RuntimeInstallState> = _state.asStateFlow()

    fun getPaths(): RuntimePaths {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val runtimeBase = File(context.filesDir, AppConstants.RUNTIME_DIR)
        val sysrootDir = File(runtimeBase, AppConstants.SYSROOT_DIR_NAME)
        val mariadbAssetsDir = File(runtimeBase, AppConstants.MARIADB_ASSETS_DIR_NAME)
        return RuntimePaths(
            qemuBinary = File(nativeDir, AppConstants.LIB_QEMU_I386),
            mariadbdBinary = File(nativeDir, AppConstants.LIB_MARIADBD),
            mariadbCliBinary = File(nativeDir, AppConstants.LIB_MARIADB_CLI),
            mariadbDumpBinary = File(nativeDir, AppConstants.LIB_MARIADB_DUMP),
            sysrootDir = sysrootDir,
            shimLibrary = File(sysrootDir, "lib/${AppConstants.LIB_ANDROID_SHIM}"),
            mariadbAssetsDir = mariadbAssetsDir
        )
    }

    fun isRuntimeVerified(): Boolean {
        val paths = getPaths()
        val sysrootMarker = File(paths.sysrootDir, AppConstants.SYSROOT_MARKER)
        val mariadbMarker = File(paths.mariadbAssetsDir, AppConstants.MARIADB_ASSETS_MARKER)
        val ldLinux = File(paths.sysrootDir, "lib/ld-linux.so.2")
        val errmsg = File(paths.mariadbAssetsDir, "share/english/errmsg.sys")
        val sysTables = File(paths.mariadbAssetsDir, "init/mysql_system_tables.sql")

        return paths.qemuBinary.isFile &&
            paths.mariadbdBinary.isFile &&
            paths.mariadbCliBinary.isFile &&
            paths.mariadbDumpBinary.isFile &&
            sysrootMarker.isFile &&
            mariadbMarker.isFile &&
            pathExistsWithSymlink(ldLinux) &&
            paths.shimLibrary.isFile &&
            paths.shimLibrary.length() > 0L &&
            errmsg.isFile &&
            errmsg.length() > 0L &&
            sysTables.isFile &&
            sysTables.length() > 0L
    }

    suspend fun ensureInstalled(forceReinstall: Boolean = false): Result<RuntimePaths> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val paths = getPaths()
                    if (!forceReinstall && isRuntimeVerified()) {
                        _state.value = RuntimeInstallState.Ready
                        return@withLock Result.success(paths)
                    }

                    verifyNativeBinaries(paths)

                    val sysrootMarker = File(paths.sysrootDir, AppConstants.SYSROOT_MARKER)
                    if (forceReinstall || !sysrootMarker.isFile || !paths.shimLibrary.isFile) {
                        extractTarXzAsset(
                            assetName = AppConstants.SYSROOT_ARCHIVE_ASSET,
                            destDir = paths.sysrootDir,
                            stageLabel = AppConstants.SYSROOT_DIR_NAME
                        )
                        ensureSysrootEtcFiles(paths.sysrootDir)
                        sysrootMarker.writeText(AppConstants.RUNTIME_VERSION.toString())
                    }

                    val mariadbMarker = File(paths.mariadbAssetsDir, AppConstants.MARIADB_ASSETS_MARKER)
                    val errmsg = File(paths.mariadbAssetsDir, "share/english/errmsg.sys")
                    if (forceReinstall || !mariadbMarker.isFile || !errmsg.isFile) {
                        extractTarXzAsset(
                            assetName = AppConstants.MARIADB_ASSETS_ARCHIVE,
                            destDir = paths.mariadbAssetsDir,
                            stageLabel = AppConstants.MARIADB_ASSETS_DIR_NAME
                        )
                        mariadbMarker.writeText(AppConstants.RUNTIME_VERSION.toString())
                    }

                    if (!isRuntimeVerified()) {
                        val msg = "Verifikasi berkas runtime gagal setelah ekstraksi"
                        _state.value = RuntimeInstallState.Error(msg)
                        return@withLock Result.failure(IllegalStateException(msg))
                    }

                    _state.value = RuntimeInstallState.Ready
                    Result.success(paths)
                } catch (e: Exception) {
                    Log.e(TAG, "Runtime setup failed", e)
                    val msg = e.message ?: e.javaClass.simpleName
                    _state.value = RuntimeInstallState.Error(msg)
                    Result.failure(e)
                }
            }
        }

    private fun verifyNativeBinaries(paths: RuntimePaths) {
        val missing = listOf(
            paths.qemuBinary,
            paths.mariadbdBinary,
            paths.mariadbCliBinary,
            paths.mariadbDumpBinary
        ).filter { !it.isFile || it.length() == 0L }

        if (missing.isNotEmpty()) {
            val abis = android.os.Build.SUPPORTED_ABIS.joinToString(",")
            val nativeDir = paths.qemuBinary.parentFile?.absolutePath.orEmpty()
            throw IllegalStateException(
                "Binary native tidak ditemukan di $nativeDir (ABI perangkat: $abis): " +
                    missing.joinToString { it.name }
            )
        }
    }

    private fun extractTarXzAsset(assetName: String, destDir: File, stageLabel: String) {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        if (!destDir.mkdirs()) {
            throw IllegalStateException("Gagal membuat direktori ${destDir.absolutePath}")
        }

        val destCanonical = destDir.canonicalFile
        val pendingHardLinks = mutableListOf<Pair<File, File>>()
        var count = 0
        _state.value = RuntimeInstallState.Installing(stageLabel, count)

        context.assets.open(assetName).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { buffered ->
                XZInputStream(buffered).use { xzStream ->
                    TarArchiveInputStream(xzStream).use { tarStream ->
                        var entry: TarArchiveEntry? = tarStream.nextEntry
                        val buffer = ByteArray(32 * 1024)
                        while (entry != null) {
                            val cleanName = entry.name.removePrefix("./").trimStart('/')
                            if (cleanName.isNotEmpty() && cleanName != ".") {
                                val target = File(destCanonical, cleanName)
                                val targetCanonical = target.canonicalFile
                                val destPrefix = destCanonical.path + File.separator
                                if (targetCanonical.path != destCanonical.path &&
                                    !targetCanonical.path.startsWith(destPrefix)
                                ) {
                                    throw SecurityException("Path traversal terdeteksi: ${entry.name}")
                                }

                                target.parentFile?.mkdirs()

                                when {
                                    entry.isDirectory -> {
                                        target.mkdirs()
                                    }
                                    entry.isSymbolicLink -> {
                                        unlinkQuietly(target)
                                        Os.symlink(entry.linkName, target.absolutePath)
                                    }
                                    entry.isLink -> {
                                        val linkOrigin = File(
                                            destCanonical,
                                            entry.linkName.removePrefix("./").trimStart('/')
                                        )
                                        pendingHardLinks.add(linkOrigin to target)
                                    }
                                    entry.isFile -> {
                                        unlinkQuietly(target)
                                        FileOutputStream(target).use { out ->
                                            while (true) {
                                                val read = tarStream.read(buffer)
                                                if (read <= 0) break
                                                out.write(buffer, 0, read)
                                            }
                                        }
                                        val mode = (entry.mode and 0x1FF) or 0b110_000_000
                                        runCatching { Os.chmod(target.absolutePath, mode) }
                                    }
                                }

                                count++
                                if (count % 25 == 0) {
                                    _state.value = RuntimeInstallState.Installing(stageLabel, count)
                                }
                            }
                            entry = tarStream.nextEntry
                        }
                    }
                }
            }
        }

        for ((origin, linkFile) in pendingHardLinks) {
            if (origin.exists()) {
                unlinkQuietly(linkFile)
                try {
                    Os.link(origin.absolutePath, linkFile.absolutePath)
                } catch (_: ErrnoException) {
                    origin.copyTo(linkFile, overwrite = true)
                }
            }
        }
        _state.value = RuntimeInstallState.Installing(stageLabel, count)
    }

    private fun ensureSysrootEtcFiles(sysrootDir: File) {
        val etcDir = File(sysrootDir, "etc").apply { mkdirs() }
        val resolv = File(etcDir, "resolv.conf")
        if (!resolv.isFile || resolv.length() == 0L) {
            resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        }
        val hosts = File(etcDir, "hosts")
        if (!hosts.isFile || hosts.length() == 0L) {
            hosts.writeText("127.0.0.1 localhost\n")
        }
        val nsswitch = File(etcDir, "nsswitch.conf")
        if (!nsswitch.isFile || nsswitch.length() == 0L) {
            nsswitch.writeText("hosts: files dns\n")
        }
    }

    private fun unlinkQuietly(file: File) {
        try {
            Os.remove(file.absolutePath)
        } catch (_: ErrnoException) {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun pathExistsWithSymlink(file: File): Boolean {
        return try {
            Os.lstat(file.absolutePath)
            true
        } catch (_: ErrnoException) {
            false
        }
    }

    companion object {
        private const val TAG = "RuntimeInstaller"
    }
}
