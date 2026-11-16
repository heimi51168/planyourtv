package app.orchard.tv.bot

import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The audit log is the accountability half of the no-PIN model: it must keep
 * every control attempt (accepted or refused), survive as plain storage, and
 * never crash on a corrupted store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BotAuditLogTest {

    // Same key BotAuditLog persists under; duplicated here to reset between tests.
    private val auditKey = "bot.audit.v1"

    @Before
    fun resetStore() {
        TvStore.init(RuntimeEnvironment.getApplication())
        TvStore.remove(auditKey)
    }

    private fun entry(actor: String, cmd: String, ok: Boolean, at: Long) =
        BotAuditLog.Entry(atMs = at, actor = actor, command = cmd, accepted = ok, detail = "$actor-$cmd")

    @Test
    fun entriesRoundTripNewestLast() {
        BotAuditLog.append(entry("妈妈", "加时20", true, 100L))
        BotAuditLog.append(entry("爸爸", "停止", true, 200L))
        BotAuditLog.append(entry("妈妈", "解锁", false, 300L))
        val recent = BotAuditLog.recent()
        assertEquals(listOf("加时20", "停止", "解锁"), recent.map { it.command })
        assertEquals(listOf(true, true, false), recent.map { it.accepted })
        assertEquals(300L, recent.last().atMs)
    }

    @Test
    fun ringKeepsTheNewestTwoHundred() {
        for (i in 1..205) {
            BotAuditLog.append(entry("家长$i", "加时$i", true, i * 1000L))
        }
        val recent = BotAuditLog.recent(limit = 200)
        assertEquals(200, recent.size)
        // Entries 1-5 fell off the ring; #6 is now the oldest.
        assertEquals("家长6", recent.first().actor)
        assertEquals("家长205", recent.last().actor)
    }

    @Test
    fun rejectedEntriesCanBeFilteredOut() {
        BotAuditLog.append(entry("妈妈", "加时20", true, 1L))
        BotAuditLog.append(entry("爸爸", "解锁", false, 2L))
        val acceptedOnly = BotAuditLog.recent(includeRejected = false)
        assertEquals(listOf("加时20"), acceptedOnly.map { it.command })
    }

    @Test
    fun corruptStoreYieldsEmptyInsteadOfCrashing() {
        TvStore.putStringDurable(auditKey, "not json at all")
        assertTrue(BotAuditLog.recent().isEmpty())
        // And the log recovers on the next append.
        BotAuditLog.append(entry("妈妈", "停止", true, 5L))
        assertEquals(1, BotAuditLog.recent().size)
    }

    @Test
    fun longDetailsAreCapped() {
        BotAuditLog.append(
            entry("妈妈", "加时20", true, 1L).copy(detail = "长".repeat(300)),
        )
        assertTrue(BotAuditLog.recent().single().detail.length <= 80)
    }
}
