package com.pocketsamp.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsamp.R
import com.pocketsamp.config.ServerCfgData
import com.pocketsamp.core.AppContainer
import com.pocketsamp.data.FolderInspection
import com.pocketsamp.data.ServerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ConfigUiState(
    val activeProfile: ServerProfile? = null,
    val folderInspection: FolderInspection? = null,
    val cfgData: ServerCfgData = ServerCfgData(),
    val isRawMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val loadError: String? = null,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.getInstance(application)
    private val profileRepository = container.profileRepository
    private val serverCfgRepository = container.serverCfgRepository

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.preferencesFlow
                .distinctUntilChangedBy { it.activeProfile }
                .collectLatest { prefs ->
                    val profile = prefs.activeProfile
                    val prevId = _uiState.value.activeProfile?.id
                    if (profile == null) {
                        _uiState.value = ConfigUiState()
                    } else if (profile.id != prevId || profile.folderPath != _uiState.value.activeProfile?.folderPath) {
                        loadForProfile(profile)
                    } else {
                        _uiState.update { it.copy(activeProfile = profile) }
                    }
                }
        }
    }

    fun reload() {
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            loadForProfile(profile)
        }
    }

    fun setRawMode(rawMode: Boolean) {
        _uiState.update { it.copy(isRawMode = rawMode, feedbackMessage = null) }
    }

    fun updateCfg(transform: (ServerCfgData) -> ServerCfgData) {
        _uiState.update { state ->
            state.copy(
                cfgData = transform(state.cfgData),
                hasUnsavedChanges = true,
                feedbackMessage = null
            )
        }
    }

    fun saveConfig() {
        val app = getApplication<Application>()
        val state = _uiState.value
        val profile = state.activeProfile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, feedbackMessage = null) }
            val result = if (state.isRawMode) {
                serverCfgRepository.saveRaw(profile.folderPath, state.cfgData.rawContent)
            } else {
                serverCfgRepository.saveStructured(profile.folderPath, state.cfgData)
            }
            result.onSuccess { verifiedData ->
                _uiState.update {
                    it.copy(
                        cfgData = verifiedData,
                        isSaving = false,
                        hasUnsavedChanges = false,
                        loadError = null,
                        feedbackMessage = app.getString(R.string.config_saved),
                        feedbackIsError = false
                    )
                }
            }.onFailure { err ->
                val msg = err.message ?: err.javaClass.simpleName
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        feedbackMessage = app.getString(R.string.config_save_failed, msg),
                        feedbackIsError = true
                    )
                }
            }
        }
    }

    fun setLowPriorityCpu(enabled: Boolean) {
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            profileRepository.updateProfileTuning(
                profileId = profile.id,
                lowPriorityCpu = enabled
            )
        }
    }

    fun setAutoStartMariaDb(enabled: Boolean) {
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            profileRepository.updateProfileTuning(
                profileId = profile.id,
                autoStartMariaDb = enabled
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private suspend fun loadForProfile(profile: ServerProfile) {
        _uiState.update {
            it.copy(
                activeProfile = profile,
                isLoading = true,
                loadError = null,
                feedbackMessage = null
            )
        }
        val inspection = profileRepository.inspectFolder(
            profile.folderPath,
            profile.executableName
        )
        val cfgResult = serverCfgRepository.load(profile.folderPath)
        _uiState.update { state ->
            cfgResult.fold(
                onSuccess = { loaded ->
                    state.copy(
                        activeProfile = profile,
                        folderInspection = inspection,
                        cfgData = loaded,
                        isLoading = false,
                        hasUnsavedChanges = false,
                        loadError = null
                    )
                },
                onFailure = { err ->
                    state.copy(
                        activeProfile = profile,
                        folderInspection = inspection,
                        cfgData = ServerCfgData(),
                        isLoading = false,
                        hasUnsavedChanges = false,
                        loadError = err.message ?: err.javaClass.simpleName
                    )
                }
            )
        }
    }
}
