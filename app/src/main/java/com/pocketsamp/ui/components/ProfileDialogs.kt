package com.pocketsamp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketsamp.R
import com.pocketsamp.core.AppConstants
import com.pocketsamp.data.ServerProfile
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectBottomSheet(
    profiles: List<ServerProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onAddProfileClick: () -> Unit,
    onEditProfileClick: (ServerProfile) -> Unit,
    onDeleteProfileClick: (ServerProfile) -> Unit,
    onDismiss: () -> Unit
) {
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
                        bottom = PocketSampDimens.Space8
                    )
            ) {
                Text(
                    text = stringResource(R.string.profile_select_sheet_title),
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

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketSampColors.TextSecondary,
                    modifier = Modifier.padding(
                        horizontal = PocketSampDimens.Space20,
                        vertical = PocketSampDimens.Space16
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    itemsIndexed(profiles, key = { _, item -> item.id }) { index, profile ->
                        val isActive = profile.id == activeProfileId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PocketSampDimens.RowDetailMinHeight)
                                .clickable {
                                    onSelectProfile(profile.id)
                                    onDismiss()
                                }
                                .padding(
                                    start = PocketSampDimens.Space16,
                                    end = PocketSampDimens.Space8,
                                    top = PocketSampDimens.Space8,
                                    bottom = PocketSampDimens.Space8
                                )
                                .testTag("profile_row_${profile.id}")
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PocketSampColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = profile.folderPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PocketSampColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.profile_selected_cd),
                                    tint = PocketSampColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = PocketSampDimens.Space8)
                                )
                            }
                            IconButton(
                                onClick = { onEditProfileClick(profile) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_edit_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = PocketSampColors.TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onDeleteProfileClick(profile) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_delete_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = PocketSampColors.TextSecondary
                                )
                            }
                        }
                        if (index < profiles.lastIndex) {
                            HorizontalDivider(
                                color = PocketSampColors.BorderSubtle,
                                modifier = Modifier.padding(horizontal = PocketSampDimens.Space16)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(PocketSampDimens.Space12))
            PocketPrimaryButton(
                text = stringResource(R.string.profile_add),
                onClick = onAddProfileClick,
                icon = Icons.Outlined.Add,
                testTag = "profile_sheet_add_button",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PocketSampDimens.Space16)
            )
        }
    }
}

@Composable
fun ProfileEditorDialog(
    editingProfile: ServerProfile?,
    onSave: (
        id: String?,
        name: String,
        folderPath: String,
        executableName: String,
        lowPriorityCpu: Boolean,
        autoStartMariaDb: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(editingProfile) { mutableStateOf(editingProfile?.name.orEmpty()) }
    var folderPath by remember(editingProfile) { mutableStateOf(editingProfile?.folderPath.orEmpty()) }
    var executableName by remember(editingProfile) {
        mutableStateOf(editingProfile?.executableName ?: AppConstants.DEFAULT_SAMP_EXECUTABLE)
    }
    var lowPriorityCpu by remember(editingProfile) {
        mutableStateOf(editingProfile?.lowPriorityCpu ?: true)
    }
    var autoStartMariaDb by remember(editingProfile) {
        mutableStateOf(editingProfile?.autoStartMariaDb ?: false)
    }
    var showFolderPicker by remember { mutableStateOf(false) }

    if (showFolderPicker) {
        FolderBrowserDialog(
            initialPath = folderPath,
            onFolderSelected = { chosenPath ->
                folderPath = chosenPath
                if (name.isBlank()) {
                    name = chosenPath.substringAfterLast('/').ifBlank { "SA-MP Server" }
                }
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    val titleRes = if (editingProfile == null) {
        R.string.profile_create_title
    } else {
        R.string.profile_edit_title
    }

    PocketDialogContainer(
        title = stringResource(titleRes),
        onDismiss = onDismiss,
        secondaryButtonText = stringResource(R.string.action_cancel),
        onSecondaryClick = onDismiss,
        primaryButtonText = stringResource(R.string.action_save),
        onPrimaryClick = {
            onSave(
                editingProfile?.id,
                name,
                folderPath,
                executableName,
                lowPriorityCpu,
                autoStartMariaDb
            )
        },
        primaryEnabled = name.isNotBlank() && folderPath.isNotBlank(),
        secondaryTestTag = "profile_editor_cancel_button",
        primaryTestTag = "profile_editor_save_button"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PocketTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.profile_name_label),
                placeholder = stringResource(R.string.profile_name_hint),
                testTag = "profile_name_input"
            )

            Column(verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8)) {
                Text(
                    text = stringResource(R.string.profile_folder_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = PocketSampColors.TextSecondary
                )
                Text(
                    text = folderPath.ifBlank { stringResource(R.string.profile_folder_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (folderPath.isBlank()) {
                        PocketSampColors.TextTertiary
                    } else {
                        PocketSampColors.TextPrimary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                PocketSecondaryButton(
                    text = stringResource(R.string.profile_pick_folder),
                    onClick = { showFolderPicker = true },
                    icon = Icons.Outlined.FolderOpen,
                    testTag = "profile_pick_folder_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PocketTextField(
                value = executableName,
                onValueChange = { executableName = it },
                label = stringResource(R.string.profile_executable_label),
                placeholder = AppConstants.DEFAULT_SAMP_EXECUTABLE,
                testTag = "profile_executable_input"
            )

            PocketCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                PocketSettingToggleRow(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.config_low_priority_title),
                    description = stringResource(R.string.config_low_priority_desc),
                    checked = lowPriorityCpu,
                    onCheckedChange = { lowPriorityCpu = it },
                    testTag = "profile_low_priority_switch"
                )
                HorizontalDivider(
                    color = PocketSampColors.BorderSubtle,
                    modifier = Modifier.padding(horizontal = PocketSampDimens.Space16)
                )
                PocketSettingToggleRow(
                    icon = Icons.Outlined.Storage,
                    title = stringResource(R.string.config_auto_mariadb_title),
                    description = stringResource(R.string.config_auto_mariadb_desc),
                    checked = autoStartMariaDb,
                    onCheckedChange = { autoStartMariaDb = it },
                    testTag = "profile_auto_mariadb_switch"
                )
            }
        }
    }
}
