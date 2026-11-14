package com.pocketsamp.db

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
import com.pocketsamp.core.AppConstants
import com.pocketsamp.core.ProcessUtils
import com.pocketsamp.runtime.RuntimeInstaller
import com.pocketsamp.runtime.RuntimePaths
import com.pocketsamp.server.DbRuntimeSnapshot
import com.pocketsamp.server.ServiceStatus
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
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class MariaDbService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val clients = CopyOnWriteArraySet<Messenger>()

    @Volatile
    private var snapshot = DbRuntimeSnapshot()

    @Volatile
    private var activeProcess: Process? = null

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
            AppConstants.ACTION_START_DB -> {
                val bufferPoolMb = intent.getIntExtra(
                    AppConstants.EXTRA_BUFFER_POOL_MB,
                    AppConstants.DEFAULT_MARIADB_BUFFER_POOL_MB
                ).coerceIn(8, 128)
                startForegroundWithStatus(getString(R.string.db_status_starting))
                serviceScope.launch {
                    startMariaDbInternal(bufferPoolMb)
                }
            }

            AppConstants.ACTION_STOP_DB -> {
                serviceScope.launch {
                    stopMariaDbInternal()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startMariaDbInternal(bufferPoolMb: Int) = lifecycleMutex.withLock {
        if (activeProcess?.isAlive == true) {
            return@withLock
        }

        stopRequested.set(false)
        updateSnapshot(DbRuntimeSnapshot(status = ServiceStatus.STARTING, port = AppConstants.MARIADB_PORT))

        val installer = RuntimeInstaller(applicationContext)
        val paths = installer.ensureInstalled().getOrElse { err ->
            updateSnapshot(
                DbRuntimeSnapshot(
                    status = ServiceStatus.ERROR,
                    errorDetail = err.message ?: "Runtime MariaDB gagal disiapkan"
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        val dbHome = File(filesDir, AppConstants.MARIADB_HOME_DIR).apply { mkdirs() }
        val dataDir = File(dbHome, AppConstants.MARIADB_DATA_DIR).apply { mkdirs() }
        val tmpDir = File(dbHome, AppConstants.MARIADB_TMP_DIR).apply { mkdirs() }
        val confFile = File(dbHome, AppConstants.MARIADB_CONF_FILE)
        val socketFile = File(dbHome, AppConstants.MARIADB_SOCKET_FILE)
        val errLogFile = File(dbHome, AppConstants.MARIADB_ERR_LOG_FILE)
        val shareDir = File(paths.mariadbAssetsDir, "share")

        writeMariaDbConfig(
            confFile = confFile,
            dataDir = dataDir,
            tmpDir = tmpDir,
            socketFile = socketFile,
            errLogFile = errLogFile,
            shareDir = shareDir,
            bufferPoolMb = bufferPoolMb
        )

        val mysqlSystemDir = File(dataDir, "mysql")
        val initialized = File(mysqlSystemDir, "global_priv.MAD").isFile ||
            File(mysqlSystemDir, "user.frm").isFile

        if (!initialized) {
            dataDir.listFiles()?.forEach { child -> child.deleteRecursively() }
            if (errLogFile.exists()) {
                errLogFile.delete()
            }
            val bootstrapResult = bootstrapDataDir(paths, confFile, dataDir, shareDir, errLogFile)
            if (bootstrapResult.isFailure) {
                val errMsg = bootstrapResult.exceptionOrNull()?.message ?: "Bootstrap MariaDB gagal"
                updateSnapshot(
                    DbRuntimeSnapshot(
                        status = ServiceStatus.ERROR,
                        errorDetail = errMsg
                    )
                )
                stopSelfCleanly()
                return@withLock
            }
        }

        if (socketFile.exists()) {
            socketFile.delete()
        }

        val process = try {
            val pb = ProcessBuilder(
                paths.mariadbdBinary.absolutePath,
                "--defaults-file=${confFile.absolutePath}"
            ).directory(dbHome).redirectErrorStream(true)

            AndroidOsProcess.setThreadPriority(AndroidOsProcess.THREAD_PRIORITY_BACKGROUND)
            try {
                pb.start()
            } finally {
                AndroidOsProcess.setThreadPriority(AndroidOsProcess.THREAD_PRIORITY_DEFAULT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch mariadbd", e)
            updateSnapshot(
                DbRuntimeSnapshot(
                    status = ServiceStatus.ERROR,
                    errorDetail = e.message ?: "Gagal menjalankan mariadbd"
                )
            )
            stopSelfCleanly()
            return@withLock
        }

        activeProcess = process
        startEpochMs = System.currentTimeMillis()
        val pid = ProcessUtils.extractPid(process)

        val drainJob = serviceScope.launch {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    while (isActive && reader.readLine() != null) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        val ready = waitForMariaDbReady(process, paths)
        if (!ready) {
            val errTail = readErrLogTail(errLogFile)
            val errMsg = if (errTail.isNotBlank()) {
                "MariaDB gagal siap: $errTail"
            } else {
                "MariaDB tidak merespons di port ${AppConstants.MARIADB_PORT}"
            }
            if (process.isAlive) process.destroyForcibly()
            activeProcess = null
            drainJob.cancel()
            updateSnapshot(DbRuntimeSnapshot(status = ServiceStatus.ERROR, errorDetail = errMsg))
            stopSelfCleanly()
            return@withLock
        }

        val rss = ProcessUtils.readProcessRssKb(pid)
        updateSnapshot(
            DbRuntimeSnapshot(
                status = ServiceStatus.RUNNING,
                pid = pid,
                rssKb = rss,
                uptimeSec = 0L,
                port = AppConstants.MARIADB_PORT,
                errorDetail = ""
            )
        )
        startForegroundWithStatus(
            getString(R.string.db_status_running, AppConstants.MARIADB_PORT)
        )

        val statsJob = serviceScope.launch {
            while (isActive && process.isAlive) {
                val uptime = ((System.currentTimeMillis() - startEpochMs) / 1000L).coerceAtLeast(0L)
                val currentRss = ProcessUtils.readProcessRssKb(pid)
                updateSnapshot(snapshot.copy(rssKb = currentRss, uptimeSec = uptime))
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
            statsJob.cancel()
            drainJob.cancel()

            if (stopRequested.get()) {
                updateSnapshot(DbRuntimeSnapshot(status = ServiceStatus.STOPPED, port = AppConstants.MARIADB_PORT))
            } else {
                val tail = readErrLogTail(errLogFile)
                val msg = if (tail.isNotBlank()) {
                    "MariaDB berhenti ($exitCode): $tail"
                } else {
                    "MariaDB berhenti tak terduga (kode keluar $exitCode)"
                }
                updateSnapshot(
                    DbRuntimeSnapshot(
                        status = ServiceStatus.ERROR,
                        port = AppConstants.MARIADB_PORT,
                        errorDetail = msg
                    )
                )
            }
            stopSelfCleanly()
        }

        monitorJobs = listOf(drainJob, statsJob, waitJob)
    }

    private fun bootstrapDataDir(
        paths: RuntimePaths,
        confFile: File,
        dataDir: File,
        shareDir: File,
        errLogFile: File
    ): Result<Unit> {
        return try {
            val sysTablesFile = File(paths.mariadbAssetsDir, "init/mysql_system_tables.sql")
            val sysDataFile = File(paths.mariadbAssetsDir, "init/mysql_system_tables_data.sql")
            if (!sysTablesFile.isFile || !sysDataFile.isFile) {
                return Result.failure(IllegalStateException("SQL bootstrap MariaDB tidak ditemukan"))
            }

            val proc = ProcessBuilder(
                paths.mariadbdBinary.absolutePath,
                "--defaults-file=${confFile.absolutePath}",
                "--bootstrap",
                "--datadir=${dataDir.absolutePath}",
                "--lc-messages-dir=${shareDir.absolutePath}"
            ).redirectErrorStream(true).start()

            proc.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write("CREATE DATABASE IF NOT EXISTS mysql;\n")
                writer.write("USE mysql;\n")
                writer.write(sysTablesFile.readText(StandardCharsets.UTF_8))
                writer.write("\n")
                writer.write(sysDataFile.readText(StandardCharsets.UTF_8))
                writer.write("\n")
            }

            val output = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            val code = proc.waitFor()
            if (code != 0) {
                val tail = readErrLogTail(errLogFile).ifBlank { output.takeLast(300) }
                return Result.failure(IllegalStateException("Bootstrap gagal ($code): $tail"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun waitForMariaDbReady(process: Process, paths: RuntimePaths): Boolean {
        val deadline = System.currentTimeMillis() + AppConstants.DB_READY_TIMEOUT_MS
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            val tcpReady = try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(AppConstants.MARIADB_HOST, AppConstants.MARIADB_PORT),
                        300
                    )
                    true
                }
            } catch (_: Exception) {
                false
            }
            if (tcpReady && verifyCliSelect1(paths)) {
                return true
            }
            delay(250L)
        }
        return false
    }

    private fun verifyCliSelect1(paths: RuntimePaths): Boolean {
        return try {
            val proc = ProcessBuilder(
                paths.mariadbCliBinary.absolutePath,
                "-h", AppConstants.MARIADB_HOST,
                "-P", AppConstants.MARIADB_PORT.toString(),
                "-u", AppConstants.MARIADB_USER,
                "-N", "-B",
                "-e", "SELECT 1;"
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor() == 0 && out == "1"
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun stopMariaDbInternal() = lifecycleMutex.withLock {
        val process = activeProcess
        if (process == null || !process.isAlive) {
            stopRequested.set(true)
            updateSnapshot(DbRuntimeSnapshot(status = ServiceStatus.STOPPED, port = AppConstants.MARIADB_PORT))
            stopSelfCleanly()
            return@withLock
        }

        stopRequested.set(true)
        updateSnapshot(snapshot.copy(status = ServiceStatus.STOPPING))

        val paths = RuntimeInstaller(applicationContext).getPaths()
        if (paths.mariadbCliBinary.isFile) {
            runCatching {
                val shutdownProc = ProcessBuilder(
                    paths.mariadbCliBinary.absolutePath,
                    "-h", AppConstants.MARIADB_HOST,
                    "-P", AppConstants.MARIADB_PORT.toString(),
                    "-u", AppConstants.MARIADB_USER,
                    "-e", "SHUTDOWN;"
                ).start()
                shutdownProc.waitFor()
            }
        }

        val deadline = System.currentTimeMillis() + AppConstants.STOP_GRACE_TIMEOUT_MS
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            delay(150L)
        }
        if (process.isAlive) {
            process.destroy()
            delay(600L)
        }
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private fun writeMariaDbConfig(
        confFile: File,
        dataDir: File,
        tmpDir: File,
        socketFile: File,
        errLogFile: File,
        shareDir: File,
        bufferPoolMb: Int
    ) {
        val content = buildString {
            appendLine("[mysqld]")
            appendLine("datadir=${dataDir.absolutePath}")
            appendLine("tmpdir=${tmpDir.absolutePath}")
            appendLine("socket=${socketFile.absolutePath}")
            appendLine("port=${AppConstants.MARIADB_PORT}")
            appendLine("bind-address=${AppConstants.MARIADB_HOST}")
            appendLine("lc-messages-dir=${shareDir.absolutePath}")
            appendLine("character-set-server=utf8mb4")
            appendLine("collation-server=utf8mb4_unicode_ci")
            appendLine("innodb_buffer_pool_size=${bufferPoolMb}M")
            appendLine("innodb_log_buffer_size=4M")
            appendLine("innodb_flush_method=fsync")
            appendLine("max_connections=${AppConstants.MARIADB_MAX_CONNECTIONS}")
            appendLine("skip-name-resolve")
            appendLine("log-error=${errLogFile.absolutePath}")
        }
        confFile.writeText(content, StandardCharsets.UTF_8)
    }

    private fun readErrLogTail(errLogFile: File): String {
        if (!errLogFile.isFile) return ""
        return try {
            errLogFile.readLines(StandardCharsets.UTF_8)
                .takeLast(3)
                .joinToString(" | ")
                .take(280)
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateSnapshot(newSnapshot: DbRuntimeSnapshot) {
        snapshot = newSnapshot
        for (client in clients) {
            sendSnapshotTo(client, newSnapshot)
        }
    }

    private fun sendSnapshotTo(client: Messenger, snap: DbRuntimeSnapshot) {
        val bundle = Bundle().apply {
            putString(AppConstants.BUNDLE_STATUS, snap.status.name)
            putInt(AppConstants.BUNDLE_PID, snap.pid)
            putLong(AppConstants.BUNDLE_RSS_KB, snap.rssKb)
            putLong(AppConstants.BUNDLE_UPTIME_SEC, snap.uptimeSec)
            putInt(AppConstants.BUNDLE_PORT, snap.port)
            putString(AppConstants.BUNDLE_ERROR, snap.errorDetail)
        }
        val msg = Message.obtain(null, AppConstants.MSG_DB_STATE).apply { data = bundle }
        try {
            client.send(msg)
        } catch (_: Exception) {
            clients.remove(client)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                AppConstants.CHANNEL_DB_ID,
                getString(R.string.notif_channel_db),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_db_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithStatus(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                AppConstants.NOTIF_DB_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AppConstants.NOTIF_DB_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MariaDbService::class.java).apply {
            action = AppConstants.ACTION_STOP_DB
        }
        val stopPending = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConstants.CHANNEL_DB_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_db_title))
            .setContentText(getString(R.string.notif_db_text, AppConstants.MARIADB_PORT, statusText))
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(0, getString(R.string.db_stop), stopPending)
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
        private const val TAG = "MariaDbService"
    }
}
