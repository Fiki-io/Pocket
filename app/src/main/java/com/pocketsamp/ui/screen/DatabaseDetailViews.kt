package com.pocketsamp.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.db.SqlQueryResult
import com.pocketsamp.db.TableColumnMeta
import com.pocketsamp.ui.components.PocketCard
import com.pocketsamp.ui.components.PocketDialogContainer
import com.pocketsamp.ui.components.PocketFeedbackBanner
import com.pocketsamp.ui.components.PocketPrimaryButton
import com.pocketsamp.ui.components.PocketScreenBackground
import com.pocketsamp.ui.components.PocketSecondaryButton
import com.pocketsamp.ui.components.PocketTextField
import com.pocketsamp.ui.theme.ConsoleTextStyle
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii
import com.pocketsamp.viewmodel.DatabaseDetailTab
import com.pocketsamp.viewmodel.DatabaseUiState

@Composable
fun DatabaseDetailView(
    databaseName: String,
    uiState: DatabaseUiState,
    onBack: () -> Unit,
    onSelectTab: (DatabaseDetailTab) -> Unit,
    onRefreshTables: () -> Unit,
    onOpenTable: (String) -> Unit,
    onSqlInputChange: (String) -> Unit,
    onExecuteSql: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    PocketScreenBackground(modifier = modifier) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = PocketSampDimens.ScreenHorizontalPadding,
                    vertical = PocketSampDimens.Space12
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
                modifier = Modifier
                    .widthIn(max = PocketSampDimens.MaxContentWidth)
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("db_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.db_back_cd),
                            tint = PocketSampColors.TextPrimary
                        )
                    }
                    Text(
                        text = databaseName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PocketSampColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.detailTab == DatabaseDetailTab.TABLES) {
                        IconButton(
                            onClick = onRefreshTables,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("db_tables_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = PocketSampColors.TextSecondary
                            )
                        }
                    }
                }

                if (uiState.feedbackMessage != null) {
                    PocketFeedbackBanner(
                        message = uiState.feedbackMessage,
                        isError = uiState.feedbackIsError,
                        onDismiss = onDismissFeedback
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DetailTabChip(
                        text = stringResource(R.string.db_tab_tables),
                        selected = uiState.detailTab == DatabaseDetailTab.TABLES,
                        onClick = { onSelectTab(DatabaseDetailTab.TABLES) },
                        testTag = "db_tab_tables",
                        modifier = Modifier.weight(1f)
                    )
                    DetailTabChip(
                        text = stringResource(R.string.db_tab_sql),
                        selected = uiState.detailTab == DatabaseDetailTab.SQL,
                        onClick = { onSelectTab(DatabaseDetailTab.SQL) },
                        testTag = "db_tab_sql",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.detailTab == DatabaseDetailTab.TABLES) {
                    TablesListCard(
                        tables = uiState.tables,
                        isLoading = uiState.isLoadingTables,
                        onOpenTable = onOpenTable,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    SqlConsoleSection(
                        sqlInput = uiState.sqlInput,
                        sqlResult = uiState.sqlResult,
                        isRunning = uiState.isRunningSql,
                        onSqlInputChange = onSqlInputChange,
                        onExecuteSql = onExecuteSql,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TablesListCard(
    tables: List<String>,
    isLoading: Boolean,
    onOpenTable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PocketCard(
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PocketSampDimens.Space24)
                ) {
                    CircularProgressIndicator(
                        color = PocketSampColors.AccentBlue,
                        strokeWidth = 2.dp
                    )
                }
            }

            tables.isEmpty() -> {
                Text(
                    text = stringResource(R.string.db_tables_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketSampColors.TextSecondary,
                    modifier = Modifier.padding(PocketSampDimens.Space16)
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(tables, key = { _, t -> t }) { index, tableName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PocketSampDimens.RowMinHeight)
                                .clickable { onOpenTable(tableName) }
                                .padding(
                                    horizontal = PocketSampDimens.Space16,
                                    vertical = PocketSampDimens.Space12
                                )
                                .testTag("db_table_row_$tableName")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TableRows,
                                contentDescription = null,
                                tint = PocketSampColors.TextSecondary,
                                modifier = Modifier.size(PocketSampDimens.IconSize)
                            )
                            Spacer(modifier = Modifier.width(PocketSampDimens.Space12))
                            Text(
                                text = tableName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PocketSampColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PocketSampColors.TextSecondary
                            )
                        }
                        if (index < tables.lastIndex) {
                            HorizontalDivider(
                                color = PocketSampColors.BorderSubtle,
                                modifier = Modifier.padding(horizontal = PocketSampDimens.Space16)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleSection(
    sqlInput: String,
    sqlResult: SqlQueryResult?,
    isRunning: Boolean,
    onSqlInputChange: (String) -> Unit,
    onExecuteSql: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
        modifier = modifier.fillMaxSize()
    ) {
        PocketCard {
            Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
                PocketTextField(
                    value = sqlInput,
                    onValueChange = onSqlInputChange,
                    label = stringResource(R.string.db_sql_hint),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 7,
                    testTag = "db_sql_input"
                )
                PocketPrimaryButton(
                    text = stringResource(R.string.db_sql_run),
                    onClick = onExecuteSql,
                    enabled = !isRunning && sqlInput.isNotBlank(),
                    icon = Icons.Outlined.PlayArrow,
                    testTag = "db_sql_run_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (sqlResult != null && sqlResult.columns.isNotEmpty()) {
            PocketCard(
                contentPadding = PaddingValues(PocketSampDimens.Space12),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ResultGrid(
                    columns = sqlResult.columns,
                    rows = sqlResult.rows
                )
            }
        }
    }
}

@Composable
fun TableRowsView(
    tableName: String,
    uiState: DatabaseUiState,
    onBack: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onInsertRow: (Map<String, String?>) -> Unit,
    onUpdateRow: (List<String?>, Map<String, String?>) -> Unit,
    onDeleteRow: (List<String?>) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showInsertDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<List<String?>?>(null) }
    var deletingRow by remember { mutableStateOf<List<String?>?>(null) }

    if (showInsertDialog) {
        RowEditorDialog(
            title = stringResource(R.string.db_row_insert),
            columns = uiState.tableColumns,
            initialValues = null,
            onSave = { values ->
                onInsertRow(values)
                showInsertDialog = false
            },
            onDismiss = { showInsertDialog = false }
        )
    }

    editingRow?.let { targetRow ->
        RowEditorDialog(
            title = stringResource(R.string.db_row_edit),
            columns = uiState.tableColumns,
            initialValues = targetRow,
            onSave = { values ->
                onUpdateRow(targetRow, values)
                editingRow = null
            },
            onDismiss = { editingRow = null }
        )
    }

    deletingRow?.let { targetRow ->
        PocketDialogContainer(
            title = stringResource(R.string.db_row_delete_confirm_title),
            onDismiss = { deletingRow = null },
            secondaryButtonText = stringResource(R.string.action_cancel),
            onSecondaryClick = { deletingRow = null },
            primaryButtonText = stringResource(R.string.action_delete),
            onPrimaryClick = {
                onDeleteRow(targetRow)
                deletingRow = null
            },
            secondaryTestTag = "db_row_delete_cancel_button",
            primaryTestTag = "db_row_delete_confirm_button"
        ) {
            Text(
                text = stringResource(R.string.db_row_delete_confirm_desc, tableName),
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary
            )
        }
    }

    val result = uiState.tableRowsResult
    PocketScreenBackground(modifier = modifier) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = PocketSampDimens.ScreenHorizontalPadding,
                    vertical = PocketSampDimens.Space12
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
                modifier = Modifier
                    .widthIn(max = PocketSampDimens.MaxContentWidth)
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("db_rows_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.db_back_cd),
                            tint = PocketSampColors.TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.db_rows_title, tableName),
                        style = MaterialTheme.typography.titleLarge,
                        color = PocketSampColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showInsertDialog = true },
                        enabled = uiState.tableColumns.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("db_row_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.db_row_insert),
                            tint = PocketSampColors.AccentBorder
                        )
                    }
                }

                if (uiState.feedbackMessage != null) {
                    PocketFeedbackBanner(
                        message = uiState.feedbackMessage,
                        isError = uiState.feedbackIsError,
                        onDismiss = onDismissFeedback
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.db_rows_count,
                            result.rows.size,
                            uiState.tablePage + 1
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8)) {
                        PocketSecondaryButton(
                            text = stringResource(R.string.db_page_prev),
                            onClick = { onLoadPage(uiState.tablePage - 1) },
                            enabled = uiState.tablePage > 0 && !uiState.isLoadingRows,
                            testTag = "db_page_prev_button"
                        )
                        PocketSecondaryButton(
                            text = stringResource(R.string.db_page_next),
                            onClick = { onLoadPage(uiState.tablePage + 1) },
                            enabled = result.rows.size >= AppConstants.DB_PAGE_SIZE && !uiState.isLoadingRows,
                            testTag = "db_page_next_button"
                        )
                    }
                }

                PocketCard(
                    contentPadding = PaddingValues(PocketSampDimens.Space12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        uiState.isLoadingRows -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    color = PocketSampColors.AccentBlue,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        result.rows.isEmpty() -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = stringResource(R.string.db_rows_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PocketSampColors.TextSecondary
                                )
                            }
                        }

                        else -> {
                            val nullText = stringResource(R.string.db_null_label)
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(result.rows) { rowIndex, row ->
                                    val rowShape = RoundedCornerShape(PocketSampRadii.Field)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(rowShape)
                                            .background(PocketSampColors.ConsoleBackground)
                                            .border(
                                                PocketSampDimens.BorderWidth,
                                                PocketSampColors.BorderSubtle,
                                                rowShape
                                            )
                                            .padding(PocketSampDimens.Space12)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = { editingRow = row },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("db_row_edit_$rowIndex")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = stringResource(R.string.action_edit),
                                                    tint = PocketSampColors.TextSecondary,
                                                    modifier = Modifier.size(PocketSampDimens.SmallIconSize)
                                                )
                                            }
                                            IconButton(
                                                onClick = { deletingRow = row },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("db_row_delete_$rowIndex")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = stringResource(R.string.action_delete),
                                                    tint = PocketSampColors.TextSecondary,
                                                    modifier = Modifier.size(PocketSampDimens.SmallIconSize)
                                                )
                                            }
                                        }
                                        result.columns.forEachIndexed { colIndex, colName ->
                                            val cellVal = row.getOrNull(colIndex)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = colName,
                                                    style = ConsoleTextStyle,
                                                    color = PocketSampColors.AccentBorder,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.width(120.dp)
                                                )
                                                Text(
                                                    text = cellVal ?: nullText,
                                                    style = ConsoleTextStyle,
                                                    color = if (cellVal == null) {
                                                        PocketSampColors.TextTertiary
                                                    } else {
                                                        PocketSampColors.TextPrimary
                                                    },
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
    }
}

