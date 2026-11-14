package com.pocketsamp.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketsamp.R
import com.pocketsamp.server.ServiceStatus
import com.pocketsamp.ui.theme.PocketSampColors
import com.pocketsamp.ui.theme.PocketSampDimens
import com.pocketsamp.ui.theme.PocketSampMotion
import com.pocketsamp.ui.theme.PocketSampRadii

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

@Composable
fun PocketScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                PocketSampColors.BackgroundTop,
                PocketSampColors.BackgroundBase,
                PocketSampColors.BackgroundBase
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        content()
    }
}

@Composable
fun PocketCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(PocketSampDimens.Space16),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed && !reduceMotion) {
            PocketSampMotion.PressScale
        } else {
            1f
        },
        animationSpec = PocketSampMotion.fastTween(),
        label = "card_press_scale"
    )

    val shape = RoundedCornerShape(PocketSampRadii.Card)
    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(PocketSampColors.SurfaceCard)
        .background(PocketSampColors.CardOverlay)
        .border(PocketSampDimens.BorderWidth, PocketSampColors.BorderSubtle, shape)
        .let { m ->
            if (onClick != null) {
                m.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                m
            }
        }
        .padding(contentPadding)

    Column(
        modifier = baseModifier,
        content = content
    )
}

@Composable
fun PocketPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed && !reduceMotion) PocketSampMotion.PressScale else 1f,
        animationSpec = PocketSampMotion.fastTween(),
        label = "primary_btn_scale"
    )
    val haptic = LocalHapticFeedback.current

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(PocketSampRadii.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = PocketSampColors.AccentBlue,
            contentColor = PocketSampColors.TextPrimary,
            disabledContainerColor = PocketSampColors.SurfaceInteractive,
            disabledContentColor = PocketSampColors.TextTertiary
        ),
        border = BorderStroke(
            PocketSampDimens.BorderWidth,
            if (enabled) PocketSampColors.AccentBorder.copy(alpha = 0.45f) else PocketSampColors.BorderSubtle
        ),
        contentPadding = PaddingValues(
            horizontal = PocketSampDimens.Space16,
            vertical = PocketSampDimens.Space12
        ),
        modifier = modifier
            .heightIn(min = PocketSampDimens.MinTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(PocketSampDimens.SmallIconSize)
            )
            Spacer(modifier = Modifier.width(PocketSampDimens.Space8))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PocketSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed && !reduceMotion) PocketSampMotion.PressScale else 1f,
        animationSpec = PocketSampMotion.fastTween(),
        label = "secondary_btn_scale"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(PocketSampRadii.Button),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = PocketSampColors.SurfaceInteractive,
            contentColor = PocketSampColors.TextPrimary,
            disabledContainerColor = PocketSampColors.SurfaceCard,
            disabledContentColor = PocketSampColors.TextTertiary
        ),
        border = BorderStroke(PocketSampDimens.BorderWidth, PocketSampColors.BorderSubtle),
        contentPadding = PaddingValues(
            horizontal = PocketSampDimens.Space16,
            vertical = PocketSampDimens.Space12
        ),
        modifier = modifier
            .heightIn(min = PocketSampDimens.MinTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(PocketSampDimens.SmallIconSize)
            )
            Spacer(modifier = Modifier.width(PocketSampDimens.Space8))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ServiceStatusBadge(
    status: ServiceStatus,
    label: String,
    modifier: Modifier = Modifier
) {
    val (tint, icon) = when (status) {
        ServiceStatus.RUNNING -> PocketSampColors.StatusActive to Icons.Outlined.PlayCircleOutline
        ServiceStatus.STARTING, ServiceStatus.STOPPING -> PocketSampColors.AccentBlue to Icons.Outlined.HourglassTop
        ServiceStatus.ERROR -> PocketSampColors.StatusWarning to Icons.Outlined.ErrorOutline
        ServiceStatus.STOPPED -> PocketSampColors.StatusNeutral to Icons.Outlined.PauseCircleOutline
    }

    Surface(
        shape = RoundedCornerShape(PocketSampRadii.Pill),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(PocketSampDimens.BorderWidth, tint.copy(alpha = 0.42f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = PocketSampDimens.Space12,
                vertical = PocketSampDimens.Space4
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(PocketSampDimens.SmallIconSize)
            )
            Spacer(modifier = Modifier.width(PocketSampDimens.Space4))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = PocketSampColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PocketFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isError) PocketSampColors.StatusWarning else PocketSampColors.StatusActive
    val icon = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle
    Surface(
        shape = RoundedCornerShape(PocketSampRadii.Field),
        color = borderColor.copy(alpha = 0.12f),
        border = BorderStroke(PocketSampDimens.BorderWidth, borderColor.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = PocketSampDimens.Space12,
                top = PocketSampDimens.Space8,
                bottom = PocketSampDimens.Space8,
                end = if (onDismiss != null) PocketSampDimens.Space4 else PocketSampDimens.Space12
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(PocketSampDimens.IconSize)
            )
            Spacer(modifier = Modifier.width(PocketSampDimens.Space12))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = PocketSampColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun PocketSettingToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PocketSampDimens.RowDetailMinHeight)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(
                horizontal = PocketSampDimens.Space16,
                vertical = PocketSampDimens.Space12
            )
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PocketSampColors.TextSecondary,
            modifier = Modifier.size(PocketSampDimens.IconSize)
        )
        Spacer(modifier = Modifier.width(PocketSampDimens.Space16))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PocketSampColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = PocketSampColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(PocketSampDimens.Space12))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PocketSampColors.TextPrimary,
                checkedTrackColor = PocketSampColors.AccentBlue,
                uncheckedThumbColor = PocketSampColors.TextSecondary,
                uncheckedTrackColor = PocketSampColors.SurfaceInteractive,
                uncheckedBorderColor = PocketSampColors.BorderStrong
            )
        )
    }
}

