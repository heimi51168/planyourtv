package app.orchard.tv.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signals which access fingerprint the last Home arrangement was built for. The gate keeps the
 * "desktop preparing" screen until this matches the desired fingerprint, or its timeout expires.
 */
object PolicyHomeReady {
    private val _fingerprint = MutableStateFlow("")
    val fingerprint: StateFlow<String> = _fingerprint.asStateFlow()

    fun mark(fingerprint: String) {
        _fingerprint.value = fingerprint
    }
}
