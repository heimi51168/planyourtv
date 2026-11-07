package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.RemotePolicy
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionState
import java.util.Locale

@Composable
internal fun V6BindPane(
    deviceNo: String,
    guide: CloudGuideState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // The parent scans the mini program first, then scans the TV code from inside it.
            // The wide separator between the two plates makes it unlikely that one camera frame
            // comfortably contains both codes.
            BindStep(
                index = "1",
                title = stringResource(R.string.policy_parent_bind_mini),
                description = stringResource(R.string.policy_parent_bind_mini_caption),
                hint = stringResource(R.string.policy_parent_bind_tv_caption),
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.policy_mini_program_qr),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            BindStepSeparator()
            Spacer(Modifier.height(16.dp))

            BindStep(
                index = "2",
                title = stringResource(R.string.policy_parent_bind_tv),
                description = stringResource(R.string.policy_parent_bind_step2),
                details = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BindInfoRow(
                            label = stringResource(R.string.policy_bind_device_id),
                            value = displayDeviceNo(deviceNo),
                        )
                        guide.pairCode?.let { code ->
                            BindPairCodeRow(
                                label = stringResource(R.string.policy_bind_pair_code),
                                code = code.chunked(3).joinToString(" "),
                                hint = stringResource(R.string.policy_bind_pair_code_hint),
                            )
                        }
                        BindGuideStatus(guide.status)
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (guide.qr != null) {
                        Image(
                            bitmap = guide.qr,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        BasicText(
                            stringResource(
                                if (guide.status == CloudGuideStatus.RETRYING) {
                                    R.string.policy_bind_retrying
                                } else {
                                    R.string.policy_bind_qr_loading
                                },
                            ),
                            style = Type.Caption.copy(
                                fontSize = 11.sp,
                                color = Palette.OnFocus,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            BasicText(
                stringResource(R.string.policy_v6_bind_note),
                style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BindStep(
    index: String,
    title: String,
    description: String,
    hint: String? = null,
    details: (@Composable () -> Unit)? = null,
    qr: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        qr()
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            BindStepHeader(index, title)
            Spacer(Modifier.height(6.dp))
            BasicText(
                description,
                style = Type.Body.copy(fontSize = 13.sp, color = Palette.Label),
            )
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                BasicText(
                    hint,
                    style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
                )
            }
            details?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
private fun BindStepHeader(index: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Fill),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                index,
                style = Type.Caption.copy(fontSize = 12.sp, color = Palette.Label),
            )
        }
        Spacer(Modifier.width(8.dp))
        BasicText(title, style = Type.Headline.copy(fontSize = 15.sp))
    }
}

@Composable
private fun BindStepSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Palette.Hairline),
        )
        BasicText(
            "↓",
            modifier = Modifier.padding(horizontal = 14.dp),
            style = Type.Caption.copy(fontSize = 13.sp, color = Palette.TertiaryLabel),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Palette.Hairline),
        )
    }
}

@Composable
private fun BindInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            style = Type.Caption.copy(fontSize = 12.sp, color = Palette.SecondaryLabel),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(value, style = Type.Body.copy(fontSize = 13.sp))
    }
}

@Composable
private fun BindPairCodeRow(label: String, code: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                label,
                style = Type.Caption.copy(fontSize = 12.sp, color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(code, style = Type.Title.copy(fontSize = 17.sp))
        }
        BasicText(
            hint,
            style = Type.Caption.copy(fontSize = 10.sp, color = Palette.SecondaryLabel),
        )
    }
}

@Composable
private fun BindGuideStatus(status: CloudGuideStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when (status) {
                        CloudGuideStatus.RETRYING -> Palette.Orange
                        CloudGuideStatus.WAITING -> Palette.Green
                        CloudGuideStatus.SUCCESS -> Palette.Green
                        CloudGuideStatus.CONNECTING -> Palette.SecondaryLabel
                    },
                ),
        )
        Spacer(Modifier.width(7.dp))
        BasicText(
            stringResource(
                when (status) {
                    CloudGuideStatus.CONNECTING -> R.string.policy_bind_connecting
                    CloudGuideStatus.WAITING -> R.string.policy_bind_waiting
                    CloudGuideStatus.RETRYING -> R.string.policy_bind_retrying
                    CloudGuideStatus.SUCCESS -> R.string.policy_bind_success
                },
            ),
            style = Type.Caption.copy(fontSize = 12.sp, color = Palette.SecondaryLabel),
        )
    }
}

