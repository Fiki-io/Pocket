package com.pocketsamp.ui.screen

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.core.ProcessUtils
import com.pocketsamp.data.AppPreferencesState
import com.pocketsamp.data.FolderInspection
import com.pocketsamp.data.ServerProfile
import com.pocketsamp.runtime.RuntimeInstallState
import com.pocketsamp.server.DbRuntimeSnapshot
import com.pocketsamp.server.ServerRuntimeSnapshot
import com.pocketsamp.server.ServiceStatus
import com.pocketsamp.storage.StoragePermissionHelper
import com.pocketsamp.ui.components.PocketCard
import com.pocketsamp.ui.components.PocketDialogContainer
import com.pocketsamp.ui.components.PocketFeedbackBanner
import com.pocketsamp.ui.components.PocketPrimaryButton
import com.pocketsamp.ui.components.PocketScreenBackground
import com.pocketsamp.ui.components.PocketSecondaryButton
import com.pocketsamp.ui.components.PocketTextField
import com.pocketsamp.ui.components.ProfileEditorDialog
import com.pocketsamp.ui.components.ProfileSelectBottomSheet
import com.pocketsamp.ui.components.ServiceStatusBadge
import com.pocketsamp.ui.theme.ConsoleTextStyle
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii
import com.pocketsamp.viewmodel.ServerUiFeedback