@Composable
private fun ResultGrid(
    columns: List<String>,
    rows: List<List<String?>>
) {
    val nullLabel = stringResource(R.string.db_null_label)
    val horizontalScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .background(PocketSampColors.SurfaceInteractive)
                        .padding(vertical = PocketSampDimens.Space8, horizontal = PocketSampDimens.Space8)
                ) {
                    columns.forEach { col ->
                        Text(
                            text = col,
                            style = ConsoleTextStyle,
                            color = PocketSampColors.AccentBorder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(end = PocketSampDimens.Space8)
                        )
                    }
                }
                HorizontalDivider(color = PocketSampColors.BorderStrong)
            }
            itemsIndexed(rows) { _, row ->
                Row(
                    modifier = Modifier.padding(
                        vertical = PocketSampDimens.Space8,
                        horizontal = PocketSampDimens.Space8
                    )
                ) {
                    columns.indices.forEach { colIdx ->
                        val cell = row.getOrNull(colIdx)
                        Text(
                            text = cell ?: nullLabel,
                            style = ConsoleTextStyle,
                            color = if (cell == null) {
                                PocketSampColors.TextTertiary
                            } else {
                                PocketSampColors.TextPrimary
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(end = PocketSampDimens.Space8)
                        )
                    }
                }
                HorizontalDivider(color = PocketSampColors.BorderSubtle)
            }
        }
    }
}

