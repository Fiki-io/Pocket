package com.pocketsamp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.core.AppContainer
import com.pocketsamp.data.AppPreferencesState
import com.pocketsamp.db.SqlQueryResult
import com.pocketsamp.db.TableColumnMeta
import com.pocketsamp.server.DbRuntimeSnapshot
import com.pocketsamp.server.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DatabaseDetailTab {
    TABLES,
    SQL
}

@Immutable
data class DatabaseUiState(
    val databases: List<String> = emptyList(),
    val isLoadingDatabases: Boolean = false,
    val selectedDatabase: String? = null,
    val detailTab: DatabaseDetailTab = DatabaseDetailTab.TABLES,
    val tables: List<String> = emptyList(),
    val isLoadingTables: Boolean = false,
    val selectedTable: String? = null,
    val tableColumns: List<TableColumnMeta> = emptyList(),
    val tableRowsResult: SqlQueryResult = SqlQueryResult(emptyList(), emptyList()),
    val tablePage: Int = 0,
    val isLoadingRows: Boolean = false,
    val sqlInput: String = "",
    val sqlResult: SqlQueryResult? = null,
    val isRunningSql: Boolean = false,
    val isBusyTransfer: Boolean = false,
    val transferBytes: Long = 0L,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false
)

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.getInstance(application)
    private val profileRepository = container.profileRepository
    private val databaseController = container.databaseController
    private val mariaDbClient = container.mariaDbClient

    val dbSnapshot: StateFlow<DbRuntimeSnapshot> = databaseController.snapshot
    val preferencesState: StateFlow<AppPreferencesState> = profileRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferencesState()
        )

    private val _uiState = MutableStateFlow(DatabaseUiState())
    val uiState: StateFlow<DatabaseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dbSnapshot
                .distinctUntilChangedBy { it.status }
                .collectLatest { snap ->
                    if (snap.status == ServiceStatus.RUNNING) {
                        refreshDatabases()
                    } else if (snap.status == ServiceStatus.STOPPED || snap.status == ServiceStatus.ERROR) {
                        _uiState.update {
                            it.copy(
                                databases = emptyList(),
                                selectedDatabase = null,
                                selectedTable = null,
                                tables = emptyList(),
                                tableColumns = emptyList(),
                                tableRowsResult = SqlQueryResult(emptyList(), emptyList()),
                                sqlResult = null
                            )
                        }
                    }
                }
        }
    }

    fun startMariaDb() {
        databaseController.startMariaDb(preferencesState.value.mariaDbBufferPoolMb)
    }

    fun stopMariaDb() {
        databaseController.stopMariaDb()
    }

    fun setBufferPoolMb(mb: Int) {
        viewModelScope.launch {
            profileRepository.setMariaDbBufferPoolMb(mb)
        }
    }

    fun setNormalizeMySql8Import(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.setNormalizeMySql8Import(enabled)
        }
    }

    fun refreshDatabases() {
        if (dbSnapshot.value.status != ServiceStatus.RUNNING) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDatabases = true) }
            val result = mariaDbClient.listUserDatabases()
            _uiState.update { state ->
                result.fold(
                    onSuccess = { dbs ->
                        state.copy(
                            databases = dbs,
                            isLoadingDatabases = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isLoadingDatabases = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                )
            }
        }
    }

    fun createDatabase(name: String) {
        viewModelScope.launch {
            mariaDbClient.createDatabase(name.trim()).fold(
                onSuccess = {
                    _uiState.update { it.copy(feedbackMessage = null) }
                    refreshDatabases()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun dropDatabase(name: String) {
        viewModelScope.launch {
            mariaDbClient.dropDatabase(name).fold(
                onSuccess = {
                    _uiState.update { state ->
                        if (state.selectedDatabase == name) {
                            state.copy(
                                selectedDatabase = null,
                                selectedTable = null,
                                tables = emptyList(),
                                feedbackMessage = null
                            )
                        } else {
                            state.copy(feedbackMessage = null)
                        }
                    }
                    refreshDatabases()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun importSql(database: String, uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusyTransfer = true,
                    transferBytes = 0L,
                    feedbackMessage = null
                )
            }
            val result = mariaDbClient.importSqlUri(
                database = database,
                uri = uri,
                normalizeMySql8 = preferencesState.value.normalizeMySql8Import,
                onBytesProcessed = { bytes ->
                    _uiState.update { it.copy(transferBytes = bytes) }
                }
            )
            _uiState.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isBusyTransfer = false,
                            feedbackMessage = app.getString(R.string.db_import_success, database),
                            feedbackIsError = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isBusyTransfer = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                )
            }
            if (_uiState.value.selectedDatabase == database) {
                refreshTables(database)
            }
        }
    }

    fun exportSql(database: String, uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyTransfer = true, feedbackMessage = null) }
            val result = mariaDbClient.exportSqlUri(database, uri)
            _uiState.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isBusyTransfer = false,
                            feedbackMessage = app.getString(R.string.db_export_success),
                            feedbackIsError = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isBusyTransfer = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                )
            }
        }
    }

    fun openDatabase(database: String) {
        _uiState.update {
            it.copy(
                selectedDatabase = database,
                selectedTable = null,
                detailTab = DatabaseDetailTab.TABLES,
                sqlResult = null,
                feedbackMessage = null
            )
        }
        refreshTables(database)
    }

    fun closeDatabase() {
        _uiState.update {
            it.copy(
                selectedDatabase = null,
                selectedTable = null,
                tables = emptyList(),
                tableColumns = emptyList(),
                tableRowsResult = SqlQueryResult(emptyList(), emptyList()),
                sqlResult = null,
                feedbackMessage = null
            )
        }
    }

    fun setDetailTab(tab: DatabaseDetailTab) {
        _uiState.update { it.copy(detailTab = tab, feedbackMessage = null) }
    }

    fun refreshTables(database: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTables = true) }
            val res = mariaDbClient.listTables(database)
            _uiState.update { state ->
                res.fold(
                    onSuccess = { tables ->
                        state.copy(tables = tables, isLoadingTables = false)
                    },
                    onFailure = { err ->
                        state.copy(
                            isLoadingTables = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                )
            }
        }
    }

    fun openTable(table: String) {
        val db = _uiState.value.selectedDatabase ?: return
        _uiState.update {
            it.copy(
                selectedTable = table,
                tablePage = 0,
                isLoadingRows = true,
                feedbackMessage = null
            )
        }
        loadTablePage(db, table, page = 0, loadMeta = true)
    }

    fun closeTable() {
        _uiState.update {
            it.copy(
                selectedTable = null,
                tableColumns = emptyList(),
                tableRowsResult = SqlQueryResult(emptyList(), emptyList()),
                tablePage = 0,
                feedbackMessage = null
            )
        }
    }

    fun loadPage(page: Int) {
        val db = _uiState.value.selectedDatabase ?: return
        val table = _uiState.value.selectedTable ?: return
        val targetPage = page.coerceAtLeast(0)
        loadTablePage(db, table, targetPage, loadMeta = false)
    }

    fun insertRow(values: Map<String, String?>) {
        val db = _uiState.value.selectedDatabase ?: return
        val table = _uiState.value.selectedTable ?: return
        viewModelScope.launch {
            mariaDbClient.insertRow(db, table, values).fold(
                onSuccess = {
                    loadTablePage(db, table, _uiState.value.tablePage, loadMeta = false)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun updateRow(originalRow: List<String?>, updatedValues: Map<String, String?>) {
        val state = _uiState.value
        val db = state.selectedDatabase ?: return
        val table = state.selectedTable ?: return
        viewModelScope.launch {
            mariaDbClient.updateRow(db, table, state.tableColumns, originalRow, updatedValues).fold(
                onSuccess = {
                    loadTablePage(db, table, state.tablePage, loadMeta = false)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun deleteRow(originalRow: List<String?>) {
        val state = _uiState.value
        val db = state.selectedDatabase ?: return
        val table = state.selectedTable ?: return
        viewModelScope.launch {
            mariaDbClient.deleteRow(db, table, state.tableColumns, originalRow).fold(
                onSuccess = {
                    loadTablePage(db, table, state.tablePage, loadMeta = false)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun updateSqlInput(sql: String) {
        _uiState.update { it.copy(sqlInput = sql) }
    }

    fun executeSql() {
        val app = getApplication<Application>()
        val db = _uiState.value.selectedDatabase
        val sql = _uiState.value.sqlInput
        if (sql.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningSql = true, feedbackMessage = null) }
            mariaDbClient.executeRawSql(db, sql).fold(
                onSuccess = { res ->
                    val infoMsg = if (res.columns.isEmpty()) {
                        app.getString(R.string.db_sql_affected)
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            isRunningSql = false,
                            sqlResult = res,
                            feedbackMessage = infoMsg,
                            feedbackIsError = false
                        )
                    }
                    if (db != null) {
                        refreshTables(db)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRunningSql = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun loadTablePage(database: String, table: String, page: Int, loadMeta: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRows = true) }
            val cols = if (loadMeta || _uiState.value.tableColumns.isEmpty()) {
                mariaDbClient.describeTable(database, table).getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingRows = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                    return@launch
                }
            } else {
                _uiState.value.tableColumns
            }

            val rowsRes = mariaDbClient.queryTableRows(
                database = database,
                table = table,
                page = page,
                pageSize = AppConstants.DB_PAGE_SIZE
            )
            _uiState.update { state ->
                rowsRes.fold(
                    onSuccess = { res ->
                        state.copy(
                            tableColumns = cols,
                            tableRowsResult = res,
                            tablePage = page,
                            isLoadingRows = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            tableColumns = cols,
                            isLoadingRows = false,
                            feedbackMessage = err.message ?: err.javaClass.simpleName,
                            feedbackIsError = true
                        )
                    }
                )
            }
        }
    }
}