@Composable
fun ServerScreen(
    runtimeState: RuntimeInstallState,
    preferencesState: AppPreferencesState,
    serverSnapshot: ServerRuntimeSnapshot,
    dbSnapshot: DbRuntimeSnapshot,
    consoleLogs: List<String>,
    hasStoragePermission: Boolean,
    hasNotificationPermission: Boolean,
    folderInspection: FolderInspection?,
    localIpv4s: List<String>,
    isWaitingForAutoDb: Boolean,
    feedback: ServerUiFeedback?,
    onRetryRuntime: () -> Unit,
    onRefreshEnvironment: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (String?, String, String, String, Boolean, Boolean) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onSendRcon: (String) -> Unit,
    onClearLogs: () -> Unit,
    onDismissFeedback: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showProfileSheet by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var storageDeniedPermanently by remember { mutableStateOf(false) }

    var rconInput by rememberSaveable { mutableStateOf("") }
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    var clipboardMessage by remember { mutableStateOf<String?>(null) }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (!granted) {
            val activity = context.findActivity()
            val showRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            storageDeniedPermanently = !showRationale
        } else {
            storageDeniedPermanently = false
        }
        onRefreshEnvironment()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshEnvironment()
        onStartServer()
    }

    if (showProfileSheet) {
        ProfileSelectBottomSheet(
            profiles = preferencesState.profiles,
            activeProfileId = preferencesState.activeProfile?.id,
            onSelectProfile = onSelectProfile,
            onAddProfileClick = {
                showProfileSheet = false
                editingProfile = null
                showProfileEditor = true
            },
            onEditProfileClick = { profile ->
                showProfileSheet = false
                editingProfile = profile
                showProfileEditor = true
            },
            onDeleteProfileClick = { profile ->
                showProfileSheet = false
                deletingProfile = profile
            },
            onDismiss = { showProfileSheet = false }
        )
    }

    if (showProfileEditor) {
        ProfileEditorDialog(
            editingProfile = editingProfile,
            onSave = { id, name, folder, exec, lowPrio, autoDb ->
                onSaveProfile(id, name, folder, exec, lowPrio, autoDb)
                showProfileEditor = false
                editingProfile = null
            },
            onDismiss = {
                showProfileEditor = false
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { target ->
        PocketDialogContainer(
            title = stringResource(R.string.profile_delete_confirm_title, target.name),
            onDismiss = { deletingProfile = null },
            secondaryButtonText = stringResource(R.string.action_cancel),
            onSecondaryClick = { deletingProfile = null },
            primaryButtonText = stringResource(R.string.action_delete),
            onPrimaryClick = {
                onDeleteProfile(target.id)
                deletingProfile = null
            },
            secondaryTestTag = "profile_delete_cancel_button",
            primaryTestTag = "profile_delete_confirm_button"
        ) {
            Text(
                text = stringResource(R.string.profile_delete_confirm_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary
            )
        }
    }

    if (showNotificationRationale) {
        PocketDialogContainer(
            title = stringResource(R.string.permission_notification_rationale_title),
            onDismiss = { showNotificationRationale = false },
            icon = Icons.Outlined.NotificationsNone,
            secondaryButtonText = stringResource(R.string.action_cancel),
            onSecondaryClick = { showNotificationRationale = false },
            primaryButtonText = stringResource(R.string.action_grant_permission),
            onPrimaryClick = {
                showNotificationRationale = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onStartServer()
                }
            },
            secondaryTestTag = "notif_rationale_cancel_button",
            primaryTestTag = "notif_rationale_grant_button"
        ) {
            Text(
                text = stringResource(R.string.permission_notification_rationale_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary
            )
        }
    }

    val activeProfile = preferencesState.activeProfile
    val isServerActive = serverSnapshot.status == ServiceStatus.RUNNING ||
        serverSnapshot.status == ServiceStatus.STARTING ||
        serverSnapshot.status == ServiceStatus.STOPPING ||
        isWaitingForAutoDb

    PocketScreenBackground(modifier = modifier) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = PocketSampDimens.ScreenHorizontalPadding,
                    vertical = PocketSampDimens.Space16
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space16),
                modifier = Modifier
                    .widthIn(max = PocketSampDimens.MaxContentWidth)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PocketSampColors.TextPrimary
                )

                AnimatedVisibility(visible = runtimeState !is RuntimeInstallState.Ready) {
                    RuntimeStatusCard(
                        runtimeState = runtimeState,
                        onRetry = onRetryRuntime
                    )
                }

                AnimatedVisibility(visible = !hasStoragePermission) {
                    StoragePermissionCard(
                        deniedPermanently = storageDeniedPermanently,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = StoragePermissionHelper.createManageAllFilesIntent(context)
                                val fallback = StoragePermissionHelper.createManageAllFilesFallbackIntent()
                                runCatching { context.startActivity(intent) }
                                    .onFailure { runCatching { context.startActivity(fallback) } }
                            } else {
                                legacyStorageLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        },
                        onOpenSettings = {
                            runCatching {
                                context.startActivity(StoragePermissionHelper.createAppDetailsIntent(context))
                            }
                        }
                    )
                }

                val activeBanner = feedback ?: clipboardMessage?.let {
                    ServerUiFeedback(message = it, isError = false)
                }
                if (activeBanner != null) {
                    PocketFeedbackBanner(
                        message = activeBanner.message,
                        isError = activeBanner.isError,
                        onDismiss = {
                            onDismissFeedback()
                            clipboardMessage = null
                        }
                    )
                }

                if (isWaitingForAutoDb) {
                    PocketFeedbackBanner(
                        message = stringResource(R.string.server_waiting_mariadb),
                        isError = false,
                        onDismiss = null
                    )
                }

                ProfileSummaryCard(
                    activeProfile = activeProfile,
                    folderInspection = folderInspection,
                    canSwitch = !isServerActive,
                    onOpenSelector = { showProfileSheet = true },
                    onAddProfile = {
                        editingProfile = null
                        showProfileEditor = true
                    }
                )

                ServerControlCard(
                    serverSnapshot = serverSnapshot,
                    dbSnapshot = dbSnapshot,
                    localIpv4s = localIpv4s,
                    activeProfile = activeProfile,
                    isWaitingForAutoDb = isWaitingForAutoDb,
                    onStartClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasNotificationPermission
                        ) {
                            showNotificationRationale = true
                        } else {
                            onStartServer()
                        }
                    },
                    onStopClick = onStopServer,
                    onNavigateToDatabase = onNavigateToDatabase,
                    onCopyAddress = { addr ->
                        copyText(context, addr)
                        clipboardMessage = context.getString(R.string.copied_to_clipboard, addr)
                    }
                )

                ConsoleCard(
                    logs = consoleLogs,
                    autoScroll = autoScroll,
                    onAutoScrollChange = { autoScroll = it },
                    onClearLogs = onClearLogs,
                    rconInput = rconInput,
                    onRconInputChange = { rconInput = it },
                    isRconEnabled = serverSnapshot.status == ServiceStatus.RUNNING,
                    onSendRcon = {
                        val cmd = rconInput.trim()
                        if (cmd.isNotEmpty()) {
                            onSendRcon(cmd)
                            rconInput = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RuntimeStatusCard(
    runtimeState: RuntimeInstallState,
    onRetry: () -> Unit
) {
    PocketCard {
        when (runtimeState) {
            RuntimeInstallState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)
                ) {
                    CircularProgressIndicator(
                        color = PocketSampColors.AccentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(PocketSampDimens.IconSize)
                    )
                    Text(
                        text = stringResource(R.string.runtime_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextPrimary
                    )
                }
            }

            is RuntimeInstallState.Installing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)
                ) {
                    CircularProgressIndicator(
                        color = PocketSampColors.AccentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(PocketSampDimens.IconSize)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.runtime_preparing),
                            style = MaterialTheme.typography.titleMedium,
                            color = PocketSampColors.TextPrimary
                        )
                        Text(
                            text = stringResource(
                                R.string.runtime_extract_stage,
                                runtimeState.stageName,
                                runtimeState.extractedCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary
                        )
                    }
                }
            }

            is RuntimeInstallState.Error -> {
                Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = PocketSampColors.StatusWarning
                        )
                        Text(
                            text = stringResource(R.string.runtime_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = PocketSampColors.TextPrimary
                        )
                    }
                    Text(
                        text = runtimeState.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextSecondary
                    )
                    PocketPrimaryButton(
                        text = stringResource(R.string.runtime_retry),
                        onClick = onRetry,
                        icon = Icons.Outlined.Refresh,
                        testTag = "runtime_retry_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            RuntimeInstallState.Ready -> Unit
        }
    }
}

