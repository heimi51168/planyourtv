package app.orchard.tv.ui.policy

/** Logical focus targets. Compose focus search is never used to move between them. */
internal sealed interface ParentFocusTarget {
    data object Switch : ParentFocusTarget
    data object Remote : ParentFocusTarget
    data object Password : ParentFocusTarget
    data object AutoUpdate : ParentFocusTarget
    data object QuizSetup : ParentFocusTarget
    data object MigrationRetry : ParentFocusTarget
    data object WaitConfigRetry : ParentFocusTarget
    data object SummaryRead : ParentFocusTarget
    data class LocalRow(val key: String) : ParentFocusTarget
}

/**
 * The rail, top to bottom. Auto update is a device-level setting and is independent of the
 * cloud binding and of child mode, so it is always present, below the password row when there
 * is one and below the child-mode switch otherwise.
 *
 * The quiz setup row is a child-mode entry: it opens the two phone codes and stays available
 * after a cloud handoff, but it is hidden while child mode is off.
 */
internal fun parentRailTargets(
    childOn: Boolean,
    remoteVisible: Boolean,
    passwordVisible: Boolean,
): List<ParentFocusTarget> = buildList {
    add(ParentFocusTarget.Switch)
    if (childOn) {
        if (remoteVisible) add(ParentFocusTarget.Remote)
        if (passwordVisible) add(ParentFocusTarget.Password)
    }
    add(ParentFocusTarget.AutoUpdate)
    if (childOn) add(ParentFocusTarget.QuizSetup)
}

/** The right-hand pane. The rail no longer selects a guard mode. */
internal enum class ParentPaneKind {
    OFF,
    LOCAL,
    BIND,
    MIGRATION,
    WAIT_CONFIG,
    SUMMARY,
    QUIZ_SETUP,
}

internal enum class ParentZone { RAIL, PANE }

/** Left always returns to the rail row that opened the pane. */
internal enum class ParentRailOrigin { SWITCH, REMOTE, QUIZ_SETUP }