@Composable
internal fun V6MigrationPane(
    busy: Boolean,
    error: Int?,
    focused: ParentFocusTarget?,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onFocusRail: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            MigrationStep(
                index = "1",
                label = stringResource(R.string.policy_v6_migrate_step_bind),
                state = MigrationStepState.DONE,
            )
            MigrationStep(
                index = "2",
                label = stringResource(R.string.policy_v6_migrate_step_upload),
                state = if (error != null) MigrationStepState.ERROR else MigrationStepState.ACTIVE,
            )
            if (error != null) {
                BasicText(
                    stringResource(error),
                    style = Type.Caption.copy(fontSize = 12.sp, color = Palette.Red),
                    modifier = Modifier.padding(start = 34.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            MigrationStep(
                index = "3",
                label = stringResource(R.string.policy_v6_migrate_step_done),
                state = MigrationStepState.WAITING,
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_v6_migrate_body),
                style = Type.Body.copy(fontSize = 13.sp, color = Palette.Label),
            )
        }
        Spacer(Modifier.height(6.dp))
        PolicyActionRow(
            label = stringResource(R.string.policy_v6_migrate_retry),
            focusRequester = retryFocus,
            onClick = onRetry,
            onLeft = onFocusRail,
            enabled = !busy,
            primary = true,
            fontSize = 14.sp,
            highlighted = focused == ParentFocusTarget.MigrationRetry,
        )
    }
}

private enum class MigrationStepState { DONE, ACTIVE, WAITING, ERROR }

@Composable
private fun MigrationStep(index: String, label: String, state: MigrationStepState) {
    val color = when (state) {
        MigrationStepState.DONE -> Palette.Green
        MigrationStepState.ACTIVE -> Palette.Orange
        MigrationStepState.ERROR -> Palette.Red
        MigrationStepState.WAITING -> Palette.SecondaryLabel
    }
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                when (state) {
                    MigrationStepState.DONE -> "✓"
                    MigrationStepState.ACTIVE -> "●"
                    MigrationStepState.ERROR -> "!"
                    MigrationStepState.WAITING -> index
                },
                style = Type.Caption.copy(fontSize = 12.sp, color = color),
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            label,
            style = Type.Body.copy(
                fontSize = 14.sp,
                color = if (state == MigrationStepState.WAITING) Palette.SecondaryLabel else Palette.Label,
            ),
        )
    }
}

@Composable
internal fun V6WaitConfigPane(
    focused: ParentFocusTarget?,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onFocusRail: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BasicText(
            stringResource(R.string.policy_v6_wait_config_body),
            style = Type.Body.copy(fontSize = 14.sp, color = Palette.Label),
        )
        Spacer(Modifier.weight(1f))
        PolicyActionRow(
            label = stringResource(R.string.policy_v6_retry),
            focusRequester = retryFocus,
            onClick = onRetry,
            onLeft = onFocusRail,
            fontSize = 14.sp,
            highlighted = focused == ParentFocusTarget.WaitConfigRetry,
        )
    }
}

