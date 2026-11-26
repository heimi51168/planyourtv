package com.planyourtv.policy

/**
 * One place for the MMKV key names. The values of the session/PIN keys must never change:
 * they are the migration contract with the pre-MMKV `policy` SharedPreferences file.
 */
internal object PolicyKeys {
    const val STATE = "session_state"
    const val TOTAL = "total_seconds"
    const val REMAINING = "remaining_seconds"
    const val USER = "user_name"
    const val BOUND = "bound"
    const val DEVICE_NO = "device_no"
    // TV Link v2 bearer. device_no is public; this secret must never enter UI state or logs.
    const val DEVICE_TOKEN = "device_token_v2"
    // MQTT command-signing public key fetched over the authenticated v2 channel.
    // State is "unknown" until the first successful fetch; an unknown state must not accept
    // dangerous commands from a v2 device.
    const val COMMAND_SIGNING_STATE = "command_signing_state_v1"
    const val COMMAND_SIGNING_KEY_ID = "command_signing_key_id_v1"
    const val COMMAND_SIGNING_PUBLIC_KEY = "command_signing_public_key_v1"
    const val COMMAND_SIGNING_FETCHED_AT = "command_signing_fetched_at_v1"

    const val PIN_SALT = "pin_salt"
    const val PIN_HASH = "pin_hash"
    const val PIN_ITERATIONS = "pin_iterations"
    const val PIN_FAILED = "pin_failed"
    const val PIN_LOCKOUT = "pin_lockout_until"
    // One-time privacy consent before local rules/password may be uploaded for remote control.
    const val CLOUD_UPLOAD_CONSENT = "cloud_upload_consent_v1"
    // Expiry of a parent-auth proof. The proof itself (and the PIN) is memory-only.
    const val PARENT_AUTH_EXPIRES_AT = "parent_auth_expires_at_v1"
    // Local change happened while offline; the parent must re-enter the PIN once to push it.
    const val PENDING_PASSWORD_SYNC = "pending_password_sync"
    // Server-side fingerprint of the PIN last applied from the cloud; drives self-heal pulls.
    const val CLOUD_PIN_VERSION = "cloud_pin_version"

    const val RECORD_ID = "record_id"
    const val RECORD_STARTED = "record_started"
    const val ACCESS_MODE = "access_mode"
    const val ALLOWED_PACKAGES = "allowed_packages"
    const val VALID_UNTIL = "valid_until_label"
    const val USER_ICON = "user_icon_url"

    const val PENDING_SETTLE_ID = "pending_settle_record_id"
    const val PENDING_SETTLE_SECONDS = "pending_settle_seconds"

    // Last known cloud policy mirror. Kept for the read-only parent-center summary while offline.
    const val REMOTE_POLICY = "remote_policy_v1"
    // Pending rule usage START/PROGRESS/END events. END must survive until acknowledged.
    const val RULE_USAGE_QUEUE = "rule_usage_queue_v1"