@Composable
private fun RowEditorDialog(
    title: String,
    columns: List<TableColumnMeta>,
    initialValues: List<String?>?,
    onSave: (Map<String, String?>) -> Unit,
    onDismiss: () -> Unit
) {
    val fieldValues = remember(columns, initialValues) {
        mutableStateMapOf<String, String>().apply {
            columns.forEachIndexed { idx, col ->
                val raw = initialValues?.getOrNull(idx) ?: col.defaultValue.orEmpty()
                put(col.name, raw)
            }
        }
    }
    val nullFlags = remember(columns, initialValues) {
        mutableStateMapOf<String, Boolean>().apply {
            columns.forEachIndexed { idx, col ->
                val isNull = if (initialValues != null) {
                    initialValues.getOrNull(idx) == null
                } else {
                    col.nullable && col.defaultValue == null
                }
                put(col.name, isNull)
            }
        }
    }

    PocketDialogContainer(
        title = title,
        onDismiss = onDismiss,
        secondaryButtonText = stringResource(R.string.action_cancel),
        onSecondaryClick = onDismiss,
        primaryButtonText = stringResource(R.string.action_save),
        onPrimaryClick = {
            val payload = linkedMapOf<String, String?>()
            for (col in columns) {
                val isNull = nullFlags[col.name] == true
                payload[col.name] = if (isNull) null else fieldValues[col.name].orEmpty()
            }
            onSave(payload)
        },
        secondaryTestTag = "db_row_editor_cancel_button",
        primaryTestTag = "db_row_editor_save_button"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
        ) {
            columns.forEach { col ->
                val isNull = nullFlags[col.name] == true
                Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space4)) {
                    PocketTextField(
                        value = if (isNull) "" else fieldValues[col.name].orEmpty(),
                        onValueChange = { updated ->
                            nullFlags[col.name] = false
                            fieldValues[col.name] = updated
                        },
                        label = "${col.name} (${col.type})",
                        testTag = "db_row_field_${col.name}"
                    )
                    if (col.nullable) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nullFlags[col.name] = !isNull }
                        ) {
                            Checkbox(
                                checked = isNull,
                                onCheckedChange = { checked -> nullFlags[col.name] = checked },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PocketSampColors.AccentBlue,
                                    uncheckedColor = PocketSampColors.TextSecondary
                                )
                            )
                            Text(
                                text = stringResource(R.string.db_is_null_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PocketSampColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PocketSampRadii.Button),
        color = if (selected) PocketSampColors.AccentBlueMuted else PocketSampColors.SurfaceCard,
        border = BorderStroke(
            PocketSampDimens.BorderWidth,
            if (selected) PocketSampColors.AccentBlue else PocketSampColors.BorderSubtle
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PocketSampColors.TextPrimary else PocketSampColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = PocketSampDimens.Space12,
                vertical = PocketSampDimens.Space12
            )
        )
    }
}
