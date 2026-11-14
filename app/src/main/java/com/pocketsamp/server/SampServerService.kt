package com.pocketsamp.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process as AndroidOsProcess
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pocketsamp.MainActivity
import com.pocketsamp.R
import com.pocketsamp.config.ServerCfgRepository
import com.pocketsamp.core.AppConstants
import com.pocketsamp.core.ProcessUtils
import com.pocketsamp.runtime.RuntimeInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class SampServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val clients = CopyOnWriteArraySet<Messenger>()
    private val logBuffer = ArrayDeque<String>(AppConstants.CONSOLE_MAX_LINES)
    private val logLock = Any()

    @Volatile
    private var snapshot = ServerRuntimeSnapshot()

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var activeFolderPath: String = ""

    @Volatile
    private var activeRconPassword: String = ""

    private val stopRequested = AtomicBoolean(false)
    private var monitorJobs: List<Job> = emptyList()
    private var startEpochMs: Long = 0L

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                AppConstants.MSG_REGISTER_CLIENT -> {
                    val replyTo = msg.replyTo ?: return
                    clients.add(replyTo)
                    sendSnapshotTo(replyTo, snapshot)
                    val lines = synchronized(logLock) { logBuffer.toList() }
                    if (lines.isNotEmpty()) {
                        sendLogsTo(replyTo, lines, reset = true)
                    }
                }
                AppConstants.MSG_UNREGISTER_CLIENT -> {
                    msg.replyTo?.let { clients.remove(it) }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(incomingHandler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_START_SERVER -> {
                val profileName = intent.getStringExtra(AppConstants.EXTRA_PROFILE_NAME).orEmpty()
                val folderPath = intent.getStringExtra(AppConstants.EXTRA_FOLDER_PATH).orEmpty()
                val executable = intent.getStringExtra(AppConstants.EXTRA_EXECUTABLE)
                    ?.ifBlank { AppConstants.DEFAULT_SAMP_EXECUTABLE }
                    ?: AppConstants.DEFAULT_SAMP_EXECUTABLE
                val lowPriority = intent.getBooleanExtra(AppConstants.EXTRA_LOW_PRIORITY, true)
                startForegroundWithStatus(
                    profileName.ifBlank { getString(R.string.app_name) },
                    snapshot.port,
                    getString(R.string.server_status_starting)
                )
                serviceScope.launch {
                    startServerInternal(profileName, folderPath, executable, lowPriority)
                }
            }

            AppConstants.ACTION_STOP_SERVER -> {
                serviceScope.launch {
                    stopServerInternal()
                }
            }

            AppConstants.ACTION_SEND_RCON -> {
                val cmd = intent.getStringExtra(AppConstants.EXTRA_RCON_COMMAND).orEmpty()
                serviceScope.launch {
                    sendRconInternal(cmd)
                }
            }

            AppConstants.ACTION_CLEAR_LOGS -> {
                synchronized(logLock) {
                    logBuffer.clear()
                }
                broadcastLogReset()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startServerInternal(
        profileName: String,
        folderPath: String,
        executableName: String,
        lowPriority: Boolean
    ) = lifecycleMutex.withLock {
        if (activeProcess?.isAlive == true) {
            appendLog("[PocketSamp] Server sudah berjalan.")
            return@withLock
        }

        stopRequested.set(false)
        updateSnapshot(
            ServerRuntimeSnapshot(
                status = ServiceStatus.STARTING,
                profileName = profileName
            )
        )

        val installer = RuntimeInstaller(applicationContext)
        val runtimeResult = installer.ensureInstalled()
        val paths = runtimeResult.getOrElse { err ->
            val msg = err.message ?: "Runtime gagal disiapkan"
            appendLog("[PocketSamp] Error runtime: $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val serverDir = File(folderPath)
        val targetExe = File(serverDir, executableName)
        if (!serverDir.isDirectory || !targetExe.isFile) {
            val msg = "Berkas $executableName tidak ditemukan di $folderPath"
            appendLog("[PocketSamp] $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val exeElf = ProcessUtils.inspectElfFile(targetExe)
        if (!exeElf.isValidI386Elf) {
            val msg = "$executableName tidak valid: ${exeElf.summary}"
            appendLog("[PocketSamp] $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val ldLinuxResolved = File(paths.sysrootDir, "lib/ld-linux.so.2").canonicalFile
        val ldElf = ProcessUtils.inspectElfFile(ldLinuxResolved)
        if (!ldElf.isValidI386Elf) {
            val msg = "Interpreter sysroot bermasalah (${ldLinuxResolved.absolutePath}): ${ldElf.summary}"
            appendLog("[PocketSamp] $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val execCacheDir = File(
            File(applicationContext.filesDir, AppConstants.RUNTIME_DIR),
            AppConstants.EXEC_CACHE_DIR
        )
        val preparedExe = try {
            ProcessUtils.prepareExecutableForQemu(targetExe, execCacheDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare executable for QEMU", e)
            val msg = e.message ?: "Gagal menyiapkan izin eksekusi $executableName"
            appendLog("[PocketSamp] $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val cfg = ServerCfgRepository().load(folderPath).getOrNull()
        val port = cfg?.port?.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: AppConstants.DEFAULT_SAMP_PORT
        activeRconPassword = cfg?.rconPassword.orEmpty()
        activeFolderPath = folderPath

        val logFile = File(serverDir, AppConstants.SAMP_SERVER_LOG_FILE)
        val initialLogOffset = if (logFile.isFile) logFile.length() else 0L

        launchQemuLocked(
            profileName = profileName,
            serverDir = serverDir,
            targetExe = targetExe,
            executableName = executableName,
            exeElf = exeElf,
            preparedExe = preparedExe,
            paths = paths,
            port = port,
            logFile = logFile,
            initialLogOffset = initialLogOffset,
            lowPriority = lowPriority,
            reservedVa = AppConstants.QEMU_RESERVED_VA
        )
    }

    private fun launchQemuLocked(
        profileName: String,
        serverDir: File,
        targetExe: File,
        executableName: String,
        exeElf: ProcessUtils.ElfInspection,
        preparedExe: ProcessUtils.PreparedExecutable,
        paths: com.pocketsamp.runtime.RuntimePaths,
        port: Int,
        logFile: File,
        initialLogOffset: Long,
        lowPriority: Boolean,
        reservedVa: String
    ) {
        val command = buildList {
            add(paths.qemuBinary.absolutePath)
            add("-R")
            add(reservedVa)
            add("-L")
            add(paths.sysrootDir.absolutePath)
            if (preparedExe.wasStaged) {
                add("-0")
                add(targetExe.absolutePath)
            }
            add("-E")
            add("POCKETSAMP_SYSROOT=${paths.sysrootDir.absolutePath}")
            add("-E")
            add(
                "LD_LIBRARY_PATH=${paths.sysrootDir.absolutePath}/lib:" +
                    "${paths.sysrootDir.absolutePath}/lib/i386-linux-gnu:" +
                    "${paths.sysrootDir.absolutePath}/usr/lib:" +
                    "${paths.sysrootDir.absolutePath}/usr/lib/i386-linux-gnu:" +
                    "${serverDir.absolutePath}/plugins:${serverDir.absolutePath}"
            )
            add("-E")
            add("LD_PRELOAD=${paths.shimLibrary.absolutePath}")
            add("-E")
            add("HOME=${serverDir.absolutePath}")
            add(preparedExe.execFile.absolutePath)
        }

        appendLog("[PocketSamp] Memulai $executableName (${exeElf.summary}, -R $reservedVa) di port $port...")

        val process = try {
            val pb = ProcessBuilder(command)
                .directory(serverDir)
                .redirectErrorStream(true)
            if (lowPriority) {
                AndroidOsProcess.setThreadPriority(AndroidOsProcess.THREAD_PRIORITY_BACKGROUND)
            }
            try {
                pb.start()
            } finally {
                if (lowPriority) {
                    AndroidOsProcess.setThreadPriority(AndroidOsProcess.THREAD_PRIORITY_DEFAULT)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start qemu-i386", e)
            val msg = e.message ?: "Gagal memulai proses qemu-i386"
            appendLog("[PocketSamp] $msg")
            updateSnapshot(
                snapshot.copy(
                    status = ServiceStatus.ERROR,
                    errorDetail = msg
                )
            )
            stopSelfCleanly()
            return
        }

        activeProcess = process
        startEpochMs = System.currentTimeMillis()
        val pid = ProcessUtils.extractPid(process)
        val initialRss = ProcessUtils.readProcessRssKb(pid)
        val sawGuestBaseError = AtomicBoolean(false)

        updateSnapshot(
            ServerRuntimeSnapshot(
                status = ServiceStatus.RUNNING,
                pid = pid,
                rssKb = initialRss,
                uptimeSec = 0L,
                port = port,
                profileName = profileName,
                errorDetail = ""
            )
        )
        startForegroundWithStatus(
            profileName.ifBlank { getString(R.string.app_name) },
            port,
            getString(R.string.server_status_running)
        )

        val stdoutJob = serviceScope.launch {
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.contains("Unable to find a guest_base", ignoreCase = true)) {
                            sawGuestBaseError.set(true)
                        }
                        if (line.isNotBlank()) {
                            appendLog(line)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val logTailJob = serviceScope.launch {
            tailServerLog(logFile, initialLogOffset)
        }

        val statsJob = serviceScope.launch {
            while (isActive && process.isAlive) {
                val uptime = ((System.currentTimeMillis() - startEpochMs) / 1000L).coerceAtLeast(0L)
                val rss = ProcessUtils.readProcessRssKb(pid)
                updateSnapshot(
                    snapshot.copy(
                        rssKb = rss,
                        uptimeSec = uptime
                    )
                )
                delay(AppConstants.PROCESS_POLL_INTERVAL_MS)
            }
        }

        val waitJob = serviceScope.launch {
            val exitCode = try {
                process.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            activeProcess = null
            logTailJob.cancel()
            statsJob.cancel()
            stdoutJob.join()

            if (!stopRequested.get() &&
                exitCode != 0 &&
                sawGuestBaseError.get() &&
                reservedVa != AppConstants.QEMU_FALLBACK_RESERVED_VA
            ) {
                appendLog(
                    "[PocketSamp] Reservasi ruang alamat $reservedVa ditolak kernel, " +
                        "mencoba mode cadangan (-R ${AppConstants.QEMU_FALLBACK_RESERVED_VA})..."
                )
                lifecycleMutex.withLock {
                    if (!stopRequested.get() && activeProcess == null) {
                        launchQemuLocked(
                            profileName = profileName,
                            serverDir = serverDir,
                            targetExe = targetExe,
                            executableName = executableName,
                            exeElf = exeElf,
                            preparedExe = preparedExe,
                            paths = paths,
                            port = port,
                            logFile = logFile,
                            initialLogOffset = initialLogOffset,
                            lowPriority = lowPriority,
                            reservedVa = AppConstants.QEMU_FALLBACK_RESERVED_VA
                        )
                    }
                }
                return@launch
            }

            if (stopRequested.get()) {
                appendLog("[PocketSamp] Server berhenti (kode keluar $exitCode).")
                updateSnapshot(
                    ServerRuntimeSnapshot(
                        status = ServiceStatus.STOPPED,
                        port = port,
                        profileName = profileName
                    )
                )
            } else {
                val msg = "Proses server berhenti tak terduga (kode keluar $exitCode)"
                appendLog("[PocketSamp] $msg")
                updateSnapshot(
                    ServerRuntimeSnapshot(
                        status = ServiceStatus.ERROR,
                        port = port,
                        profileName = profileName,
                        errorDetail = msg
                    )
                )
            }
            stopSelfCleanly()
        }

        monitorJobs = listOf(stdoutJob, logTailJob, statsJob, waitJob)
    }

    private suspend fun tailServerLog(logFile: File, startOffset: Long) {
        var offset = startOffset
        var carry = ""
        while (activeProcess?.isAlive == true) {
            try {
                if (logFile.isFile) {
                    val len = logFile.length()
                    if (len < offset) {
                        offset = 0L
                        carry = ""
                    }
                    if (len > offset) {
                        RandomAccessFile(logFile, "r").use { raf ->
                            raf.seek(offset)
                            val chunkSize = (len - offset).coerceAtMost(64 * 1024L).toInt()
                            val bytes = ByteArray(chunkSize)
                            val read = raf.read(bytes)
                            if (read > 0) {
                                offset += read
                                val text = carry + String(bytes, 0, read, StandardCharsets.UTF_8)
                                val parts = text.split('\n')
                                carry = if (text.endsWith('\n')) "" else parts.lastOrNull().orEmpty()
                                val completeLines = if (text.endsWith('\n')) parts else parts.dropLast(1)
                                val nonEmpty = completeLines.map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
                                if (nonEmpty.isNotEmpty()) {
                                    appendLogs(nonEmpty)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            delay(AppConstants.LOG_TAIL_INTERVAL_MS)
        }
    }

    private suspend fun stopServerInternal() = lifecycleMutex.withLock {
        val process = activeProcess
        if (process == null || !process.isAlive) {
            stopRequested.set(true)
            updateSnapshot(snapshot.copy(status = ServiceStatus.STOPPED, pid = -1, rssKb = -1L))
            stopSelfCleanly()
            return@withLock
        }

        stopRequested.set(true)
        updateSnapshot(snapshot.copy(status = ServiceStatus.STOPPING))
        appendLog("[PocketSamp] Menghentikan server...")

        if (activeRconPassword.isNotBlank()) {
            RconClient.sendCommand(snapshot.port, activeRconPassword, "exit")
            val deadline = System.currentTimeMillis() + AppConstants.STOP_GRACE_TIMEOUT_MS
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                delay(150L)
            }
        }

        if (process.isAlive) {
            process.destroy()
            val deadline = System.currentTimeMillis() + 1200L
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                delay(100L)
            }
        }
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private suspend fun sendRconInternal(command: String) {
        if (activeProcess?.isAlive != true) {
            appendLog("[PocketSamp] Server tidak sedang berjalan.")
            return
        }
        val latestCfg = if (activeFolderPath.isNotBlank()) {
            ServerCfgRepository().load(activeFolderPath).getOrNull()
        } else {
            null
        }
        val password = latestCfg?.rconPassword?.ifBlank { activeRconPassword } ?: activeRconPassword
        if (password.isBlank()) {
            appendLog("[PocketSamp] rcon_password belum diatur di server.cfg.")
            return
        }
        activeRconPassword = password
        appendLog("> $command")
        val result = RconClient.sendCommand(snapshot.port, password, command)
        result.onSuccess { lines ->
            if (lines.isEmpty()) {
                appendLog("[RCON] Perintah dikirim.")
            } else {
                appendLogs(lines.map { "[RCON] $it" })
            }
        }.onFailure { err ->
            appendLog("[RCON Error] ${err.message ?: err.javaClass.simpleName}")
        }
    }

    private fun appendLog(line: String) {
        appendLogs(listOf(line))
    }

    private fun appendLogs(lines: List<String>) {
        if (lines.isEmpty()) return
        synchronized(logLock) {
            for (line in lines) {
                if (logBuffer.size >= AppConstants.CONSOLE_MAX_LINES) {
                    logBuffer.removeFirst()
                }
                logBuffer.addLast(line)
            }
        }
        for (client in clients) {
            sendLogsTo(client, lines, reset = false)
        }
    }

    private fun updateSnapshot(newSnapshot: ServerRuntimeSnapshot) {
        snapshot = newSnapshot
        for (client in clients) {
            sendSnapshotTo(client, newSnapshot)
        }
    }

    private fun sendSnapshotTo(client: Messenger, snap: ServerRuntimeSnapshot) {
        val bundle = Bundle().apply {
            putString(AppConstants.BUNDLE_STATUS, snap.status.name)
            putInt(AppConstants.BUNDLE_PID, snap.pid)
            putLong(AppConstants.BUNDLE_RSS_KB, snap.rssKb)
            putLong(AppConstants.BUNDLE_UPTIME_SEC, snap.uptimeSec)
            putInt(AppConstants.BUNDLE_PORT, snap.port)
            putString(AppConstants.BUNDLE_PROFILE_NAME, snap.profileName)
            putString(AppConstants.BUNDLE_ERROR, snap.errorDetail)
        }
        val msg = Message.obtain(null, AppConstants.MSG_SERVER_STATE).apply { data = bundle }
        try {
            client.send(msg)
        } catch (_: Exception) {
            clients.remove(client)
        }
    }

    private fun sendLogsTo(client: Messenger, lines: List<String>, reset: Boolean) {
        val bundle = Bundle().apply {
            putStringArrayList(AppConstants.BUNDLE_LOG_LINES, ArrayList(lines))
        }
        val what = if (reset) AppConstants.MSG_SERVER_LOG_RESET else AppConstants.MSG_SERVER_LOG_BATCH
        val msg = Message.obtain(null, what).apply { data = bundle }
        try {
            client.send(msg)
        } catch (_: Exception) {
            clients.remove(client)
        }
    }

    private fun broadcastLogReset() {
        for (client in clients) {
            sendLogsTo(client, emptyList(), reset = true)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                AppConstants.CHANNEL_SERVER_ID,
                getString(R.string.notif_channel_server),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_server_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithStatus(profileName: String, port: Int, statusText: String) {
        val notification = buildNotification(profileName, port, statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                AppConstants.NOTIF_SERVER_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AppConstants.NOTIF_SERVER_ID, notification)
        }
    }

    private fun buildNotification(profileName: String, port: Int, statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SampServerService::class.java).apply {
            action = AppConstants.ACTION_STOP_SERVER
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConstants.CHANNEL_SERVER_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_server_title, profileName))
            .setContentText(getString(R.string.notif_server_text, port, statusText))
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(0, getString(R.string.server_stop), stopPending)
            .build()
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJobs.forEach { it.cancel() }
        activeProcess?.let { proc ->
            if (proc.isAlive) proc.destroyForcibly()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SampServerService"
    }
}