    // Local engine. PIN keys stay separate physically, but TvPinStore writes both copies so
    // LOCAL and CLOUD share one logical password. The session/rules keys remain mode-specific.
    const val LOCAL_MODE = "local_mode"
    const val MODE_VERSION = "policy_mode_version"
    // Legacy flat rules. Schema 3 no longer writes this key.
    const val LOCAL_RULES = "local_rules_v1"
    // Schema 2 rule sets. V42 treats these documents as unconfigured instead of migrating them.
    const val LOCAL_RULES_V2 = "local_rules_v2"
    // Schema 3: the manual time-rule set with day groups plus the shared app scope.
    const val LOCAL_RULES_V3 = "local_rules_v3"
    // Execution configuration, separate from the rule template.
    const val LOCAL_POLICY_EPOCH = "local_policy_epoch_v1"
    // Exchange records already confirmed or ended on this TV. The grant poll must not re-arm
    // them (especially when it still reports costTime=0), including after a process restart.
    const val LOCAL_SETTLED_GRANT_RECORDS = "local_settled_grant_records_v1"
    // A parent override must not survive a restart as an unlock.
    const val LOCAL_PARENT_OVERRIDE = "local_parent_override_v1"
    const val LOCAL_PIN_SALT = "local_pin_salt"
    const val LOCAL_PIN_HASH = "local_pin_hash"
    const val LOCAL_PIN_FAILED = "local_pin_failed"
    const val LOCAL_PIN_LOCKOUT = "local_pin_lockout_until"
    const val LOCAL_RECOVERY_SALT = "local_recovery_salt"
    const val LOCAL_RECOVERY_HASH = "local_recovery_hash"
    const val LOCAL_RECOVERY_FAILED = "local_recovery_failed"
    const val LOCAL_RECOVERY_LOCKOUT = "local_recovery_lockout_until"
    const val LOCAL_DAY_DATE = "local_day_date"
    const val LOCAL_DAY_USED = "local_day_used_seconds"
    const val LOCAL_BREAK_UNTIL = "local_break_until_epoch_ms"
    // End of the most recent rest-worthy child segment. The rest deadline is derived from this
    // if the process dies between the end report and the BREAK_UNTIL write.
    const val LOCAL_LAST_SESSION_ENDED_AT = "local_last_session_ended_at_epoch_ms"
    const val LOCAL_SESSION_TOTAL = "local_session_total_seconds"
    const val LOCAL_SESSION_REMAINING = "local_session_remaining_seconds"
    const val LOCAL_SESSION_END = "local_session_end_reason"
    // V6 rule-plan snapshot. Defaults keep a session saved by an older build readable.
    const val LOCAL_SESSION_ID = "local_session_id"
    const val LOCAL_SESSION_TEMPLATE_VERSION = "local_session_template_version"
    const val LOCAL_SESSION_REST_AFTER = "local_session_rest_after_seconds"
    const val LOCAL_SESSION_STARTED_AT = "local_session_started_at_epoch_ms"
    const val LOCAL_SESSION_USAGE_DATE = "local_session_usage_date"
    const val LOCAL_SESSION_DEADLINE = "local_session_deadline_epoch_ms"
    // Fixed wall deadline including the screen-off grace. Missing on sessions saved by V10.
    const val LOCAL_SESSION_WALL_DEADLINE = "local_session_wall_deadline_epoch_ms"
    // Non-zero while the screen is off; active counting stops but budget is not consumed.
    const val LOCAL_SESSION_PAUSED_AT = "local_session_paused_at_epoch_ms"
    const val LOCAL_SESSION_REPORT_USAGE = "local_session_report_usage"
    // Distinguishes a parent-confirmed local start from a phone-issued grant after a restart.
    const val LOCAL_SESSION_STARTED_LOCALLY = "local_session_started_locally"
    const val LOCAL_SESSION_ACTIVATION = "local_session_activation"
    const val LOCAL_SESSION_ACCESS_MODE = "local_session_access_mode"
    const val LOCAL_SESSION_ALLOWED_PACKAGES = "local_session_allowed_packages"
    // V45 parent-started session outside the child plan; never writes the daily ledger.
    const val LOCAL_SESSION_OUTSIDE_PLAN = "local_session_outside_plan"
    // V37 durable execution state. Each key is one atomic JSON snapshot with a version and CRC;
    // the legacy keys above remain as the migration/rollback source and are never the authority
    // once these exist.
    const val LOCAL_RUNTIME_V2 = "local_runtime_v2"
    const val LOCAL_GUARD_V2 = "local_guard_v2"
    // An in-progress study-to-earn round. Kept outside the runtime snapshot so adding it cannot
    // change the codec version or invalidate an existing session/ledger document.
    const val LOCAL_STUDY_ROUND_V1 = "local_study_round_v1"
    // V58 LOCAL fruit account. One document per local family member under this key plus a
    // stable member identifier. The ledger deliberately survives resetLocalSetup and is
    // independent of the TV's active member/session state.
    const val LOCAL_FRUIT_ACCOUNT_V1 = "local_fruit_account_v1"

    // Hosted (cloud rule guard) execution partition. A TV controlled from the cloud keeps its
    // usage/guard/study state here, so it can never overwrite — or be mistaken for — the
    // independent LOCAL child-mode state. Rule templates are deliberately not duplicated: the
    // cloud policy cache (REMOTE_POLICY) is their only copy, and LOCAL rules stay in
    // LOCAL_RULES_V3.
    const val HOSTED_POLICY_EPOCH = "hosted_policy_epoch_v1"
    const val HOSTED_SETTLED_GRANT_RECORDS = "hosted_settled_grant_records_v1"
    const val HOSTED_RUNTIME_V2 = "hosted_runtime_v2"
    const val HOSTED_GUARD_V2 = "hosted_guard_v2"
    const val HOSTED_STUDY_ROUND_V1 = "hosted_study_round_v1"
    // Cloud fruit account projection for the hosted rule guard. Written only by the device
    // context fetch, scoped by cloud member; the LOCAL fruit ledger stays untouched.
    const val HOSTED_FRUIT_FACTS_V1 = "hosted_fruit_facts_v1"
    // The cloud roster is per member since 2026-11-21. Every hosted key above gains a
    // `@member:<id>` suffix so one child's ledger, rest cursor and study round can never leak
    // into another's. The bare key remains as the pre-scope partition used for migration.
    const val HOSTED_SUBJECT_INDEX_V1 = "hosted_subject_index_v1"
    // The pre-scope partition may be adopted exactly once (into the member active at upgrade).
    // Without this marker every later member would adopt the same stale ledger on first scope.
    const val HOSTED_LEGACY_ADOPTED_V1 = "hosted_legacy_adopted_v1"

    internal fun hostedScoped(base: String, scope: String?): String =
        if (scope.isNullOrBlank()) base else "$base@$scope"

    // V40 family cache. Exactly one family is active on a TV; subject keys are built from the
    // validated local family/user IDs in FamilyKeys, never from a cloud ID or request string.
    const val FAMILY_INDEX_V1 = "family.index.v1"
    const val SUBJECT_ACTIVE_V1 = "subject.active.v1"
    const val IDENTITY_LINKS_V1 = "identity.links.v1"
    const val SUBJECT_MIGRATION_V1 = "subject.migration.v1"
}
