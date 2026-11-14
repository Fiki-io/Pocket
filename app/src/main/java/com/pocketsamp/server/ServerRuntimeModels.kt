package com.pocketsamp.server

import androidx.compose.runtime.Immutable

enum class ServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

@Immutable
data class ServerRuntimeSnapshot(
    val status: ServiceStatus = ServiceStatus.STOPPED,
    val pid: Int = -1,
    val rssKb: Long = -1L,
    val uptimeSec: Long = 0L,
    val port: Int = 7777,
    val profileName: String = "",
    val errorDetail: String = ""
)

@Immutable
data class DbRuntimeSnapshot(
    val status: ServiceStatus = ServiceStatus.STOPPED,
    val pid: Int = -1,
    val rssKb: Long = -1L,
    val uptimeSec: Long = 0L,
    val port: Int = 3306,
    val errorDetail: String = ""
)
