package com.pocketsamp.ui.screen

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.core.ProcessUtils
import com.pocketsamp.data.AppPreferencesState
import com.pocketsamp.server.DbRuntimeSnapshot
import com.pocketsamp.server.ServiceStatus
import com.pocketsamp.ui.components.PocketCard
import com.pocketsamp.ui.components.PocketDialogContainer
import com.pocketsamp.ui.components.PocketFeedbackBanner
import com.pocketsamp.ui.components.PocketNavigationRow
import com.pocketsamp.ui.components.PocketPrimaryButton
import com.pocketsamp.ui.components.PocketScreenBackground
import com.pocketsamp.ui.components.PocketSecondaryButton
import com.pocketsamp.ui.components.PocketSettingToggleRow
import com.pocketsamp.ui.components.PocketTextField
import com.pocketsamp.ui.components.ServiceStatusBadge
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii
import com.pocketsamp.viewmodel.DatabaseDetailTab
import com.pocketsamp.viewmodel.DatabaseUiState

@Composable
fun DatabaseScreen(
    dbSnapshot: DbRuntimeSnapshot,
    preferencesState: AppPreferencesState,
    uiState: DatabaseUiState,
    onStartMariaDb: () -> Unit,
    onStopMariaDb: () -> Unit,
    onSetBufferPoolMb: (Int) -> Unit,
    onSetNormalizeMySql8: (Boolean) -> Unit,
    onRefreshDatabases: () -> Unit,
    onCreateDatabase: (String) -> Unit,
    onDropDatabase: (String) -> Unit,
    onImportSql: (String, android.net.Uri) -> Unit,
    onExportSql: (String, android.net.Uri) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onCloseDatabase: () -> Unit,
    onSelectDetailTab: (DatabaseDetailTab) -> Unit,
    onRefreshTables: (String) -> Unit,
    onOpenTable: (String) -> Unit,
    onCloseTable: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onInsertRow: (Map<String, String?>) -> Unit,
    onUpdateRow: (List<String?>, Map<String, String?>) -> Unit,
    onDeleteRow: (List<String?>) -> Unit,
    onSqlInputChange: (String) -> Unit,
    onExecuteSql: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDb = uiState.selectedDatabase
    val selectedTable = uiState.selectedTable

    if (selectedDb != null && selectedTable != null) {
        TableRowsView(
            tableName = selectedTable,
            uiState = uiState,
            onBack = onCloseTable,
            onLoadPage = onLoadPage,
            onInsertRow = onInsertRow,
            onUpdateRow = onUpdateRow,
            onDeleteRow = onDeleteRow,
            onDismissFeedback = onDismissFeedback,
            modifier = modifier
        )
        return
    }

    if (selectedDb != null) {
        DatabaseDetailView(
            databaseName = selectedDb,
            uiState = uiState,
            onBack = onCloseDatabase,
            onSelectTab = onSelectDetailTab,
            onRefreshTables = { onRefreshTables(selectedDb) },
            onOpenTable = onOpenTable,
            onSqlInputChange = onSqlInputChange,
            onExecuteSql = onExecuteSql,
            onDismissFeedback = onDismissFeedback,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    var showCreateDbDialog by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf("") }
    var droppingDb by remember { mutableStateOf<String?>(null) }
    var showBufferPoolSheet by remember { mutableStateOf(false) }
    var pendingTransferDb by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val db = pendingTransferDb
        pendingTransferDb = null
        if (uri != null && db != null) {
            onImportSql(db, uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/sql")
    ) { uri ->
        val db = pendingTransferDb
        pendingTransferDb = null
        if (uri != null && db != null) {
            onExportSql(db, uri)
        }
    }

    if (showCreateDbDialog) {
        PocketDialogContainer(
            title = stringResource(R.string.db_create),
            onDismiss = { showCreateDbDialog = false },
            secondaryButtonText = stringResource(R.string.action_cancel),
            onSecondaryClick = { showCreateDbDialog = false },
            primaryButtonText = stringResource(R.string.action_add),
            onPrimaryClick = {
                onCreateDatabase(newDbName)
                newDbName = ""
                showCreateDbDialog = false
            },
            primaryEnabled = newDbName.isNotBlank(),
            secondaryTestTag = "db_create_cancel_button",
            primaryTestTag = "db_create_confirm_button"
        ) {
            PocketTextField(
                value = newDbName,
                onValueChange = { newDbName = it },
                label = stringResource(R.string.db_create_hint),
                testTag = "db_create_name_input"
            )
        }
    }

    droppingDb?.let { targetDb ->
        PocketDialogContainer(
            title = stringResource(R.string.db_drop_confirm_title, targetDb),
            onDismiss = { droppingDb = null },
            secondaryButtonText = stringResource(R.string.action_cancel),
            onSecondaryClick = { droppingDb = null },
            primaryButtonText = stringResource(R.string.action_delete),
            onPrimaryClick = {
                onDropDatabase(targetDb)
                droppingDb = null
            },
            secondaryTestTag = "db_drop_cancel_button",
            primaryTestTag = "db_drop_confirm_button"
        ) {
            Text(
                text = stringResource(R.string.db_drop_confirm_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary
            )
        }
    }

    if (showBufferPoolSheet) {
        BufferPoolBottomSheet(
            currentMb = preferencesState.mariaDbBufferPoolMb,
            onSelectMb = { mb ->
                onSetBufferPoolMb(mb)
                showBufferPoolSheet = false
            },
            onDismiss = { showBufferPoolSheet = false }
        )
    }

    val statusLabel = when (dbSnapshot.status) {
        ServiceStatus.STOPPED -> stringResource(R.string.db_status_stopped)
        ServiceStatus.STARTING -> stringResource(R.string.db_status_starting)
        ServiceStatus.RUNNING -> stringResource(R.string.db_status_running, dbSnapshot.port)
        ServiceStatus.STOPPING -> stringResource(R.string.server_status_stopping)
        ServiceStatus.ERROR -> stringResource(R.string.db_status_error)
    }

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
                    text = stringResource(R.string.db_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PocketSampColors.TextPrimary
                )

                if (uiState.feedbackMessage != null) {
                    PocketFeedbackBanner(
                        message = uiState.feedbackMessage,
                        isError = uiState.feedbackIsError,
                        onDismiss = onDismissFeedback
                    )
                }

                if (uiState.isBusyTransfer) {
                    PocketCard {
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
                                    text = stringResource(R.string.db_import_running),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PocketSampColors.TextPrimary
                                )
                                if (uiState.transferBytes > 0L) {
                                    Text(
                                        text = Formatter.formatShortFileSize(
                                            context,
                                            uiState.transferBytes
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PocketSampColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                PocketCard {
                    Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                        ServiceStatusBadge(
                            status = dbSnapshot.status,
                            label = statusLabel
                        )
                        Text(
                            text = stringResource(R.string.db_credentials_info, AppConstants.MARIADB_PORT),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary
                        )
                        if (dbSnapshot.status == ServiceStatus.RUNNING || dbSnapshot.pid > 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space16),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (dbSnapshot.pid > 0) {
                                    Text(
                                        text = stringResource(R.string.server_pid_label, dbSnapshot.pid),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PocketSampColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.server_ram_label,
                                        ProcessUtils.formatMemoryKb(context, dbSnapshot.rssKb)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PocketSampColors.TextSecondary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.server_uptime_label,
                                        ProcessUtils.formatUptime(dbSnapshot.uptimeSec)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PocketSampColors.TextSecondary
                                )
                            }
                        }
                        if (dbSnapshot.status == ServiceStatus.ERROR &&
                            dbSnapshot.errorDetail.isNotBlank()
                        ) {
                            Text(
                                text = dbSnapshot.errorDetail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PocketSampColors.StatusWarning
                            )
                        }
                        val dbBusy = dbSnapshot.status == ServiceStatus.RUNNING ||
                            dbSnapshot.status == ServiceStatus.STARTING ||
                            dbSnapshot.status == ServiceStatus.STOPPING
                        if (dbBusy) {
                            PocketSecondaryButton(
                                text = stringResource(R.string.db_stop),
                                onClick = onStopMariaDb,
                                enabled = dbSnapshot.status != ServiceStatus.STOPPING,
                                icon = Icons.Outlined.Stop,
                                testTag = "db_stop_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            PocketPrimaryButton(
                                text = stringResource(R.string.db_start),
                                onClick = onStartMariaDb,
                                icon = Icons.Outlined.PlayArrow,
                                testTag = "db_start_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PocketCard(contentPadding = PaddingValues(0.dp)) {
                    PocketNavigationRow(
                        icon = Icons.Outlined.Memory,
                        title = stringResource(
                            R.string.db_buffer_pool_label,
                            preferencesState.mariaDbBufferPoolMb
                        ),
                        value = stringResource(
                            R.string.db_buffer_pool_option,
                            preferencesState.mariaDbBufferPoolMb
                        ),
                        onClick = { showBufferPoolSheet = true },
                        testTag = "db_buffer_pool_row"
                    )
                    HorizontalDivider(
                        color = PocketSampColors.BorderSubtle,
                        modifier = Modifier.padding(horizontal = PocketSampDimens.Space16)
                    )
                    PocketSettingToggleRow(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.db_normalize_mysql8_title),
                        description = stringResource(R.string.db_normalize_mysql8_desc),
                        checked = preferencesState.normalizeMySql8Import,
                        onCheckedChange = onSetNormalizeMySql8,
                        testTag = "db_normalize_mysql8_switch"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.db_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = PocketSampColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (dbSnapshot.status == ServiceStatus.RUNNING) {
                        IconButton(
                            onClick = onRefreshDatabases,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("db_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = PocketSampColors.TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { showCreateDbDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("db_create_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.db_create),
                                tint = PocketSampColors.AccentBorder
                            )
                        }
                    }
                }

                if (dbSnapshot.status != ServiceStatus.RUNNING) {
                    PocketCard {
                        Text(
                            text = stringResource(R.string.db_start_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary
                        )
                    }
                } else if (uiState.isLoadingDatabases) {
                    PocketCard {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PocketSampDimens.Space16)
                        ) {
                            CircularProgressIndicator(
                                color = PocketSampColors.AccentBlue,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (uiState.databases.isEmpty()) {
                    PocketCard {
                        Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                            Text(
                                text = stringResource(R.string.db_empty_databases),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PocketSampColors.TextSecondary
                            )
                            PocketPrimaryButton(
                                text = stringResource(R.string.db_create),
                                onClick = { showCreateDbDialog = true },
                                icon = Icons.Outlined.Add,
                                testTag = "db_empty_create_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                        uiState.databases.forEach { dbName ->
                            PocketCard(onClick = { onOpenDatabase(dbName) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Storage,
                                            contentDescription = null,
                                            tint = PocketSampColors.AccentBorder,
                                            modifier = Modifier.size(PocketSampDimens.IconSize)
                                        )
                                        Spacer(modifier = Modifier.width(PocketSampDimens.Space12))
                                        Text(
                                            text = dbName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = PocketSampColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { droppingDb = dbName },
                                            enabled = !uiState.isBusyTransfer,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("db_drop_$dbName")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.action_delete),
                                                tint = PocketSampColors.TextSecondary
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        PocketSecondaryButton(
                                            text = stringResource(R.string.db_import_sql),
                                            onClick = {
                                                pendingTransferDb = dbName
                                                importLauncher.launch(arrayOf("*/*"))
                                            },
                                            enabled = !uiState.isBusyTransfer,
                                            icon = Icons.Outlined.Upload,
                                            testTag = "db_import_$dbName",
                                            modifier = Modifier.weight(1f)
                                        )
                                        PocketSecondaryButton(
                                            text = stringResource(R.string.db_export_sql),
                                            onClick = {
                                                pendingTransferDb = dbName
                                                exportLauncher.launch("$dbName.sql")
                                            },
                                            enabled = !uiState.isBusyTransfer,
                                            icon = Icons.Outlined.Download,
                                            testTag = "db_export_$dbName",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BufferPoolBottomSheet(
    currentMb: Int,
    onSelectMb: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember { listOf(8, 16, 32, 64) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = PocketSampRadii.Sheet,
            topEnd = PocketSampRadii.Sheet
        ),
        containerColor = PocketSampColors.SurfaceOverlay,
        scrimColor = PocketSampColors.ScrimDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PocketSampColors.GlowRadialNeutral,
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = PocketSampDimens.GlowRadiusPx
                        ),
                        radius = PocketSampDimens.GlowRadiusPx,
                        center = Offset(size.width / 2f, 0f)
                    )
                }
                .padding(bottom = PocketSampDimens.Space24)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PocketSampDimens.Space16,
                        top = PocketSampDimens.Space12,
                        end = PocketSampDimens.Space8,
                        bottom = PocketSampDimens.Space4
                    )
            ) {
                Text(
                    text = stringResource(R.string.db_buffer_pool_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = PocketSampColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = PocketSampDimens.Space24)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = PocketSampColors.TextSecondary
                    )
                }
            }

            Text(
                text = stringResource(R.string.db_buffer_pool_restart_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary,
                modifier = Modifier.padding(
                    horizontal = PocketSampDimens.Space20,
                    vertical = PocketSampDimens.Space8
                )
            )

            options.forEachIndexed { idx, mb ->
                val selected = mb == currentMb
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PocketSampDimens.RowMinHeight)
                        .clickable { onSelectMb(mb) }
                        .padding(horizontal = PocketSampDimens.Space20)
                        .testTag("buffer_pool_option_$mb")
                ) {
                    Text(
                        text = stringResource(R.string.db_buffer_pool_option, mb),
                        style = MaterialTheme.typography.titleMedium,
                        color = PocketSampColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.option_selected_cd),
                            tint = PocketSampColors.TextPrimary
                        )
                    }
                }
                if (idx < options.lastIndex) {
                    HorizontalDivider(
                        color = PocketSampColors.BorderSubtle,
                        modifier = Modifier.padding(horizontal = PocketSampDimens.Space20)
                    )
                }
            }
        }
    }
}
