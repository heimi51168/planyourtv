package app.orchard.tv.quiz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The entertainment app a successful quiz round should open.
 *
 * Settlement starts the policy session, which disposes the lock screen — and with it the review
 * composition — before the report can call back into the host. The settlement therefore confirms
 * the target here, outside any composition, and [app.orchard.tv.ui.policy.PolicyHomeGate] collects
 * it, waits for the session to become ACTIVE and opens the app once.
 *
 * The target is confirmed only after the policy session really started; abandoning the quiz or a
 * refused start leaves it empty.
 */
object QuizLaunchTarget {
    private val _confirmed = MutableStateFlow<String?>(null)
    val confirmed: StateFlow<String?> = _confirmed.asStateFlow()

    /** Arms the launch after the earned session started. */
    fun confirm(packageName: String?) {
        val name = packageName?.takeIf { it.isNotBlank() } ?: return
        _confirmed.value = name
    }

    /** Takes the one-shot launch, clearing it only if it is still [packageName]. */
    @Synchronized
    fun consume(packageName: String): Boolean {
        if (_confirmed.value != packageName) return false
        _confirmed.value = null
        return true
    }

    fun clear() {
        _confirmed.value = null
    }
}
