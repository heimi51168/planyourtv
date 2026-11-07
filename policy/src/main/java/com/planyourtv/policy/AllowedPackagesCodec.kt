package com.planyourtv.policy

import org.json.JSONArray

/**
 * Strict JSON array codec for the rule app scope. A malformed array returns null instead of
 * silently becoming "all apps": widening a child's allowlist because of bad data is worse than
 * rejecting that one config snapshot.
 */
internal object AllowedPackagesCodec {
    fun parse(array: JSONArray?): Set<String>? {
        if (array == null) return null
        val packages = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val raw = array.opt(index)
            if (raw !is String) return null
            val value = raw.trim()
            if (value.isEmpty()) return null
            packages.add(value)
        }
        return packages
    }

    fun toJson(packages: Set<String>): JSONArray = JSONArray().also { array ->
        packages.sorted().forEach(array::put)
    }

    fun parseString(raw: String?): Set<String>? =
        raw?.let { runCatching { parse(JSONArray(it)) }.getOrNull() }

    fun toJsonString(packages: Set<String>): String = toJson(packages).toString()
}
