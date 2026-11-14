package com.pocketsamp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketsamp.R
import com.pocketsamp.config.ServerCfgData
import com.pocketsamp.ui.components.PocketCard
import com.pocketsamp.ui.components.PocketFeedbackBanner
import com.pocketsamp.ui.components.PocketPrimaryButton
import com.pocketsamp.ui.components.PocketScreenBackground
import com.pocketsamp.ui.components.PocketSettingToggleRow
import com.pocketsamp.ui.components.PocketTextField
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii
import com.pocketsamp.viewmodel.ConfigUiState

@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onReload: () -> Unit,
    onSetRawMode: (Boolean) -> Unit,
    onUpdateCfg: ((ServerCfgData) -> ServerCfgData) -> Unit,
    onSaveConfig: () -> Unit,
    onSetLowPriorityCpu: (Boolean) -> Unit,
    onSetAutoStartMariaDb: (Boolean) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.nav_config),
                        style = MaterialTheme.typography.headlineSmall,
                        color = PocketSampColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.activeProfile != null) {
                        IconButton(
                            onClick = onReload,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("config_reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = PocketSampColors.TextSecondary
                            )
                        }
                    }
                }

                val profile = uiState.activeProfile
                if (profile == null) {
                    PocketCard {
                        Text(
                            text = stringResource(R.string.config_no_profile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary
                        )
                    }
                    return@Column
                }

                if (uiState.feedbackMessage != null) {
                    PocketFeedbackBanner(
                        message = uiState.feedbackMessage,
                        isError = uiState.feedbackIsError,
                        onDismiss = onDismissFeedback
                    )
                }

                if (uiState.loadError != null) {
                    PocketFeedbackBanner(
                        message = uiState.loadError,
                        isError = true,
                        onDismiss = onDismissFeedback
                    )
                }

                uiState.folderInspection?.let { inspection ->
                    val noneLabel = stringResource(R.string.config_none_detected)
                    val amxText = if (inspection.gamemodeFiles.isEmpty()) {
                        noneLabel
                    } else {
                        inspection.gamemodeFiles.joinToString(", ")
                    }
                    val pluginsText = if (inspection.pluginFiles.isEmpty()) {
                        noneLabel
                    } else {
                        inspection.pluginFiles.joinToString(", ")
                    }
                    PocketCard {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = PocketSampColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.config_detected_assets,
                                amxText,
                                pluginsText
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PocketSampColors.TextSecondary,
                            modifier = Modifier.padding(top = PocketSampDimens.Space4)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModeTabButton(
                        text = stringResource(R.string.config_mode_structured),
                        selected = !uiState.isRawMode,
                        onClick = { onSetRawMode(false) },
                        testTag = "config_mode_structured_tab",
                        modifier = Modifier.weight(1f)
                    )
                    ModeTabButton(
                        text = stringResource(R.string.config_mode_raw),
                        selected = uiState.isRawMode,
                        onClick = { onSetRawMode(true) },
                        testTag = "config_mode_raw_tab",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(R.string.config_section_server),
                    style = MaterialTheme.typography.labelLarge,
                    color = PocketSampColors.TextSecondary
                )

                PocketCard {
                    if (uiState.isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PocketSampDimens.Space16)
                        ) {
                            CircularProgressIndicator(
                                color = PocketSampColors.AccentBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(PocketSampDimens.IconSize)
                            )
                        }
                    } else if (uiState.isRawMode) {
                        PocketTextField(
                            value = uiState.cfgData.rawContent,
                            onValueChange = { text -> onUpdateCfg { it.copy(rawContent = text) } },
                            label = stringResource(R.string.config_raw_label),
                            singleLine = false,
                            minLines = 10,
                            maxLines = 22,
                            testTag = "config_raw_input"
                        )
                    } else {
                        StructuredCfgFields(
                            cfgData = uiState.cfgData,
                            onUpdateCfg = onUpdateCfg
                        )
                    }

                    PocketPrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSaveConfig,
                        enabled = !uiState.isSaving && !uiState.isLoading,
                        icon = Icons.Outlined.Save,
                        testTag = "config_save_button",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PocketSampDimens.Space12)
                    )
                }

                Text(
                    text = stringResource(R.string.config_section_tuning),
                    style = MaterialTheme.typography.labelLarge,
                    color = PocketSampColors.TextSecondary
                )

                PocketCard(contentPadding = PaddingValues(0.dp)) {
                    PocketSettingToggleRow(
                        icon = Icons.Outlined.Memory,
                        title = stringResource(R.string.config_low_priority_title),
                        description = stringResource(R.string.config_low_priority_desc),
                        checked = profile.lowPriorityCpu,
                        onCheckedChange = onSetLowPriorityCpu,
                        testTag = "config_low_priority_switch"
                    )
                    HorizontalDivider(
                        color = PocketSampColors.BorderSubtle,
                        modifier = Modifier.padding(horizontal = PocketSampDimens.Space16)
                    )
                    PocketSettingToggleRow(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.config_auto_mariadb_title),
                        description = stringResource(R.string.config_auto_mariadb_desc),
                        checked = profile.autoStartMariaDb,
                        onCheckedChange = onSetAutoStartMariaDb,
                        testTag = "config_auto_mariadb_switch"
                    )
                }
            }
        }
    }
}

