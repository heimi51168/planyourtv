package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicySessionTest {
    @Test
    fun pendingOnlyStartsWithPositiveTime() {
        val pending = PolicySession().pending(30L, "小明")
        assertEquals(SessionState.PENDING, pending.state)
        assertEquals(30L, pending.remainingSeconds)

        val active = pending.start()
        assertEquals(SessionState.ACTIVE, active.state)
        assertEquals(30L, active.remainingSeconds)

        val empty = pending.copy(remainingSeconds = 0L)
        assertEquals(SessionState.PENDING, empty.start().state)
    }

    @Test
    fun activeTickConsumesOneSecondOnlyWhenInteractive() {
        val active = PolicySession().pending(2L, "小明").start()

        assertEquals(2L, active.tick(interactive = false).remainingSeconds)
        val one = active.tick(interactive = true)
        assertEquals(1L, one.remainingSeconds)
        assertEquals(SessionState.ACTIVE, one.state)

        val done = one.tick(interactive = true)
        assertEquals(SessionState.IDLE, done.state)
        assertEquals(0L, done.remainingSeconds)
    }

    @Test
    fun lockClearsTheGrant() {
        val active = PolicySession().pending(30L, "小明").start()
        val locked = active.lock()
        assertEquals(SessionState.IDLE, locked.state)
        assertEquals(0L, locked.remainingSeconds)
    }
}