@Composable
private fun StoragePermissionCard(
    deniedPermanently: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = PocketSampColors.StatusWarning
                )
                Text(
                    text = stringResource(R.string.permission_storage_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = PocketSampColors.TextPrimary
                )
            }
            Text(
                text = if (deniedPermanently) {
                    stringResource(R.string.permission_storage_denied)
                } else {
                    stringResource(R.string.permission_storage_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary
            )
            PocketPrimaryButton(
                text = if (deniedPermanently) {
                    stringResource(R.string.action_open_settings)
                } else {
                    stringResource(R.string.action_grant_permission)
                },
                onClick = if (deniedPermanently) onOpenSettings else onRequestPermission,
                icon = Icons.Outlined.Settings,
                testTag = "storage_permission_button",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    activeProfile: ServerProfile?,
    folderInspection: FolderInspection?,
    canSwitch: Boolean,
    onOpenSelector: () -> Unit,
    onAddProfile: () -> Unit
) {
    PocketCard(
        onClick = if (activeProfile != null && canSwitch) onOpenSelector else null
    ) {
        if (activeProfile == null) {
            Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                Text(
                    text = stringResource(R.string.profile_none),
                    style = MaterialTheme.typography.titleMedium,
                    color = PocketSampColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.profile_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketSampColors.TextSecondary
                )
                PocketPrimaryButton(
                    text = stringResource(R.string.profile_add),
                    onClick = onAddProfile,
                    icon = Icons.Outlined.Add,
                    testTag = "profile_empty_add_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_active_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = PocketSampColors.TextSecondary
                        )
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = PocketSampColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onOpenSelector,
                        enabled = canSwitch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = stringResource(R.string.profile_switch_cd),
                            tint = if (canSwitch) {
                                PocketSampColors.TextPrimary
                            } else {
                                PocketSampColors.TextTertiary
                            }
                        )
                    }
                }
                Text(
                    text = activeProfile.folderPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketSampColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (folderInspection != null) {
                    Text(
                        text = stringResource(
                            R.string.profile_folder_stats,
                            folderInspection.gamemodeFiles.size,
                            folderInspection.pluginFiles.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.AccentBorder
                    )
                    if (!folderInspection.executableExists) {
                        Text(
                            text = stringResource(
                                R.string.profile_missing_executable,
                                activeProfile.executableName
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.StatusWarning
                        )
                    } else if (!folderInspection.serverCfgExists) {
                        Text(
                            text = stringResource(R.string.profile_missing_cfg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.StatusWarning
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerControlCard(
    serverSnapshot: ServerRuntimeSnapshot,
    dbSnapshot: DbRuntimeSnapshot,
    localIpv4s: List<String>,
    activeProfile: ServerProfile?,
    isWaitingForAutoDb: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    onCopyAddress: (String) -> Unit
) {
    val context = LocalContext.current
    val statusLabel = when (serverSnapshot.status) {
        ServiceStatus.STOPPED -> stringResource(R.string.server_status_stopped)
        ServiceStatus.STARTING -> stringResource(R.string.server_status_starting)
        ServiceStatus.RUNNING -> stringResource(R.string.server_status_running)
        ServiceStatus.STOPPING -> stringResource(R.string.server_status_stopping)
        ServiceStatus.ERROR -> stringResource(R.string.server_status_error)
    }
    val localEndpoint = "127.0.0.1:${serverSnapshot.port}"

    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                ServiceStatusBadge(
                    status = serverSnapshot.status,
                    label = statusLabel
                )
                Surface(
                    onClick = onNavigateToDatabase,
                    shape = RoundedCornerShape(PocketSampRadii.Pill),
                    color = PocketSampColors.SurfaceInteractive,
                    border = BorderStroke(PocketSampDimens.BorderWidth, PocketSampColors.BorderSubtle),
                    modifier = Modifier.testTag("server_db_pill_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = PocketSampDimens.Space12,
                            vertical = PocketSampDimens.Space4
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = if (dbSnapshot.status == ServiceStatus.RUNNING) {
                                PocketSampColors.StatusActive
                            } else {
                                PocketSampColors.TextSecondary
                            },
                            modifier = Modifier.size(PocketSampDimens.SmallIconSize)
                        )
                        Spacer(modifier = Modifier.width(PocketSampDimens.Space4))
                        Text(
                            text = if (dbSnapshot.status == ServiceStatus.RUNNING) {
                                stringResource(R.string.server_db_pill_running)
                            } else {
                                stringResource(R.string.server_db_pill_stopped)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = PocketSampColors.TextPrimary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space4)) {
                AddressRow(
                    text = stringResource(R.string.server_address_local, localEndpoint),
                    onCopy = { onCopyAddress(localEndpoint) },
                    testTag = "copy_local_address_button"
                )
                localIpv4s.forEachIndexed { idx, ip ->
                    val lanEndpoint = "$ip:${serverSnapshot.port}"
                    AddressRow(
                        text = stringResource(R.string.server_address_lan, lanEndpoint),
                        onCopy = { onCopyAddress(lanEndpoint) },
                        testTag = "copy_lan_address_button_$idx"
                    )
                }
            }

            if (serverSnapshot.status == ServiceStatus.RUNNING || serverSnapshot.pid > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space16),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (serverSnapshot.pid > 0) {
                        Text(
                            text = stringResource(R.string.server_pid_label, serverSnapshot.pid),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.server_ram_label,
                            ProcessUtils.formatMemoryKb(context, serverSnapshot.rssKb)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextSecondary
                    )
                    Text(
                        text = stringResource(
                            R.string.server_uptime_label,
                            ProcessUtils.formatUptime(serverSnapshot.uptimeSec)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextSecondary
                    )
                }
            }

            if (serverSnapshot.status == ServiceStatus.ERROR &&
                serverSnapshot.errorDetail.isNotBlank()
            ) {
                Text(
                    text = serverSnapshot.errorDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketSampColors.StatusWarning
                )
            }

            val runningOrBusy = serverSnapshot.status == ServiceStatus.RUNNING ||
                serverSnapshot.status == ServiceStatus.STARTING ||
                serverSnapshot.status == ServiceStatus.STOPPING ||
                isWaitingForAutoDb

            if (runningOrBusy) {
                PocketSecondaryButton(
                    text = stringResource(R.string.server_stop),
                    onClick = onStopClick,
                    enabled = serverSnapshot.status != ServiceStatus.STOPPING,
                    icon = Icons.Outlined.Stop,
                    testTag = "server_stop_button",
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PocketPrimaryButton(
                    text = stringResource(R.string.server_start),
                    onClick = onStartClick,
                    enabled = activeProfile != null,
                    icon = Icons.Outlined.PlayArrow,
                    testTag = "server_start_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AddressRow(
    text: String,
    onCopy: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = PocketSampColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_copy),
                tint = PocketSampColors.TextSecondary,
                modifier = Modifier.size(PocketSampDimens.SmallIconSize)
            )
        }
    }
}

@Composable
private fun ConsoleCard(
    logs: List<String>,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    rconInput: String,
    onRconInputChange: (String) -> Unit,
    isRconEnabled: Boolean,
    onSendRcon: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.server_console_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = PocketSampColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.server_console_autoscroll),
                    style = MaterialTheme.typography.labelSmall,
                    color = PocketSampColors.TextSecondary
                )
                Spacer(modifier = Modifier.width(PocketSampDimens.Space4))
                Switch(
                    checked = autoScroll,
                    onCheckedChange = onAutoScrollChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PocketSampColors.TextPrimary,
                        checkedTrackColor = PocketSampColors.AccentBlue,
                        uncheckedThumbColor = PocketSampColors.TextSecondary,
                        uncheckedTrackColor = PocketSampColors.SurfaceInteractive
                    ),
                    modifier = Modifier.testTag("console_autoscroll_switch")
                )
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("console_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = PocketSampColors.TextSecondary
                    )
                }
            }

            val boxShape = RoundedCornerShape(PocketSampRadii.Field)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(boxShape)
                    .background(PocketSampColors.ConsoleBackground)
                    .border(PocketSampDimens.BorderWidth, PocketSampColors.BorderSubtle, boxShape)
                    .padding(PocketSampDimens.Space12)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.server_console_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextTertiary,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space4),
                        contentPadding = PaddingValues(bottom = PocketSampDimens.Space8),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(logs, key = { index, _ -> index }) { _, line ->
                            Text(
                                text = line,
                                style = ConsoleTextStyle,
                                color = PocketSampColors.TextPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = PocketSampColors.BorderSubtle)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                modifier = Modifier.fillMaxWidth()
            ) {
                PocketTextField(
                    value = rconInput,
                    onValueChange = onRconInputChange,
                    label = stringResource(R.string.server_rcon_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (isRconEnabled) onSendRcon()
                    }),
                    testTag = "rcon_command_input",
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSendRcon,
                    enabled = isRconEnabled && rconInput.isNotBlank(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rcon_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.server_rcon_send_cd),
                        tint = if (isRconEnabled && rconInput.isNotBlank()) {
                            PocketSampColors.AccentBorder
                        } else {
                            PocketSampColors.TextTertiary
                        }
                    )
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(AppConstants.SAMP_CFG_FILE, text))
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