@Composable
internal fun V6SummaryPane(
    state: PolicyUiState,
    remote: RemotePolicy,
    focused: ParentFocusTarget?,
    readFocus: FocusRequester,
    onFocusRail: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        if (remote.mode == RemotePolicyMode.REDEEM) {
            SummaryRow(
                label = stringResource(R.string.policy_v6_mode),
                value = stringResource(R.string.policy_v6_mode_redeem),
            )
            SummaryRow(
                label = stringResource(R.string.policy_v6_today_usage),
                value = when {
                    state.sessionState == SessionState.ACTIVE ->
                        stringResource(
                            R.string.policy_v6_redeem_active,
                            (state.effectiveRemainingSeconds.coerceAtLeast(60L) / 60L).toInt(),
                        )
                    state.sessionState == SessionState.PENDING ->
                        stringResource(R.string.policy_parent_status_pending)
                    else -> stringResource(R.string.policy_v6_redeem_none)
                },
            )
            SummaryRow(
                label = stringResource(R.string.policy_v6_rule_time),
                value = stringResource(R.string.policy_v6_rule_template_saved),
            )
        } else {
            SummaryRow(
                label = stringResource(R.string.policy_v6_mode),
                value = stringResource(R.string.policy_v6_mode_rule),
            )
            // Read-only mirror of the local editor: apps first, then the timing switch and the
            // active set's rows. The parent must change them on the phone.
            SummaryRow(
                label = stringResource(R.string.policy_v6_apps),
                value = if (remote.rules.usesAppSelection) {
                    stringResource(
                        R.string.policy_v6_apps_count,
                        remote.rules.effectiveAllowedPackages.size,
                    )
                } else {
                    stringResource(R.string.policy_v6_apps_all)
                },
            )
            SummaryRow(
                label = stringResource(R.string.policy_v6_study_apps),
                value = if (remote.rules.hasStudyMode) {
                    stringResource(
                        R.string.policy_v6_apps_count,
                        remote.rules.effectiveStudyPackages.size,
                    )
                } else {
                    stringResource(R.string.policy_v6_study_none)
                },
            )
            SummaryRow(
                label = stringResource(R.string.policy_v6_activation),
                value = stringResource(
                    if (state.activation == Activation.MANUAL) {
                        R.string.policy_v6_activation_manual
                    } else {
                        R.string.policy_v6_activation_auto
                    },
                ),
            )
            val dailyLimit = remote.rules.autoDailyLimitMinutes
            val usedMinutes = (state.dailyUsedSeconds / 60L).toInt().coerceAtLeast(0)
            SummaryRow(
                label = stringResource(R.string.policy_v6_today_usage),
                value = if (state.activation == Activation.MANUAL) {
                    stringResource(R.string.policy_manual_used_today, usedMinutes)
                } else {
                    stringResource(R.string.policy_v6_usage_value, usedMinutes, dailyLimit) +
                        " · " +
                        stringResource(
                            R.string.policy_v6_remaining,
                            (dailyLimit - usedMinutes).coerceAtLeast(0),
                        )
                },
            )
            SummaryRow(
                label = stringResource(R.string.policy_parent_weekday),
                value = windowsLabel(remote.rules.weekdayWindows),
            )
            SummaryRow(
                label = stringResource(R.string.policy_parent_weekend),
                value = windowsLabel(remote.rules.weekendWindows),
            )
            SummaryRow(
                // Manual segments take their length from the parent's start command, so the
                // session cap is not part of that mode's effective policy. Rest still applies.
                label = stringResource(
                    if (state.activation == Activation.MANUAL) {
                        R.string.policy_parent_rest
                    } else {
                        R.string.policy_v6_session_rest
                    },
                ),
                value = if (state.activation == Activation.MANUAL) {
                    stringResource(R.string.policy_parent_minutes, remote.rules.restMinutes)
                } else {
                    stringResource(R.string.policy_parent_minutes, remote.rules.sessionLimitMinutes) +
                        " / " +
                        stringResource(R.string.policy_parent_minutes, remote.rules.restMinutes)
                },
            )
            SummaryRow(
                label = stringResource(R.string.policy_parent_screen_off_buffer),
                value = stringResource(
                    R.string.policy_parent_minutes,
                    remote.rules.sessionScreenOffBufferMinutes,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        PolicyActionRow(
            label = stringResource(R.string.policy_v6_phone_only),
            focusRequester = readFocus,
            onClick = {},
            onLeft = onFocusRail,
            centered = true,
            fontSize = 13.sp,
            highlighted = focused == ParentFocusTarget.SummaryRead,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            label,
            style = Type.Body.copy(fontSize = 14.sp, color = Palette.SecondaryLabel),
            modifier = Modifier.width(150.dp),
        )
        BasicText(
            value,
            style = Type.Body.copy(fontSize = 14.sp, color = Palette.Label),
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun windowsLabel(windows: List<LocalTimeWindow>): String =
    windows.joinToString("  ") { "${formatMinute(it.startMinute)}–${formatMinute(it.endMinute)}" }

internal fun formatMinute(minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

/**
 * Display-only masking: the parent only needs enough of the public deviceNo to recognize
 * the TV, so show its first six characters in uppercase. The real deviceNo still goes into
 * the QR payload and every cloud request; only the on-screen label is shortened.
 */
internal fun displayDeviceNo(deviceNo: String): String =
    deviceNo.take(6).uppercase(Locale.ROOT)

internal fun handoffLabel(result: PolicyModeCoordinator.HandoffResult): Int? = when (result) {
    PolicyModeCoordinator.HandoffResult.Success,
    PolicyModeCoordinator.HandoffResult.NotNeeded,
    -> null
    PolicyModeCoordinator.HandoffResult.NeedPassword -> R.string.policy_v6_migrate_need_pin
    PolicyModeCoordinator.HandoffResult.NoRules -> R.string.policy_v6_migrate_no_rules
    PolicyModeCoordinator.HandoffResult.Busy -> null
    is PolicyModeCoordinator.HandoffResult.Failed -> R.string.policy_v6_migrate_failed
}
