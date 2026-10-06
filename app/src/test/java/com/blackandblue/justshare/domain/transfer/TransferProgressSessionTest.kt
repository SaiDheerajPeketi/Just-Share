package com.blackandblue.justshare.domain.transfer

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProgressSessionTest {
    @Test
    fun queuedCompletionCannotRestartCancelledProgress() {
        val session = TransferProgressSession()
        var replay: String? = null
        val id = session.begin { replay = null }
        val queued = mutableListOf<() -> Unit>()
        val shown = mutableListOf<String>()
        session.withCurrent(id) {
            replay = "100%"
            queued += { session.withCurrent(id) { shown += "100%" } }
        }
        session.clear { replay = null }
        queued.forEach { it() }
        assertEquals(null, replay)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun oldErrorCannotReplaceFreshRetryProgress() {
        val session = TransferProgressSession()
        val old = session.begin {}
        session.clear {}
        val replacement = session.begin {}
        var progress = 0
        assertTrue(session.withCurrent(replacement) { progress = 45 })
        assertFalse(session.withCurrent(old) { progress = -1 })
        assertEquals(45, progress)
        assertFalse(session.isCurrent(old))
        assertTrue(session.isCurrent(replacement))
    }

    @Test
    fun newConnectionAcceptsFirstIncomingProgressWithoutUiStart() {
        val session = TransferProgressSession()
        assertFalse(session.withCurrent(0) { error("No connection exists") })
        val id = session.begin {}
        var file: String? = null
        assertTrue(session.withCurrent(id) { file = "first-file.pdf" })
        assertEquals("first-file.pdf", file)
    }

    @Test
    fun cancellingDuringPublicationClearsReplayAndRejectsQueuedDelivery() {
        val session = TransferProgressSession()
        var replay: String? = null
        val id = session.begin { replay = null }
        val publicationStarted = CountDownLatch(1)
        val finishPublication = CountDownLatch(1)
        val cancellationStarted = CountDownLatch(1)
        val cancellationFinished = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val publisher = Thread {
            try {
                session.withCurrent(id) {
                    publicationStarted.countDown()
                    check(finishPublication.await(2, TimeUnit.SECONDS))
                    replay = "old-file.pdf"
                }
            } catch (failure: Throwable) {
                failures.add(failure)
            }
        }
        val cancellation = Thread {
            cancellationStarted.countDown()
            try {
                session.clear { replay = null }
            } catch (failure: Throwable) {
                failures.add(failure)
            } finally {
                cancellationFinished.countDown()
            }
        }
        try {
            publisher.start()
            assertTrue(publicationStarted.await(2, TimeUnit.SECONDS))
            cancellation.start()
            assertTrue(cancellationStarted.await(2, TimeUnit.SECONDS))
            assertFalse(cancellationFinished.await(50, TimeUnit.MILLISECONDS))
            finishPublication.countDown()
            assertTrue(cancellationFinished.await(2, TimeUnit.SECONDS))
            assertEquals(null, replay)
            assertFalse(session.withCurrent(id) { replay = "late delivery" })
        } finally {
            finishPublication.countDown()
            publisher.join(2_000)
            cancellation.join(2_000)
        }
        assertFalse(publisher.isAlive)
        assertFalse(cancellation.isAlive)
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(null, replay)
    }
}