@Composable
private fun StructuredCfgFields(
    cfgData: ServerCfgData,
    onUpdateCfg: ((ServerCfgData) -> ServerCfgData) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)) {
        PocketTextField(
            value = cfgData.hostname,
            onValueChange = { v -> onUpdateCfg { it.copy(hostname = v) } },
            label = stringResource(R.string.config_hostname),
            testTag = "cfg_hostname_input"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
            modifier = Modifier.fillMaxWidth()
        ) {
            PocketTextField(
                value = cfgData.port,
                onValueChange = { v -> onUpdateCfg { it.copy(port = v.filter { ch -> ch.isDigit() }) } },
                label = stringResource(R.string.config_port),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "cfg_port_input",
                modifier = Modifier.weight(1f)
            )
            PocketTextField(
                value = cfgData.maxPlayers,
                onValueChange = { v -> onUpdateCfg { it.copy(maxPlayers = v.filter { ch -> ch.isDigit() }) } },
                label = stringResource(R.string.config_maxplayers),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "cfg_maxplayers_input",
                modifier = Modifier.weight(1f)
            )
        }
        PocketTextField(
            value = cfgData.rconPassword,
            onValueChange = { v -> onUpdateCfg { it.copy(rconPassword = v) } },
            label = stringResource(R.string.config_rcon_password),
            testTag = "cfg_rcon_password_input"
        )
        PocketTextField(
            value = cfgData.password,
            onValueChange = { v -> onUpdateCfg { it.copy(password = v) } },
            label = stringResource(R.string.config_password),
            testTag = "cfg_password_input"
        )
        PocketTextField(
            value = cfgData.gamemode0,
            onValueChange = { v -> onUpdateCfg { it.copy(gamemode0 = v) } },
            label = stringResource(R.string.config_gamemode0),
            testTag = "cfg_gamemode0_input"
        )
        PocketTextField(
            value = cfgData.plugins,
            onValueChange = { v -> onUpdateCfg { it.copy(plugins = v) } },
            label = stringResource(R.string.config_plugins),
            testTag = "cfg_plugins_input"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
            modifier = Modifier.fillMaxWidth()
        ) {
            PocketTextField(
                value = cfgData.sleepMs,
                onValueChange = { v -> onUpdateCfg { it.copy(sleepMs = v.filter { ch -> ch.isDigit() }) } },
                label = stringResource(R.string.config_sleep),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "cfg_sleep_input",
                modifier = Modifier.weight(1f)
            )
            PocketTextField(
                value = cfgData.streamRateMs,
                onValueChange = { v -> onUpdateCfg { it.copy(streamRateMs = v.filter { ch -> ch.isDigit() }) } },
                label = stringResource(R.string.config_stream_rate),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "cfg_stream_rate_input",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.config_lanmode),
                style = MaterialTheme.typography.titleMedium,
                color = PocketSampColors.TextPrimary
            )
            Switch(
                checked = cfgData.lanMode,
                onCheckedChange = { checked -> onUpdateCfg { it.copy(lanMode = checked) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PocketSampColors.TextPrimary,
                    checkedTrackColor = PocketSampColors.AccentBlue,
                    uncheckedThumbColor = PocketSampColors.TextSecondary,
                    uncheckedTrackColor = PocketSampColors.SurfaceInteractive
                ),
                modifier = Modifier.testTag("cfg_lanmode_switch")
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.config_announce),
                style = MaterialTheme.typography.titleMedium,
                color = PocketSampColors.TextPrimary
            )
            Switch(
                checked = cfgData.announce,
                onCheckedChange = { checked -> onUpdateCfg { it.copy(announce = checked) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PocketSampColors.TextPrimary,
                    checkedTrackColor = PocketSampColors.AccentBlue,
                    uncheckedThumbColor = PocketSampColors.TextSecondary,
                    uncheckedTrackColor = PocketSampColors.SurfaceInteractive
                ),
                modifier = Modifier.testTag("cfg_announce_switch")
            )
        }
    }
}

@Composable
private fun ModeTabButton(
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
