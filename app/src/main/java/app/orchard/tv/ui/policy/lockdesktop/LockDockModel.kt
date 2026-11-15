package app.orchard.tv.ui.policy.lockdesktop

import androidx.annotation.DrawableRes

/** The fixed lock-screen actions, in the order the desktop dock draws them. */
enum class LockDockId { QUIZ, START, PARENT }

/**
 * One dock tile. The host decides availability and copy from the policy state; this model is
 * otherwise pure, so the dock never reads business state or resources.
 */
data class LockDockSlot(
    val id: LockDockId,
    @param:DrawableRes val art: Int,
    /** Used for accessibility and tracing only; the finished artwork already carries the name. */
    val label: String,
    /** The selected description under the dock, copied from the existing footer logic. */
    val hint: String,
    val enabled: Boolean,
)
