package com.pocketsamp.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketsamp.R
import com.pocketsamp.storage.FolderEntry
import com.pocketsamp.storage.StoragePermissionHelper
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FolderBrowserDialog(
    initialPath: String,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultRoot = remember { StoragePermissionHelper.getInitialBrowserPath() }
    var currentPath by remember(initialPath) {
        val candidate = initialPath.takeIf { it.isNotBlank() && File(it).isDirectory }
        mutableStateOf(candidate ?: defaultRoot)
    }
    var safError by remember { mutableStateOf<String?>(null) }
    val invalidSafMsg = stringResource(R.string.folder_picker_invalid_saf)

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val resolved = StoragePermissionHelper.resolveTreeUriToPosixPath(uri)
            if (resolved != null) {
                safError = null
                onFolderSelected(resolved)
            } else {
                safError = invalidSafMsg
            }
        }
    }

    val entries by produceState(initialValue = emptyList<FolderEntry>(), key1 = currentPath) {
        value = withContext(Dispatchers.IO) {
            StoragePermissionHelper.listChildFolders(currentPath)
        }
    }

    val parentPath = remember(currentPath) {
        val file = File(currentPath)
        file.parentFile?.takeIf { it.canRead() }?.absolutePath
    }

    PocketDialogContainer(
        title = stringResource(R.string.folder_picker_title),
        onDismiss = onDismiss,
        icon = Icons.Outlined.Folder,
        secondaryButtonText = stringResource(R.string.action_cancel),
        onSecondaryClick = onDismiss,
        primaryButtonText = stringResource(R.string.folder_picker_use_current),
        onPrimaryClick = { onFolderSelected(currentPath) },
        secondaryTestTag = "folder_picker_cancel_button",
        primaryTestTag = "folder_picker_confirm_button"
    ) {
        Text(
            text = currentPath,
            style = MaterialTheme.typography.bodyMedium,
            color = PocketSampColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (safError != null) {
            PocketFeedbackBanner(
                message = safError.orEmpty(),
                isError = true,
                onDismiss = { safError = null }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space8),
            modifier = Modifier.fillMaxWidth()
        ) {
            PocketSecondaryButton(
                text = stringResource(R.string.folder_picker_up),
                onClick = {
                    if (parentPath != null) {
                        safError = null
                        currentPath = parentPath
                    }
                },
                enabled = parentPath != null,
                icon = Icons.Outlined.ArrowUpward,
                testTag = "folder_picker_up_button",
                modifier = Modifier.weight(1f)
            )
            PocketSecondaryButton(
                text = stringResource(R.string.folder_picker_system_fallback),
                onClick = { safLauncher.launch(null) },
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                testTag = "folder_picker_saf_button",
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            color = PocketSampColors.ConsoleBackground,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(PocketSampRadii.Field),
            border = androidx.compose.foundation.BorderStroke(
                PocketSampDimens.BorderWidth,
                PocketSampColors.BorderSubtle
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 300.dp)
        ) {
            if (entries.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PocketSampDimens.Space24)
                ) {
                    Text(
                        text = stringResource(R.string.folder_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketSampColors.TextSecondary
                    )
                }
            } else {
                LazyColumn {
                    items(entries, key = { it.path }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PocketSampDimens.RowMinHeight)
                                .clickable {
                                    safError = null
                                    currentPath = entry.path
                                }
                                .padding(
                                    horizontal = PocketSampDimens.Space12,
                                    vertical = PocketSampDimens.Space8
                                )
                                .testTag("folder_entry_${entry.name}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = if (entry.hasServerCfg || entry.hasSampBinary) {
                                    PocketSampColors.AccentBorder
                                } else {
                                    PocketSampColors.TextSecondary
                                },
                                modifier = Modifier.size(PocketSampDimens.IconSize)
                            )
                            Spacer(modifier = Modifier.width(PocketSampDimens.Space12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PocketSampColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.hasServerCfg) {
                                    Text(
                                        text = stringResource(R.string.folder_picker_samp_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PocketSampColors.AccentBorder
                                    )
                                }
                            }
                            if (entry.hasSampBinary) {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = null,
                                    tint = PocketSampColors.StatusActive,
                                    modifier = Modifier.size(PocketSampDimens.SmallIconSize)
                                )
                            }
                        }
                        HorizontalDivider(color = PocketSampColors.BorderSubtle)
                    }
                }
            }
        }
    }
}
