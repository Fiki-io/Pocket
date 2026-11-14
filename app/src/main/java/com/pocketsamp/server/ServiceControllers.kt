package com.pocketsamp.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.content.ContextCompat
import com.pocketsamp.core.AppConstants
import com.pocketsamp.data.ServerProfile
import com.pocketsamp.db.MariaDbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerController(context: Context) {

    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow(ServerRuntimeSnapshot())
    val snapshot: StateFlow<ServerRuntimeSnapshot> = _snapshot.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var serviceMessenger: Messenger? = null
    private var bound = false

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            when (msg.what) {
                AppConstants.MSG_SERVER_STATE -> {
                    val statusName = bundle.getString(AppConstants.BUNDLE_STATUS)
                        ?: ServiceStatus.STOPPED.name
                    val status = runCatching { ServiceStatus.valueOf(statusName) }
                        .getOrDefault(ServiceStatus.STOPPED)
                    _snapshot.value = ServerRuntimeSnapshot(
                        status = status,
                        pid = bundle.getInt(AppConstants.BUNDLE_PID, -1),
                        rssKb = bundle.getLong(AppConstants.BUNDLE_RSS_KB, -1L),
                        uptimeSec = bundle.getLong(AppConstants.BUNDLE_UPTIME_SEC, 0L),
                        port = bundle.getInt(AppConstants.BUNDLE_PORT, AppConstants.DEFAULT_SAMP_PORT),
                        profileName = bundle.getString(AppConstants.BUNDLE_PROFILE_NAME).orEmpty(),
                        errorDetail = bundle.getString(AppConstants.BUNDLE_ERROR).orEmpty()
                    )
                }

                AppConstants.MSG_SERVER_LOG_RESET -> {
                    val lines = bundle.getStringArrayList(AppConstants.BUNDLE_LOG_LINES).orEmpty()
                    _logs.value = lines.takeLast(AppConstants.CONSOLE_MAX_LINES)
                }

                AppConstants.MSG_SERVER_LOG_BATCH -> {
                    val lines = bundle.getStringArrayList(AppConstants.BUNDLE_LOG_LINES).orEmpty()
                    if (lines.isNotEmpty()) {
                        val merged = (_logs.value + lines)
                        _logs.value = if (merged.size > AppConstants.CONSOLE_MAX_LINES) {
                            merged.takeLast(AppConstants.CONSOLE_MAX_LINES)
                        } else {
                            merged
                        }
                    }
                }
            }
        }
    }

    private val clientMessenger = Messenger(incomingHandler)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val messenger = Messenger(service)
            serviceMessenger = messenger
            val msg = Message.obtain(null, AppConstants.MSG_REGISTER_CLIENT).apply {
                replyTo = clientMessenger
            }
            runCatching { messenger.send(msg) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
        }
    }

    fun bind() {
        if (bound) return
        val intent = Intent(appContext, SampServerService::class.java)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        serviceMessenger?.let { messenger ->
            val msg = Message.obtain(null, AppConstants.MSG_UNREGISTER_CLIENT).apply {
                replyTo = clientMessenger
            }
            runCatching { messenger.send(msg) }
        }
        runCatching { appContext.unbindService(connection) }
        bound = false
        serviceMessenger = null
    }

    fun startServer(profile: ServerProfile) {
        bind()
        val intent = Intent(appContext, SampServerService::class.java).apply {
            action = AppConstants.ACTION_START_SERVER
            putExtra(AppConstants.EXTRA_PROFILE_ID, profile.id)
            putExtra(AppConstants.EXTRA_PROFILE_NAME, profile.name)
            putExtra(AppConstants.EXTRA_FOLDER_PATH, profile.folderPath)
            putExtra(AppConstants.EXTRA_EXECUTABLE, profile.executableName)
            putExtra(AppConstants.EXTRA_LOW_PRIORITY, profile.lowPriorityCpu)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopServer() {
        val intent = Intent(appContext, SampServerService::class.java).apply {
            action = AppConstants.ACTION_STOP_SERVER
        }
        appContext.startService(intent)
    }

    fun sendRcon(command: String) {
        val intent = Intent(appContext, SampServerService::class.java).apply {
            action = AppConstants.ACTION_SEND_RCON
            putExtra(AppConstants.EXTRA_RCON_COMMAND, command)
        }
        appContext.startService(intent)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        val intent = Intent(appContext, SampServerService::class.java).apply {
            action = AppConstants.ACTION_CLEAR_LOGS
        }
        appContext.startService(intent)
    }
}

class DatabaseController(context: Context) {

    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow(DbRuntimeSnapshot())
    val snapshot: StateFlow<DbRuntimeSnapshot> = _snapshot.asStateFlow()

    private var serviceMessenger: Messenger? = null
    private var bound = false

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == AppConstants.MSG_DB_STATE) {
                val bundle = msg.data
                val statusName = bundle.getString(AppConstants.BUNDLE_STATUS)
                    ?: ServiceStatus.STOPPED.name
                val status = runCatching { ServiceStatus.valueOf(statusName) }
                    .getOrDefault(ServiceStatus.STOPPED)
                _snapshot.value = DbRuntimeSnapshot(
                    status = status,
                    pid = bundle.getInt(AppConstants.BUNDLE_PID, -1),
                    rssKb = bundle.getLong(AppConstants.BUNDLE_RSS_KB, -1L),
                    uptimeSec = bundle.getLong(AppConstants.BUNDLE_UPTIME_SEC, 0L),
                    port = bundle.getInt(AppConstants.BUNDLE_PORT, AppConstants.MARIADB_PORT),
                    errorDetail = bundle.getString(AppConstants.BUNDLE_ERROR).orEmpty()
                )
            }
        }
    }

    private val clientMessenger = Messenger(incomingHandler)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val messenger = Messenger(service)
            serviceMessenger = messenger
            val msg = Message.obtain(null, AppConstants.MSG_REGISTER_CLIENT).apply {
                replyTo = clientMessenger
            }
            runCatching { messenger.send(msg) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
        }
    }

    fun bind() {
        if (bound) return
        val intent = Intent(appContext, MariaDbService::class.java)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        serviceMessenger?.let { messenger ->
            val msg = Message.obtain(null, AppConstants.MSG_UNREGISTER_CLIENT).apply {
                replyTo = clientMessenger
            }
            runCatching { messenger.send(msg) }
        }
        runCatching { appContext.unbindService(connection) }
        bound = false
        serviceMessenger = null
    }

    fun startMariaDb(bufferPoolMb: Int) {
        bind()
        val intent = Intent(appContext, MariaDbService::class.java).apply {
            action = AppConstants.ACTION_START_DB
            putExtra(AppConstants.EXTRA_BUFFER_POOL_MB, bufferPoolMb)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopMariaDb() {
        val intent = Intent(appContext, MariaDbService::class.java).apply {
            action = AppConstants.ACTION_STOP_DB
        }
        appContext.startService(intent)
    }
}
