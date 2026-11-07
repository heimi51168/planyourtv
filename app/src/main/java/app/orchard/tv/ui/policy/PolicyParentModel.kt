package app.orchard.tv.ui.policy

/** Logical focus targets. Compose focus search is never used to move between them. */
internal sealed interface ParentFocusTarget {
    data object Switch : ParentFocusTarget
    data object Remote : ParentFocusTarget
    data object Password : ParentFocusTarget
    data object MigrationRetry : ParentFocusTarget
    data object WaitConfigRetry : ParentFocusTarget
    data object SummaryRead : ParentFocusTarget
    data class LocalRow(val key: String) : ParentFocusTarget
}

/** The right-hand pane. The rail no longer selects a guard mode. */
internal enum class ParentPaneKind {
    OFF,
    LOCAL,
    BIND,
    MIGRATION,
    WAIT_CONFIG,
    SUMMARY,
}

internal enum class ParentZone { RAIL, PANE }

/** Left always returns to the rail row that opened the pane. */
internal enum class ParentRailOrigin { SWITCH, REMOTE }
