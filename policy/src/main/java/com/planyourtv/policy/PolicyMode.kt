package com.planyourtv.policy

/**
 * Which time engine is in charge. The two modes are mutually exclusive: exactly one of them
 * evaluates time, owns the active session and accepts its own PIN.
 */
enum class PolicyMode {
    NONE,
    LOCAL,
    CLOUD,
}
