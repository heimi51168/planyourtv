package app.orchard.tv.ui.policy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import app.orchard.tv.R
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow

/**
 * Local rule editor. A value only changes after OK enters the edit state, so a parent who wants
 * to return to the rail can press Left without accidentally changing a limit.
 */
@Composable
internal fun V6LocalPane(
    rules: LocalRules,
    quizEntryVisible: Boolean,
    sourceEntriesVisible: Boolean,
    sessionActive: Boolean,
    readOnly: Boolean,
    editingKey: String?,
    armedRemoveKey: String?,
    endArmed: Boolean,
    focusedTarget: ParentFocusTarget?,
    focusFor: (String) -> FocusRequester,
    bringFor: (String) -> BringIntoViewRequester,
    onFocusRow: (String) -> Unit,
    onFocusRail: () -> Unit,
    onActivateValue: (String) -> Unit,
    onAdjust: (String, Int) -> Unit,
    onToggleQuizEntry: () -> Unit,
    onToggleSourceEntries: () -> Unit,
    onRemoveWindow: (Boolean, Int) -> Unit,
    onOpenApps: () -> Unit,
    onOpenStudyApps: () -> Unit,
    onEndSession: () -> Unit,
) {
    val scroll = rememberScrollState()
    val focusKeys = v6LocalFocusKeys(rules, sessionActive, readOnly)
    val highlightedKey = (focusedTarget as? ParentFocusTarget.LocalRow)?.key

    fun upFor(key: String): (() -> Unit)? {
        val previous = focusKeys.getOrNull(focusKeys.indexOf(key) - 1) ?: return null
        return { onFocusRow(previous) }
    }

    fun downFor(key: String): (() -> Unit)? {
        val next = focusKeys.getOrNull(focusKeys.indexOf(key) + 1) ?: return null
        return { onFocusRow(next) }
    }

    @Composable
    fun WindowRows(weekday: Boolean, windows: List<LocalTimeWindow>) {
        val day = if (weekday) "wd" else "we"
        windows.forEachIndexed { index, window ->
            val multiple = windows.size > 1
            val startKey = "$day-$index-start"
            val endKey = "$day-$index-end"
            V6ValueRow(
                label = if (multiple) {
                    stringResource(R.string.policy_parent_window_n_start, index + 1)
                } else {
                    stringResource(R.string.policy_parent_window_start)
                },
                value = formatMinute(window.startMinute),
                editing = !readOnly && editingKey == startKey,
                canDecrease = !readOnly && window.startMinute > 0,
                canIncrease = !readOnly && window.startMinute + 15 <= window.endMinute - 15,
                focusRequester = focusFor(startKey),
                bringIntoViewRequester = bringFor(startKey),
                onUp = upFor(startKey),
                onDown = downFor(startKey),
                onLeftRail = onFocusRail,
                onActivate = { if (!readOnly) onActivateValue(startKey) },
                onDecrease = { onAdjust(startKey, -15) },
                onIncrease = { onAdjust(startKey, 15) },
                highlighted = highlightedKey == startKey,
            )
            Spacer(Modifier.height(6.dp))
            V6ValueRow(
                label = if (multiple) {
                    stringResource(R.string.policy_parent_window_n_end, index + 1)
                } else {
                    stringResource(R.string.policy_parent_window_end)
                },
                value = formatMinute(window.endMinute),
                editing = !readOnly && editingKey == endKey,
                canDecrease = !readOnly && window.endMinute - 15 >= window.startMinute + 15,
                canIncrease = !readOnly && window.endMinute < 1440,
                focusRequester = focusFor(endKey),
                bringIntoViewRequester = bringFor(endKey),
                onUp = upFor(endKey),
                onDown = downFor(endKey),
                onLeftRail = onFocusRail,
                onActivate = { if (!readOnly) onActivateValue(endKey) },
                onDecrease = { onAdjust(endKey, -15) },
                onIncrease = { onAdjust(endKey, 15) },
                highlighted = highlightedKey == endKey,
            )
            if (multiple && !readOnly) {
                Spacer(Modifier.height(6.dp))
                val removeKey = "$day-$index-remove"
                val removeLabel = if (armedRemoveKey == removeKey) {
                    stringResource(R.string.policy_v6_remove_confirm)
                } else {
                    stringResource(R.string.policy_parent_remove_window_n, index + 1)
                }
                PolicyActionRow(
                    label = removeLabel,
                    focusRequester = focusFor(removeKey),
                    onClick = { onRemoveWindow(weekday, index) },
                    onUp = upFor(removeKey),
                    onDown = downFor(removeKey),
                    onLeft = onFocusRail,
                    armed = armedRemoveKey == removeKey,
                    fontSize = 14.sp,
                    highlighted = highlightedKey == removeKey,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            // The app scope is independent of the timing windows, so it stays at the top.
            PolicySectionHeader(stringResource(R.string.policy_parent_section_apps))
            RuleHint(stringResource(R.string.policy_v6_apps_shared_hint))
            PolicyActionRow(
                label = stringResource(R.string.policy_parent_apps),
                focusRequester = focusFor("apps"),
                onClick = { if (!readOnly) onOpenApps() },
                onUp = upFor("apps"),
                onDown = downFor("apps"),
                onLeft = onFocusRail,
                trailing = if (rules.usesAppSelection) {
                    stringResource(R.string.policy_parent_apps_count, rules.effectiveAllowedPackages.size)
                } else {
                    stringResource(R.string.policy_parent_apps_all)
                },
                fontSize = 15.sp,
                highlighted = highlightedKey == "apps",
            )
            Spacer(Modifier.height(6.dp))
            PolicyActionRow(
                label = stringResource(R.string.policy_v6_study_apps),
                focusRequester = focusFor("study"),
                onClick = { if (!readOnly) onOpenStudyApps() },
                onUp = upFor("study"),
                onDown = downFor("study"),
                onLeft = onFocusRail,
                trailing = if (rules.hasStudyMode) {
                    stringResource(R.string.policy_parent_apps_count, rules.effectiveStudyPackages.size)
                } else {
                    stringResource(R.string.policy_v6_study_none)
                },
                fontSize = 15.sp,
                highlighted = highlightedKey == "study",
            )
            Spacer(Modifier.height(8.dp))

            // Sources are not apps, but they are another child-mode entry. The switch only
            // controls Aerial's own entries, as the hint says.
            PolicySectionHeader(stringResource(R.string.policy_v6_source_section))
            PolicySwitchRow(
                title = stringResource(R.string.policy_v6_source_entries),
                status = stringResource(
                    if (sourceEntriesVisible) {
                        R.string.policy_v6_source_entries_on
                    } else {
                        R.string.policy_v6_source_entries_off
                    },
                ),
                on = sourceEntriesVisible,
                focusRequester = focusFor("sources"),
                onClick = { if (!readOnly) onToggleSourceEntries() },
                onUp = upFor("sources"),
                onDown = downFor("sources"),
                onLeft = onFocusRail,
                highlighted = highlightedKey == "sources",
            )
            RuleHint(stringResource(R.string.policy_v6_source_entries_hint))
            Spacer(Modifier.height(8.dp))

            // Local timing is manual only. The hint explains how a segment starts; the quiz
            // switch below only shows/hides the dedicated lock-screen entry, and the quiz
            // itself stays available from entertainment apps.
            PolicySectionHeader(stringResource(R.string.policy_v6_activation_section))
            RuleHint(stringResource(R.string.policy_v6_activation_manual_hint))
            PolicySwitchRow(
                title = stringResource(R.string.policy_v6_quiz_challenge),
                status = stringResource(R.string.policy_v6_quiz_hint),
                on = quizEntryVisible,
                focusRequester = focusFor("quiz"),
                onClick = { if (!readOnly) onToggleQuizEntry() },
                onUp = upFor("quiz"),
                onDown = downFor("quiz"),
                onLeft = onFocusRail,
                highlighted = highlightedKey == "quiz",
            )
            Spacer(Modifier.height(8.dp))

            PolicySectionHeader(stringResource(R.string.policy_parent_section_time))
            if (rules.hasDefaultDayGrouping()) {
                RuleHint(stringResource(R.string.policy_parent_windows_schedule_hint))
                PolicySectionHeader(stringResource(R.string.policy_parent_weekday))
                WindowRows(true, rules.weekdayWindows)
                PolicySectionHeader(stringResource(R.string.policy_parent_weekend))
                WindowRows(false, rules.weekendWindows)
            } else {
                // The native pane only understands the default Monday–Friday / weekend split.
                // Writing through it would silently rewrite a custom group's schedule, so the
                // web page stays the editor and the TV only explains where to find it.
                RuleHint(stringResource(R.string.policy_parent_custom_schedule_hint))
            }

            PolicySectionHeader(stringResource(R.string.policy_parent_section_limits))
            // Manual mode has no daily quota, and its per-session length is the value the parent
            // types on the lock screen; only the rest interval still applies to manual sessions.
            V6ValueRow(
                label = stringResource(R.string.policy_parent_rest),
                value = stringResource(R.string.policy_parent_minutes, rules.restMinutes),
                editing = !readOnly && editingKey == "rest",
                canDecrease = !readOnly && rules.restMinutes > 0,
                canIncrease = !readOnly && rules.restMinutes < MAX_EDIT_REST_MINUTES,
                focusRequester = focusFor("rest"),
                bringIntoViewRequester = bringFor("rest"),
                onUp = upFor("rest"),
                onDown = downFor("rest"),
                onLeftRail = onFocusRail,
                onActivate = { if (!readOnly) onActivateValue("rest") },
                onDecrease = { onAdjust("rest", -5) },
                onIncrease = { onAdjust("rest", 5) },
                highlighted = highlightedKey == "rest",
            )
            RuleHint(stringResource(R.string.policy_parent_rest_hint))
            Spacer(Modifier.height(6.dp))
            // Each activation stores its own grace. It is a wall-clock deadline, not extra
            // usable time, so it stays a local rule even when synced.
            V6ValueRow(
                label = stringResource(R.string.policy_parent_screen_off_buffer),
                value = stringResource(R.string.policy_parent_minutes, rules.sessionScreenOffBufferMinutes),
                editing = !readOnly && editingKey == "buffer",
                canDecrease = !readOnly &&
                    rules.sessionScreenOffBufferMinutes > LocalRules.MIN_SESSION_BUFFER_MINUTES,
                canIncrease = !readOnly &&
                    rules.sessionScreenOffBufferMinutes < LocalRules.MAX_SESSION_BUFFER_MINUTES,
                focusRequester = focusFor("buffer"),
                bringIntoViewRequester = bringFor("buffer"),
                onUp = upFor("buffer"),
                onDown = downFor("buffer"),
                onLeftRail = onFocusRail,
                onActivate = { if (!readOnly) onActivateValue("buffer") },
                onDecrease = { onAdjust("buffer", -5) },
                onIncrease = { onAdjust("buffer", 5) },
                highlighted = highlightedKey == "buffer",
            )
            RuleHint(stringResource(R.string.policy_parent_screen_off_buffer_hint))

            if (sessionActive && !readOnly) {
                PolicySectionHeader(stringResource(R.string.policy_parent_section_usage))
                PolicyActionRow(
                    label = stringResource(
                        if (endArmed) {
                            R.string.policy_parent_arm_end_session
                        } else {
                            R.string.policy_parent_end_session
                        },
                    ),
                    focusRequester = focusFor("end-session"),
                    onClick = onEndSession,
                    onUp = upFor("end-session"),
                    onDown = downFor("end-session"),
                    onLeft = onFocusRail,
                    armed = endArmed,
                    fontSize = 14.sp,
                    highlighted = highlightedKey == "end-session",
                )
                Spacer(Modifier.height(8.dp))
            }
            // Read-only cloud rules have no editing keys, so the key hint (and a second
            // "edit on the phone" line) would only add noise at the bottom of the pane.
            if (!readOnly) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_v6_nav_hint),
                    style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** One-line explanation under a rule row; secondary text so the value stays dominant. */
@Composable
private fun RuleHint(text: String) {
    BasicText(
        text,
        style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun V6ValueRow(
    label: String,
    value: String,
    editing: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester?,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onLeftRail: () -> Unit,
    onActivate: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    highlighted: Boolean,
) {
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = onActivate,
        onUp = onUp,
        onDown = onDown,
        onLeft = {
            if (editing) {
                if (canDecrease) onDecrease()
            } else {
                onLeftRail()
            }
        },
        onRight = {
            if (editing && canIncrease) onIncrease()
        },
        bringIntoViewRequester = bringIntoViewRequester,
        highlighted = highlighted,
    ) { plate ->
        val labelColor = if (plate) Palette.OnFocus.copy(alpha = 0.72f) else Palette.SecondaryLabel
        val valueColor = if (plate) Palette.OnFocus else Palette.Label
        BasicText(
            label,
            style = Type.Body.copy(fontSize = 15.sp, color = labelColor),
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            BasicText(
                text = when {
                    editing && canDecrease && canIncrease -> "‹ $value ›"
                    editing && canDecrease -> "‹ $value"
                    editing && canIncrease -> "$value ›"
                    else -> value
                },
                style = Type.Headline.copy(
                    fontSize = 16.sp,
                    color = if (editing) Palette.Orange else valueColor,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                modifier = Modifier.width(190.dp),
            )
        }
    }
}

/**
 * The native TV pane edits only the default two-group schedule. A custom grouping (added on the
 * web page) must not be rewritten through the weekday/weekend projection.
 */
internal fun LocalRules.hasDefaultDayGrouping(): Boolean =
    dayGroups.size == 2 &&
        dayGroups.any { it.days == listOf(1, 2, 3, 4, 5) } &&
        dayGroups.any { it.days == listOf(6, 7) }

internal fun v6LocalFocusKeys(
    rules: LocalRules,
    sessionActive: Boolean,
    readOnly: Boolean = false,
): List<String> = buildList {
    // Shared app and source entries first, then the manual timing rows.
    add("apps")
    add("study")
    add("sources")
    add("quiz")
    if (rules.hasDefaultDayGrouping()) {
        rules.weekdayWindows.forEachIndexed { index, _ ->
            add("wd-$index-start")
            add("wd-$index-end")
            if (!readOnly && rules.weekdayWindows.size > 1) add("wd-$index-remove")
        }
        rules.weekendWindows.forEachIndexed { index, _ ->
            add("we-$index-start")
            add("we-$index-end")
            if (!readOnly && rules.weekendWindows.size > 1) add("we-$index-remove")
        }
    }
    // Manual time takes its segment length from the lock-screen entry, so there is no
    // daily/session editor here; only the rest interval and screen-off buffer remain.
    add("rest")
    add("buffer")
    if (sessionActive && !readOnly) add("end-session")
}

internal fun isV6ValueKey(key: String): Boolean =
    key.endsWith("-start") || key.endsWith("-end") ||
        key == "rest" || key == "buffer"

internal fun v6ValueStep(key: String): Int =
    if (key == "rest" || key == "buffer") 5 else 15
