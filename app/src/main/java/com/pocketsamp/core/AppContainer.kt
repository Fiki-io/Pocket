package com.pocketsamp.core

import android.content.Context
import com.pocketsamp.config.ServerCfgRepository
import com.pocketsamp.data.ProfileRepository
import com.pocketsamp.db.MariaDbClient
import com.pocketsamp.runtime.RuntimeInstaller
import com.pocketsamp.server.DatabaseController
import com.pocketsamp.server.ServerController

class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    val runtimeInstaller: RuntimeInstaller by lazy { RuntimeInstaller(appContext) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(appContext) }
    val serverCfgRepository: ServerCfgRepository by lazy { ServerCfgRepository() }
    val serverController: ServerController by lazy { ServerController(appContext) }
    val databaseController: DatabaseController by lazy { DatabaseController(appContext) }
    val mariaDbClient: MariaDbClient by lazy { MariaDbClient(appContext) }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
