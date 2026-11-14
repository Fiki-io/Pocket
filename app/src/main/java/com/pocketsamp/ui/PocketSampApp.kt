package com.pocketsamp.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.ui.components.rememberReduceMotion
import com.pocketsamp.ui.screen.ConfigScreen
import com.pocketsamp.ui.screen.DatabaseScreen
import com.pocketsamp.ui.screen.ServerScreen
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampMotion
import com.pocketsamp.viewmodel.ConfigViewModel
import com.pocketsamp.viewmodel.DatabaseViewModel
import com.pocketsamp.viewmodel.ServerViewModel

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = AppConstants.ROUTE_SERVER,
        labelRes = R.string.nav_server,
        selectedIcon = Icons.Filled.Dns,
        unselectedIcon = Icons.Outlined.Dns,
        testTag = "nav_tab_server"
    ),
    TopDestination(
        route = AppConstants.ROUTE_CONFIG,
        labelRes = R.string.nav_config,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_config"
    ),
    TopDestination(
        route = AppConstants.ROUTE_DATABASE,
        labelRes = R.string.nav_database,
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
        testTag = "nav_tab_database"
    )
)

@Composable
fun PocketSampApp(
    serverViewModel: ServerViewModel = viewModel(),
    configViewModel: ConfigViewModel = viewModel(),
    databaseViewModel: DatabaseViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val reduceMotion = rememberReduceMotion()

    val runtimeState by serverViewModel.runtimeState.collectAsStateWithLifecycle()
    val preferencesState by serverViewModel.preferencesState.collectAsStateWithLifecycle()
    val serverSnapshot by serverViewModel.serverSnapshot.collectAsStateWithLifecycle()
    val dbSnapshot by serverViewModel.dbSnapshot.collectAsStateWithLifecycle()
    val consoleLogs by serverViewModel.consoleLogs.collectAsStateWithLifecycle()
    val hasStoragePermission by serverViewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val hasNotificationPermission by serverViewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val folderInspection by serverViewModel.folderInspection.collectAsStateWithLifecycle()
    val localIpv4s by serverViewModel.localIpv4s.collectAsStateWithLifecycle()
    val isWaitingForAutoDb by serverViewModel.isWaitingForAutoDb.collectAsStateWithLifecycle()
    val serverFeedback by serverViewModel.feedback.collectAsStateWithLifecycle()

    val configUiState by configViewModel.uiState.collectAsStateWithLifecycle()
    val dbUiState by databaseViewModel.uiState.collectAsStateWithLifecycle()
    val dbPrefsState by databaseViewModel.preferencesState.collectAsStateWithLifecycle()

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketSampColors.BackgroundBase)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = PocketSampColors.BackgroundBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    PocketBottomNavBar(
                        currentRoute = currentDestination?.hierarchy?.firstOrNull()?.route,
                        onSelectRoute = ::navigateTopLevel
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .imePadding()
            ) {
                if (useRail) {
                    PocketNavRail(
                        currentRoute = currentDestination?.hierarchy?.firstOrNull()?.route,
                        onSelectRoute = ::navigateTopLevel
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = AppConstants.ROUTE_SERVER,
                    enterTransition = {
                        if (reduceMotion) EnterTransition.None else fadeIn(PocketSampMotion.fastTween())
                    },
                    exitTransition = {
                        if (reduceMotion) ExitTransition.None else fadeOut(PocketSampMotion.fastTween())
                    },
                    popEnterTransition = {
                        if (reduceMotion) EnterTransition.None else fadeIn(PocketSampMotion.fastTween())
                    },
                    popExitTransition = {
                        if (reduceMotion) ExitTransition.None else fadeOut(PocketSampMotion.fastTween())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppConstants.ROUTE_SERVER) {
                        ServerScreen(
                            runtimeState = runtimeState,
                            preferencesState = preferencesState,
                            serverSnapshot = serverSnapshot,
                            dbSnapshot = dbSnapshot,
                            consoleLogs = consoleLogs,
                            hasStoragePermission = hasStoragePermission,
                            hasNotificationPermission = hasNotificationPermission,
                            folderInspection = folderInspection,
                            localIpv4s = localIpv4s,
                            isWaitingForAutoDb = isWaitingForAutoDb,
                            feedback = serverFeedback,
                            onRetryRuntime = serverViewModel::retryRuntimeInstall,
                            onRefreshEnvironment = serverViewModel::refreshEnvironmentState,
                            onSelectProfile = serverViewModel::selectProfile,
                            onSaveProfile = serverViewModel::saveProfile,
                            onDeleteProfile = serverViewModel::deleteProfile,
                            onStartServer = serverViewModel::startServer,
                            onStopServer = serverViewModel::stopServer,
                            onSendRcon = serverViewModel::sendRconCommand,
                            onClearLogs = serverViewModel::clearConsoleLogs,
                            onDismissFeedback = serverViewModel::dismissFeedback,
                            onNavigateToDatabase = { navigateTopLevel(AppConstants.ROUTE_DATABASE) }
                        )
                    }

                    composable(AppConstants.ROUTE_CONFIG) {
                        ConfigScreen(
                            uiState = configUiState,
                            onReload = configViewModel::reload,
                            onSetRawMode = configViewModel::setRawMode,
                            onUpdateCfg = configViewModel::updateCfg,
                            onSaveConfig = configViewModel::saveConfig,
                            onSetLowPriorityCpu = configViewModel::setLowPriorityCpu,
                            onSetAutoStartMariaDb = configViewModel::setAutoStartMariaDb,
                            onDismissFeedback = configViewModel::clearFeedback
                        )
                    }

                    composable(AppConstants.ROUTE_DATABASE) {
                        DatabaseScreen(
                            dbSnapshot = dbSnapshot,
                            preferencesState = dbPrefsState,
                            uiState = dbUiState,
                            onStartMariaDb = databaseViewModel::startMariaDb,
                            onStopMariaDb = databaseViewModel::stopMariaDb,
                            onSetBufferPoolMb = databaseViewModel::setBufferPoolMb,
                            onSetNormalizeMySql8 = databaseViewModel::setNormalizeMySql8Import,
                            onRefreshDatabases = databaseViewModel::refreshDatabases,
                            onCreateDatabase = databaseViewModel::createDatabase,
                            onDropDatabase = databaseViewModel::dropDatabase,
                            onImportSql = databaseViewModel::importSql,
                            onExportSql = databaseViewModel::exportSql,
                            onOpenDatabase = databaseViewModel::openDatabase,
                            onCloseDatabase = databaseViewModel::closeDatabase,
                            onSelectDetailTab = databaseViewModel::setDetailTab,
                            onRefreshTables = databaseViewModel::refreshTables,
                            onOpenTable = databaseViewModel::openTable,
                            onCloseTable = databaseViewModel::closeTable,
                            onLoadPage = databaseViewModel::loadPage,
                            onInsertRow = databaseViewModel::insertRow,
                            onUpdateRow = databaseViewModel::updateRow,
                            onDeleteRow = databaseViewModel::deleteRow,
                            onSqlInputChange = databaseViewModel::updateSqlInput,
                            onExecuteSql = databaseViewModel::executeSql,
                            onDismissFeedback = databaseViewModel::clearFeedback
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketBottomNavBar(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSampColors.BackgroundBase)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = PocketSampColors.BorderSubtle)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
        ) {
            topDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavItemContent(
                    destination = dest,
                    selected = selected,
                    onClick = { onSelectRoute(dest.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PocketNavRail(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(PocketSampColors.BackgroundBase)
            .navigationBarsPadding()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .padding(vertical = PocketSampDimens.Space16)
        ) {
            topDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavItemContent(
                    destination = dest,
                    selected = selected,
                    onClick = { onSelectRoute(dest.route) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        VerticalDivider(color = PocketSampColors.BorderSubtle)
    }
}

@Composable
private fun NavItemContent(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(destination.labelRes)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = PocketSampDimens.Space8, horizontal = PocketSampDimens.Space4)
            .testTag(destination.testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .drawBehind {
                    if (selected) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PocketSampColors.GlowRadialAccent,
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.9f
                        )
                    }
                }
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = null,
                tint = if (selected) PocketSampColors.TextPrimary else PocketSampColors.TextSecondary,
                modifier = Modifier.size(PocketSampDimens.IconSize)
            )
        }
        Spacer(modifier = Modifier.size(PocketSampDimens.Space4))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) PocketSampColors.TextPrimary else PocketSampColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
