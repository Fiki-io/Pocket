package com.pocketsamp.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsamp.R
import com.pocketsamp.core.AppContainer
import com.pocketsamp.core.ProcessUtils
import com.pocketsamp.data.AppPreferencesState
import com.pocketsamp.data.FolderInspection
import com.pocketsamp.data.ServerProfile
import com.pocketsamp.runtime.RuntimeInstallState
import com.pocketsamp.server.DbRuntimeSnapshot
import com.pocketsamp.server.ServerRuntimeSnapshot
import com.pocketsamp.server.ServiceStatus
import com.pocketsamp.storage.StoragePermissionHelper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ServerUiFeedback(
    val message: String,
    val isError: Boolean
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.getInstance(application)
    private val runtimeInstaller = container.runtimeInstaller
    private val profileRepository = container.profileRepository
    private val serverCfgRepository = container.serverCfgRepository
    private val serverController = container.serverController
    private val databaseController = container.databaseController

    val runtimeState: StateFlow<RuntimeInstallState> = runtimeInstaller.state
    val preferencesState: StateFlow<AppPreferencesState> = profileRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferencesState()
        )
    val serverSnapshot: StateFlow<ServerRuntimeSnapshot> = serverController.snapshot
    val dbSnapshot: StateFlow<DbRuntimeSnapshot> = databaseController.snapshot
    val consoleLogs: StateFlow<List<String>> = serverController.logs

    private val _hasStoragePermission = MutableStateFlow(
        StoragePermissionHelper.hasAllFilesAccess(application)
    )
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(
        StoragePermissionHelper.hasNotificationPermission(application)
    )
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _folderInspection = MutableStateFlow<FolderInspection?>(null)
    val folderInspection: StateFlow<FolderInspection?> = _folderInspection.asStateFlow()

    private val _localIpv4s = MutableStateFlow<List<String>>(emptyList())
    val localIpv4s: StateFlow<List<String>> = _localIpv4s.asStateFlow()

    private val _isWaitingForAutoDb = MutableStateFlow(false)
    val isWaitingForAutoDb: StateFlow<Boolean> = _isWaitingForAutoDb.asStateFlow()

    private val _feedback = MutableStateFlow<ServerUiFeedback?>(null)
    val feedback: StateFlow<ServerUiFeedback?> = _feedback.asStateFlow()

    private var autoDbStartJob: Job? = null

    init {
        viewModelScope.launch {
            runtimeInstaller.ensureInstalled()
        }
        viewModelScope.launch {
            preferencesState.collectLatest { prefs ->
                refreshFolderInspection(prefs.activeProfile)
            }
        }
        refreshEnvironmentState()
    }

    fun bindServices() {
        serverController.bind()
        databaseController.bind()
    }

    fun unbindServices() {
        serverController.unbind()
        databaseController.unbind()
    }

    fun refreshEnvironmentState() {
        val app = getApplication<Application>()
        _hasStoragePermission.value = StoragePermissionHelper.hasAllFilesAccess(app)
        _hasNotificationPermission.value = StoragePermissionHelper.hasNotificationPermission(app)
        viewModelScope.launch {
            val ips = withContext(Dispatchers.IO) {
                ProcessUtils.getLocalIpv4Addresses()
            }
            _localIpv4s.value = ips
            refreshFolderInspection(preferencesState.value.activeProfile)
        }
    }

    fun retryRuntimeInstall() {
        viewModelScope.launch {
            runtimeInstaller.ensureInstalled(forceReinstall = true)
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.setActiveProfile(profileId)
        }
    }

    fun saveProfile(
        id: String?,
        name: String,
        folderPath: String,
        executableName: String,
        lowPriorityCpu: Boolean,
        autoStartMariaDb: Boolean
    ) {
        viewModelScope.launch {
            val saved = profileRepository.saveProfile(
                id = id,
                name = name,
                folderPath = folderPath,
                executableName = executableName,
                lowPriorityCpu = lowPriorityCpu,
                autoStartMariaDb = autoStartMariaDb
            )
            refreshFolderInspection(saved)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }

    fun startServer() {
        val app = getApplication<Application>()
        val profile = preferencesState.value.activeProfile
        if (profile == null) {
            _feedback.value = ServerUiFeedback(
                message = app.getString(R.string.server_start_missing_profile),
                isError = true
            )
            return
        }
        if (!_hasStoragePermission.value) {
            _feedback.value = ServerUiFeedback(
                message = app.getString(R.string.permission_storage_denied),
                isError = true
            )
            return
        }

        autoDbStartJob?.cancel()
        autoDbStartJob = viewModelScope.launch {
            val runtimeRes = runtimeInstaller.ensureInstalled()
            if (runtimeRes.isFailure) {
                _feedback.value = ServerUiFeedback(
                    message = runtimeRes.exceptionOrNull()?.message
                        ?: app.getString(R.string.runtime_error_title),
                    isError = true
                )
                return@launch
            }

            val inspection = profileRepository.inspectFolder(
                profile.folderPath,
                profile.executableName
            )
            _folderInspection.value = inspection
            if (!inspection.folderExists || !inspection.executableExists) {
                _feedback.value = ServerUiFeedback(
                    message = app.getString(
                        R.string.profile_missing_executable,
                        profile.executableName
                    ),
                    isError = true
                )
                return@launch
            }

            if (profile.autoStartMariaDb && dbSnapshot.value.status != ServiceStatus.RUNNING) {
                _isWaitingForAutoDb.value = true
                val waitResult = async(start = CoroutineStart.UNDISPATCHED) {
                    dbSnapshot
                        .drop(1)
                        .first { snap ->
                            snap.status == ServiceStatus.RUNNING ||
                                snap.status == ServiceStatus.ERROR
                        }
                }
                databaseController.startMariaDb(preferencesState.value.mariaDbBufferPoolMb)
                val finalDbState = waitResult.await()
                _isWaitingForAutoDb.value = false
                if (finalDbState.status != ServiceStatus.RUNNING) {
                    _feedback.value = ServerUiFeedback(
                        message = finalDbState.errorDetail.ifBlank {
                            app.getString(R.string.db_status_error)
                        },
                        isError = true
                    )
                    return@launch
                }
            }

            _feedback.value = null
            serverController.startServer(profile)
        }
    }

    fun stopServer() {
        autoDbStartJob?.cancel()
        _isWaitingForAutoDb.value = false
        serverController.stopServer()
    }

    fun sendRconCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        val app = getApplication<Application>()
        val profile = preferencesState.value.activeProfile
        if (profile == null) {
            _feedback.value = ServerUiFeedback(
                message = app.getString(R.string.server_start_missing_profile),
                isError = true
            )
            return
        }
        viewModelScope.launch {
            val cfg = serverCfgRepository.load(profile.folderPath).getOrNull()
            if (cfg != null && cfg.rconPassword.isBlank()) {
                _feedback.value = ServerUiFeedback(
                    message = app.getString(R.string.server_rcon_missing_password),
                    isError = true
                )
                return@launch
            }
            serverController.sendRcon(clean)
            _feedback.value = ServerUiFeedback(
                message = app.getString(R.string.server_rcon_sent, clean),
                isError = false
            )
        }
    }

    fun clearConsoleLogs() {
        serverController.clearLogs()
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    private suspend fun refreshFolderInspection(profile: ServerProfile?) {
        if (profile == null) {
            _folderInspection.value = null
            return
        }
        _folderInspection.value = profileRepository.inspectFolder(
            profile.folderPath,
            profile.executableName
        )
    }
}
