package com.pocketsamp.core

import android.content.Context
import android.os.Build
import android.system.Os
import android.text.format.Formatter
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Locale

object ProcessUtils {

    fun extractPid(process: Process): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val pidMethod = runCatching { Process::class.java.getMethod("pid") }.getOrNull()
            if (pidMethod != null) {
                val value = runCatching { (pidMethod.invoke(process) as? Long)?.toInt() }.getOrNull()
                if (value != null && value > 0) return value
            }
        }
        val pidField = runCatching {
            process.javaClass.getDeclaredField("pid").apply { isAccessible = true }
        }.getOrNull()
        if (pidField != null) {
            val value = runCatching { pidField.getInt(process) }.getOrNull()
            if (value != null && value > 0) return value
        }
        val match = Regex("""pid=(\d+)""").find(process.toString())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    fun readProcessRssKb(pid: Int): Long {
        if (pid <= 0) return -1L
        val statusFile = File("/proc/$pid/status")
        if (!statusFile.canRead()) return -1L
        return try {
            statusFile.useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.replace("VmRSS:", "")
                    ?.replace("kB", "")
                    ?.trim()
                    ?.toLongOrNull() ?: -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    fun formatMemoryKb(context: Context, kb: Long): String {
        if (kb < 0L) return "-"
        return Formatter.formatShortFileSize(context, kb * 1024L)
    }

    fun formatUptime(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        val secs = safeSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    fun getLocalIpv4Addresses(): List<String> {
        val result = linkedSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        address.hostAddress?.let { ip ->
                            if (ip.isNotBlank()) result.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return result.toList()
        }
        return result.toList()
    }

    fun isHost32BitArm(): Boolean {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        return primaryAbi.startsWith("armeabi")
    }

    data class ElfInspection(
        val isValidI386Elf: Boolean,
        val summary: String
    )

    data class PreparedExecutable(
        val execFile: File,
        val wasStaged: Boolean
    )

    fun hasExecuteModeBit(file: File): Boolean {
        return try {
            (Os.stat(file.absolutePath).st_mode and 0b001_001_001) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * QEMU 9.1.1 prepare_binprm() rejects any guest executable with -ENOEXEC when
     * !(st.st_mode & 0111). Android /storage/emulated/0 FUSE mounts strip +x bits.
     * Stage ONLY the single target executable into app-private filesDir when needed,
     * keeping the gamemode directory itself uncopied as CWD.
     */
    fun prepareExecutableForQemu(targetExe: File, execCacheDir: File): PreparedExecutable {
        if (hasExecuteModeBit(targetExe)) {
            return PreparedExecutable(execFile = targetExe, wasStaged = false)
        }

        runCatching {
            val currentMode = Os.stat(targetExe.absolutePath).st_mode and 0x1FF
            Os.chmod(targetExe.absolutePath, currentMode or 0b001_001_001)
        }
        if (hasExecuteModeBit(targetExe)) {
            return PreparedExecutable(execFile = targetExe, wasStaged = false)
        }

        if (!execCacheDir.exists() && !execCacheDir.mkdirs()) {
            throw IllegalStateException("Gagal membuat direktori cache eksekusi: ${execCacheDir.absolutePath}")
        }

        val canonicalPath = targetExe.canonicalPath
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPath.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(Locale.US, it) }
        val safeName = targetExe.name.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .ifBlank { AppConstants.DEFAULT_SAMP_EXECUTABLE }
        val cachedExe = File(execCacheDir, "${digest}_$safeName")
        val metaFile = File(execCacheDir, "${digest}_$safeName.meta")

        val expectedLen = targetExe.length()
        val expectedStamp = "$expectedLen:${targetExe.lastModified()}"

        if (cachedExe.isFile &&
            cachedExe.length() == expectedLen &&
            metaFile.isFile &&
            runCatching { metaFile.readText() }.getOrNull() == expectedStamp
        ) {
            runCatching { Os.chmod(cachedExe.absolutePath, 0b111_101_101) }
            if (hasExecuteModeBit(cachedExe)) {
                return PreparedExecutable(execFile = cachedExe, wasStaged = true)
            }
        }

        val tmpFile = File(execCacheDir, "${digest}_$safeName.tmp")
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
        targetExe.inputStream().use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        Os.chmod(tmpFile.absolutePath, 0b111_101_101)
        if (cachedExe.exists()) {
            cachedExe.delete()
        }
        if (!tmpFile.renameTo(cachedExe)) {
            tmpFile.delete()
            throw IllegalStateException("Gagal menyiapkan salinan executable ${targetExe.name}")
        }
        Os.chmod(cachedExe.absolutePath, 0b111_101_101)
        if (!cachedExe.isFile || cachedExe.length() != expectedLen || !hasExecuteModeBit(cachedExe)) {
            throw IllegalStateException("Verifikasi izin eksekusi ${cachedExe.name} gagal")
        }
        metaFile.writeText(expectedStamp)
        return PreparedExecutable(execFile = cachedExe, wasStaged = true)
    }

    fun inspectElfFile(file: File): ElfInspection {
        if (!file.isFile) {
            return ElfInspection(false, "Berkas tidak ditemukan")
        }
        val size = file.length()
        if (size < 52L) {
            return ElfInspection(false, "Ukuran berkas terlalu kecil ($size byte), kemungkinan rusak atau kosong")
        }
        return try {
            val header = ByteArray(64)
            val bytesRead = file.inputStream().use { it.read(header) }
            if (bytesRead < 20) {
                return ElfInspection(false, "Gagal membaca header berkas ($bytesRead byte)")
            }
            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF
            val b2 = header[2].toInt() and 0xFF
            val b3 = header[3].toInt() and 0xFF

            if (b0 == 0x4D && b1 == 0x5A) {
                return ElfInspection(
                    false,
                    "Berkas adalah executable Windows (MZ/PE), bukan binary Linux x86"
                )
            }
            if (b0 == 0x23 && b1 == 0x21) {
                val firstLine = String(header, 0, bytesRead, Charsets.UTF_8)
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                return ElfInspection(
                    false,
                    "Berkas adalah skrip teks ($firstLine), bukan binary ELF Linux x86"
                )
            }
            if (b0 != 0x7F || b1 != 0x45 || b2 != 0x4C || b3 != 0x46) {
                val hex = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3)
                return ElfInspection(
                    false,
                    "Berkas bukan ELF Linux yang valid (header: $hex, ukuran: $size byte)"
                )
            }

            val eiClass = header[4].toInt() and 0xFF
            val eiData = header[5].toInt() and 0xFF
            val eType = (header[16].toInt() and 0xFF) or ((header[17].toInt() and 0xFF) shl 8)
            val eMachine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)

            val archName = when (eMachine) {
                3 -> "x86 (32-bit)"
                62 -> "x86_64 (64-bit)"
                40 -> "ARM (32-bit)"
                183 -> "ARM64 (64-bit)"
                else -> "machine=$eMachine"
            }
            val className = when (eiClass) {
                1 -> "ELF32"
                2 -> "ELF64"
                else -> "class=$eiClass"
            }

            if (eiClass != 1 || eiData != 1 || eMachine != 3 || (eType != 2 && eType != 3)) {
                return ElfInspection(
                    false,
                    "Format binary tidak cocok untuk QEMU i386: $className $archName type=$eType (butuh ELF32 x86 32-bit)"
                )
            }

            ElfInspection(
                true,
                "ELF32 x86 (type=$eType, ukuran=$size byte)"
            )
        } catch (e: Exception) {
            ElfInspection(false, "Gagal memeriksa header berkas: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
