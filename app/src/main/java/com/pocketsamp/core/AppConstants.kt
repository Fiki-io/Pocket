package com.pocketsamp.core

object AppConstants {
    const val DATASTORE_NAME = "pocketsamp_preferences"
    const val KEY_PROFILES_JSON = "profiles_json"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    const val KEY_MARIADB_BUFFER_POOL_MB = "mariadb_buffer_pool_mb"
    const val KEY_NORMALIZE_MYSQL8_IMPORT = "normalize_mysql8_import"

    const val RUNTIME_VERSION = 1
    const val RUNTIME_DIR = "runtime"
    const val EXEC_CACHE_DIR = "exec_cache"
    const val SYSROOT_ARCHIVE_ASSET = "sysroot-i386.tar.xz"
    const val SYSROOT_DIR_NAME = "sysroot-i386"
    const val SYSROOT_MARKER = ".sysroot_ready_v$RUNTIME_VERSION"

    const val MARIADB_ASSETS_ARCHIVE = "mariadb-assets.tar.xz"
    const val MARIADB_ASSETS_DIR_NAME = "mariadb-assets"
    const val MARIADB_ASSETS_MARKER = ".mariadb_assets_ready_v$RUNTIME_VERSION"

    const val LIB_QEMU_I386 = "libqemu_i386.so"
    const val LIB_MARIADBD = "libmariadbd.so"
    const val LIB_MARIADB_CLI = "libmariadb_cli.so"
    const val LIB_MARIADB_DUMP = "libmariadb_dump.so"
    const val LIB_ANDROID_SHIM = "libandroid_shim.so"

    const val DEFAULT_SAMP_EXECUTABLE = "samp03svr"
    const val SAMP_SERVER_LOG_FILE = "server_log.txt"
    const val SAMP_CFG_FILE = "server.cfg"
    const val DEFAULT_SAMP_PORT = 7777
    const val QEMU_RESERVED_VA = "768M"
    const val QEMU_FALLBACK_RESERVED_VA = "0"

    const val MARIADB_HOME_DIR = "mariadb"
    const val MARIADB_DATA_DIR = "data"
    const val MARIADB_TMP_DIR = "tmp"
    const val MARIADB_CONF_FILE = "my.cnf"
    const val MARIADB_SOCKET_FILE = "mariadb.sock"
    const val MARIADB_ERR_LOG_FILE = "mariadb.err"
    const val MARIADB_HOST = "127.0.0.1"
    const val MARIADB_PORT = 3306
    const val MARIADB_USER = "root"
    const val DEFAULT_MARIADB_BUFFER_POOL_MB = 16
    const val MARIADB_MAX_CONNECTIONS = 20

    const val CONSOLE_MAX_LINES = 500
    const val DB_PAGE_SIZE = 50
    const val PROCESS_POLL_INTERVAL_MS = 1500L
    const val LOG_TAIL_INTERVAL_MS = 300L
    const val RCON_TIMEOUT_MS = 2000
    const val STOP_GRACE_TIMEOUT_MS = 3500L
    const val DB_READY_TIMEOUT_MS = 15000L

    const val ROUTE_SERVER = "server"
    const val ROUTE_CONFIG = "config"
    const val ROUTE_DATABASE = "database"

    const val CHANNEL_SERVER_ID = "pocketsamp_server_channel"
    const val CHANNEL_DB_ID = "pocketsamp_db_channel"
    const val NOTIF_SERVER_ID = 1001
    const val NOTIF_DB_ID = 1002

    const val ACTION_START_SERVER = "com.pocketsamp.action.START_SERVER"
    const val ACTION_STOP_SERVER = "com.pocketsamp.action.STOP_SERVER"
    const val ACTION_SEND_RCON = "com.pocketsamp.action.SEND_RCON"
    const val ACTION_CLEAR_LOGS = "com.pocketsamp.action.CLEAR_LOGS"
    const val ACTION_START_DB = "com.pocketsamp.action.START_DB"
    const val ACTION_STOP_DB = "com.pocketsamp.action.STOP_DB"

    const val EXTRA_PROFILE_ID = "extra_profile_id"
    const val EXTRA_PROFILE_NAME = "extra_profile_name"
    const val EXTRA_FOLDER_PATH = "extra_folder_path"
    const val EXTRA_EXECUTABLE = "extra_executable"
    const val EXTRA_LOW_PRIORITY = "extra_low_priority"
    const val EXTRA_RCON_COMMAND = "extra_rcon_command"
    const val EXTRA_BUFFER_POOL_MB = "extra_buffer_pool_mb"

    const val MSG_REGISTER_CLIENT = 1
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_SERVER_STATE = 3
    const val MSG_SERVER_LOG_BATCH = 4
    const val MSG_SERVER_LOG_RESET = 5
    const val MSG_DB_STATE = 6

    const val BUNDLE_STATUS = "status"
    const val BUNDLE_PID = "pid"
    const val BUNDLE_RSS_KB = "rss_kb"
    const val BUNDLE_UPTIME_SEC = "uptime_sec"
    const val BUNDLE_PORT = "port"
    const val BUNDLE_PROFILE_NAME = "profile_name"
    const val BUNDLE_ERROR = "error"
    const val BUNDLE_LOG_LINES = "log_lines"
}
