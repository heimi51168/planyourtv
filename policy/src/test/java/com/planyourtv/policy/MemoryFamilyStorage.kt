package com.planyourtv.policy

class MemoryFamilyStorage : FamilyStorage {
    val values = linkedMapOf<String, String>()
    var ready = true
    var writeCount = 0
    var failFromWrite: Int = -1

    override fun isReady(): Boolean = ready

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String): Boolean {
        if (!ready) return false
        if (failFromWrite >= 0 && writeCount >= failFromWrite) {
            writeCount++
            return false
        }
        writeCount++
        values[key] = value
        return true
    }

    override fun remove(key: String): Boolean = values.remove(key) != null
}
