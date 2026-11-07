package com.planyourtv.policy.quiz

import org.json.JSONObject

/** What the pending local edit is waiting for. A binding report uses the idempotent endpoint. */
enum class QuizPendingKind {
    NONE,
    BINDING,
    EDIT,
}

/**
 * Durable sync state for the quiz config. It is separate from [QuizSourceConfig] because a
 * pending upload or a conflict is not part of the configuration the engine executes; it only
 * tells the parent page what still needs to reach the cloud.
 */
data class QuizSyncState(
    val pendingLocal: Boolean = false,
    val conflict: Boolean = false,
    val pendingKind: QuizPendingKind = QuizPendingKind.NONE,
    /** Stable code, never a server message: NETWORK / SERVER / CONFLICT / CLOUD_MANAGED / STORAGE. */
    val lastError: String = "",
    val updatedAt: Long = 0L,
) {
    fun encode(): JSONObject = JSONObject()
        .put("pendingLocal", pendingLocal)
        .put("conflict", conflict)
        .put("pendingKind", pendingKind.name)
        .put("lastError", lastError)
        .put("updatedAt", updatedAt)

    companion object {
        fun decode(raw: String?): QuizSyncState? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val kind = when (root.optString("pendingKind", "")) {
                    QuizPendingKind.BINDING.name -> QuizPendingKind.BINDING
                    QuizPendingKind.EDIT.name -> QuizPendingKind.EDIT
                    else -> QuizPendingKind.NONE
                }
                QuizSyncState(
                    pendingLocal = root.optBoolean("pendingLocal", false),
                    conflict = root.optBoolean("conflict", false),
                    pendingKind = kind,
                    lastError = root.optString("lastError", ""),
                    updatedAt = root.optLong("updatedAt", 0L).coerceAtLeast(0L),
                )
            }.getOrNull()
        }
    }
}
