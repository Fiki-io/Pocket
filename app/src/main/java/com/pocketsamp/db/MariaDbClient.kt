package com.pocketsamp.db

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.pocketsamp.core.AppConstants
import com.pocketsamp.runtime.RuntimeInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Immutable
data class TableColumnMeta(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val defaultValue: String?
)

@Immutable
data class SqlQueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>
)

class MariaDbClient(private val context: Context) {

    private val installer = RuntimeInstaller(context)
    private val systemDatabases = setOf("information_schema", "mysql", "performance_schema", "sys")

    suspend fun listUserDatabases(): Result<List<String>> = withContext(Dispatchers.IO) {
        executeTsvQuery(null, "SHOW DATABASES;").map { res ->
            res.rows.mapNotNull { it.firstOrNull() }
                .filter { it !in systemDatabases }
                .sorted()
        }
    }

    suspend fun createDatabase(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isValidIdentifier(name)) {
            return@withContext Result.failure(
                IllegalArgumentException("Nama database hanya boleh huruf, angka, dan garis bawah")
            )
        }
        executeTsvQuery(
            null,
            "CREATE DATABASE `${escapeIdentifier(name)}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        ).map { }
    }

    suspend fun dropDatabase(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isValidIdentifier(name) || name.lowercase() in systemDatabases) {
            return@withContext Result.failure(IllegalArgumentException("Nama database tidak valid"))
        }
        executeTsvQuery(null, "DROP DATABASE `${escapeIdentifier(name)}`;").map { }
    }

    suspend fun listTables(database: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isValidIdentifier(database)) {
            return@withContext Result.failure(IllegalArgumentException("Database tidak valid"))
        }
        executeTsvQuery(database, "SHOW TABLES;").map { res ->
            res.rows.mapNotNull { it.firstOrNull() }.sorted()
        }
    }

    suspend fun describeTable(database: String, table: String): Result<List<TableColumnMeta>> =
        withContext(Dispatchers.IO) {
            executeTsvQuery(database, "DESCRIBE `${escapeIdentifier(table)}`;").map { res ->
                res.rows.mapNotNull { row ->
                    val name = row.getOrNull(0) ?: return@mapNotNull null
                    val type = row.getOrNull(1).orEmpty()
                    val nullable = row.getOrNull(2).equals("YES", ignoreCase = true)
                    val key = row.getOrNull(3).orEmpty()
                    val def = row.getOrNull(4)
                    TableColumnMeta(
                        name = name,
                        type = type,
                        nullable = nullable,
                        isPrimaryKey = key.equals("PRI", ignoreCase = true),
                        defaultValue = def
                    )
                }
            }
        }

    suspend fun queryTableRows(
        database: String,
        table: String,
        page: Int,
        pageSize: Int = AppConstants.DB_PAGE_SIZE
    ): Result<SqlQueryResult> = withContext(Dispatchers.IO) {
        val offset = (page.coerceAtLeast(0)) * pageSize
        executeTsvQuery(
            database,
            "SELECT * FROM `${escapeIdentifier(table)}` LIMIT $pageSize OFFSET $offset;"
        )
    }

    suspend fun insertRow(
        database: String,
        table: String,
        values: Map<String, String?>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (values.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Nilai kolom kosong"))
        }
        val cols = values.keys.joinToString(", ") { "`${escapeIdentifier(it)}`" }
        val vals = values.values.joinToString(", ") { formatSqlLiteral(it) }
        val sql = "INSERT INTO `${escapeIdentifier(table)}` ($cols) VALUES ($vals);"
        executeTsvQuery(database, sql).map { }
    }

    suspend fun updateRow(
        database: String,
        table: String,
        columns: List<TableColumnMeta>,
        originalRow: List<String?>,
        updatedValues: Map<String, String?>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (updatedValues.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada kolom yang diubah"))
        }
        val setClause = updatedValues.entries.joinToString(", ") { (k, v) ->
            "`${escapeIdentifier(k)}` = ${formatSqlLiteral(v)}"
        }
        val whereClause = buildWhereClause(columns, originalRow)
            ?: return@withContext Result.failure(IllegalStateException("Klausa WHERE tidak dapat dibentuk"))
        val sql = "UPDATE `${escapeIdentifier(table)}` SET $setClause WHERE $whereClause LIMIT 1;"
        executeTsvQuery(database, sql).map { }
    }

    suspend fun deleteRow(
        database: String,
        table: String,
        columns: List<TableColumnMeta>,
        originalRow: List<String?>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val whereClause = buildWhereClause(columns, originalRow)
            ?: return@withContext Result.failure(IllegalStateException("Klausa WHERE tidak dapat dibentuk"))
        val sql = "DELETE FROM `${escapeIdentifier(table)}` WHERE $whereClause LIMIT 1;"
        executeTsvQuery(database, sql).map { }
    }

    suspend fun executeRawSql(database: String?, sql: String): Result<SqlQueryResult> =
        withContext(Dispatchers.IO) {
            if (sql.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Perintah SQL kosong"))
            }
            executeTsvQuery(database, sql)
        }

    suspend fun importSqlUri(
        database: String,
        uri: Uri,
        normalizeMySql8: Boolean,
        onBytesProcessed: (Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val paths = installer.ensureInstalled().getOrElse { return@withContext Result.failure(it) }
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Berkas SQL tidak dapat dibuka"))

            val cmd = listOf(
                paths.mariadbCliBinary.absolutePath,
                "-h", AppConstants.MARIADB_HOST,
                "-P", AppConstants.MARIADB_PORT.toString(),
                "-u", AppConstants.MARIADB_USER,
                database
            )
            val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()

            var bytesTotal = 0L
            val stderrBuilder = StringBuilder()
            val stderrThread = Thread {
                proc.errorStream.bufferedReader(StandardCharsets.UTF_8).use { errReader ->
                    errReader.forEachLine { line ->
                        if (stderrBuilder.length < 4000) {
                            stderrBuilder.appendLine(line)
                        }
                    }
                }
            }.apply { start() }

            input.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8), 64 * 1024).use { reader ->
                    BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8), 64 * 1024).use { writer ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            val finalLine = if (normalizeMySql8) normalizeMySql8Line(line) else line
                            writer.write(finalLine)
                            writer.newLine()
                            bytesTotal += finalLine.length + 1
                            if (bytesTotal % (128 * 1024L) < 2048L) {
                                onBytesProcessed(bytesTotal)
                            }
                        }
                        writer.flush()
                    }
                }
            }

            onBytesProcessed(bytesTotal)
            val exitCode = proc.waitFor()
            stderrThread.join(2000L)
            if (exitCode != 0) {
                val errMsg = stderrBuilder.toString().trim().ifBlank { "Exit code $exitCode" }
                return@withContext Result.failure(IllegalStateException(errMsg))
            }
            Result.success(bytesTotal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSqlUri(database: String, uri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            try {
                val paths = installer.ensureInstalled().getOrElse { return@withContext Result.failure(it) }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext Result.failure(IllegalStateException("Berkas tujuan tidak dapat ditulis"))

                val cmd = listOf(
                    paths.mariadbDumpBinary.absolutePath,
                    "-h", AppConstants.MARIADB_HOST,
                    "-P", AppConstants.MARIADB_PORT.toString(),
                    "-u", AppConstants.MARIADB_USER,
                    "--routines",
                    "--triggers",
                    "--single-transaction",
                    database
                )
                val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()

                val stderrBuilder = StringBuilder()
                val stderrThread = Thread {
                    proc.errorStream.bufferedReader(StandardCharsets.UTF_8).use { errReader ->
                        errReader.forEachLine { line ->
                            if (stderrBuilder.length < 4000) {
                                stderrBuilder.appendLine(line)
                            }
                        }
                    }
                }.apply { start() }

                var bytesWritten = 0L
                output.use { outStream ->
                    proc.inputStream.use { inStream ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = inStream.read(buffer)
                            if (read <= 0) break
                            outStream.write(buffer, 0, read)
                            bytesWritten += read
                        }
                        outStream.flush()
                    }
                }

                val exitCode = proc.waitFor()
                stderrThread.join(2000L)
                if (exitCode != 0 || bytesWritten == 0L) {
                    val errMsg = stderrBuilder.toString().trim().ifBlank {
                        "Export gagal (kode keluar $exitCode, ukuran $bytesWritten byte)"
                    }
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }
                Result.success(bytesWritten)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun executeTsvQuery(database: String?, sql: String): Result<SqlQueryResult> {
        return try {
            val paths = installer.ensureInstalled().getOrElse { return Result.failure(it) }
            val cmd = buildList {
                add(paths.mariadbCliBinary.absolutePath)
                add("-h")
                add(AppConstants.MARIADB_HOST)
                add("-P")
                add(AppConstants.MARIADB_PORT.toString())
                add("-u")
                add(AppConstants.MARIADB_USER)
                add("--batch")
                if (!database.isNullOrBlank()) {
                    add(database)
                }
                add("-e")
                add(sql)
            }

            val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
            val stdout = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            val stderr = proc.errorStream.bufferedReader(StandardCharsets.UTF_8).readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                val errMsg = stderr.trim().ifBlank { "Exit code $exitCode" }
                return Result.failure(IllegalStateException(errMsg))
            }

            if (stdout.isBlank()) {
                return Result.success(SqlQueryResult(emptyList(), emptyList()))
            }

            val lines = stdout.trimEnd('\r', '\n').split('\n')
            if (lines.isEmpty()) {
                return Result.success(SqlQueryResult(emptyList(), emptyList()))
            }

            val columns = lines.first().trimEnd('\r').split('\t').map { unescapeTsvCell(it) ?: "" }
            val rows = lines.drop(1).map { line ->
                line.trimEnd('\r').split('\t').map { cell -> unescapeTsvCell(cell) }
            }
            Result.success(SqlQueryResult(columns = columns, rows = rows))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildWhereClause(
        columns: List<TableColumnMeta>,
        originalRow: List<String?>
    ): String? {
        if (columns.isEmpty() || columns.size != originalRow.size) return null
        val pkIndices = columns.indices.filter { columns[it].isPrimaryKey }
        val targetIndices = if (pkIndices.isNotEmpty()) pkIndices else columns.indices.toList()
        return targetIndices.joinToString(" AND ") { idx ->
            val col = escapeIdentifier(columns[idx].name)
            val value = originalRow[idx]
            if (value == null) "`$col` IS NULL" else "`$col` = ${formatSqlLiteral(value)}"
        }
    }

    private fun unescapeTsvCell(raw: String): String? {
        if (raw == "NULL") return null
        if (!raw.contains('\\')) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '0' -> sb.append('\u0000')
                    '\\' -> sb.append('\\')
                    'N' -> return null
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun normalizeMySql8Line(line: String): String {
        if (!line.contains("0900") && !line.contains("utf8mb3")) return line
        return line
            .replace("utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci")
            .replace("utf8mb4_0900_as_cs", "utf8mb4_bin")
            .replace("utf8mb4_0900_as_ci", "utf8mb4_unicode_ci")
            .replace("utf8mb3_general_ci", "utf8_general_ci")
            .replace("utf8mb3", "utf8")
    }

    private fun formatSqlLiteral(value: String?): String {
        if (value == null) return "NULL"
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u0000", "\\0")
        return "'$escaped'"
    }

    private fun escapeIdentifier(identifier: String): String {
        return identifier.replace("`", "``")
    }

    private fun isValidIdentifier(name: String): Boolean {
        return name.matches(Regex("^[a-zA-Z0-9_]{1,64}$"))
    }
}