@Composable
fun PocketNavigationRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PocketSampDimens.RowMinHeight)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(
                horizontal = PocketSampDimens.Space16,
                vertical = PocketSampDimens.Space12
            )
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PocketSampColors.TextSecondary,
            modifier = Modifier.size(PocketSampDimens.IconSize)
        )
        Spacer(modifier = Modifier.width(PocketSampDimens.Space16))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PocketSampColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(PocketSampDimens.Space8))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = PocketSampColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(PocketSampDimens.Space4))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PocketSampColors.TextSecondary
        )
    }
}

@Composable
fun PocketTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        placeholder = if (placeholder != null) {
            {
                Text(
                    text = placeholder,
                    color = PocketSampColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(PocketSampRadii.Field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PocketSampColors.TextPrimary,
            unfocusedTextColor = PocketSampColors.TextPrimary,
            focusedContainerColor = PocketSampColors.SurfaceInteractive.copy(alpha = 0.65f),
            unfocusedContainerColor = PocketSampColors.SurfaceInteractive.copy(alpha = 0.45f),
            focusedBorderColor = PocketSampColors.AccentBlue,
            unfocusedBorderColor = PocketSampColors.BorderSubtle,
            focusedLabelColor = PocketSampColors.AccentBorder,
            unfocusedLabelColor = PocketSampColors.TextSecondary,
            cursorColor = PocketSampColors.AccentBlue
        ),
        modifier = modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
    )
}

@Composable
fun PocketDialogContainer(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryTestTag: String? = null,
    primaryTestTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(PocketSampColors.ScrimDark)
                .padding(PocketSampDimens.Space16)
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = if (reduceMotion) {
                    fadeIn(PocketSampMotion.fastTween())
                } else {
                    fadeIn(PocketSampMotion.standardTween()) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = PocketSampMotion.standardTween()
                    )
                },
                exit = if (reduceMotion) {
                    fadeOut(PocketSampMotion.fastTween())
                } else {
                    fadeOut(PocketSampMotion.fastTween()) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = PocketSampMotion.fastTween()
                    )
                }
            ) {
                val shape = RoundedCornerShape(PocketSampRadii.Sheet)
                Surface(
                    shape = shape,
                    color = PocketSampColors.SurfaceOverlay,
                    border = BorderStroke(PocketSampDimens.BorderWidth, PocketSampColors.BorderStrong),
                    modifier = modifier
                        .widthIn(max = PocketSampDimens.MaxContentWidth)
                        .fillMaxWidth()
                        .clip(shape)
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
                ) {
                    Column(
                        modifier = Modifier.padding(PocketSampDimens.Space20),
                        verticalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PocketSampDimens.Space24)
                            ) {
                                if (icon != null) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = PocketSampColors.TextPrimary,
                                        modifier = Modifier.size(PocketSampDimens.IconSize)
                                    )
                                    Spacer(modifier = Modifier.size(PocketSampDimens.Space8))
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PocketSampColors.TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = PocketSampColors.TextSecondary
                                )
                            }
                        }

                        content()

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PocketSampDimens.Space12),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PocketSecondaryButton(
                                text = secondaryButtonText,
                                onClick = onSecondaryClick,
                                testTag = secondaryTestTag,
                                modifier = Modifier.weight(1f)
                            )
                            PocketPrimaryButton(
                                text = primaryButtonText,
                                onClick = onPrimaryClick,
                                enabled = primaryEnabled,
                                testTag = primaryTestTag,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
