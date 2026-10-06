package com.blackandblue.justshare

import java.io.InterruptedIOException
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the actual service guards without starting a service or connecting to a peer. */
class TransferConnectionOwnershipTest {
    private fun field(name: String) = CommunicationService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }

    private fun withReplacement(block: (CommunicationService, Long, Long, Socket, ServerSocket, ExecutorService) -> Unit) {
        val service = CommunicationService()
        val executor = field("executorService").get(service) as ExecutorService
        val socket = Socket()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        try {
            val old = CommunicationService.beginTransferUpdateSession()
            CommunicationService.clearTransferUpdate()
            val replacement = CommunicationService.beginTransferUpdateSession()
            field("progressGeneration").setLong(service, replacement)
            field("communicationSocket").set(service, socket)
            field("serverSocket").set(service, listener)
            (field("serviceState").get(service) as AtomicInteger).set(2) // CONNECTING
            block(service, old, replacement, socket, listener, executor)
        } finally {
            socket.close()
            listener.close()
            executor.shutdownNow()
            CommunicationService.clearTransferUpdate()
        }
    }

    @Test
    fun delayedOldWorkerCleanupLeavesReplacementResourcesOpen() = withReplacement { service, old, _, socket, listener, executor ->
        val cleanup = CommunicationService::class.java.getDeclaredMethod(
            "closeAllAndStop", java.lang.Long::class.java, java.lang.Integer::class.java
        ).apply { isAccessible = true }
        cleanup.invoke(service, old, 42)
        assertFalse(socket.isClosed)
        assertFalse(listener.isClosed)
        assertFalse(executor.isShutdown)
        assertSame(socket, field("communicationSocket").get(service))
        assertSame(listener, field("serverSocket").get(service))
        assertFalse(field("stopRequested").getBoolean(service))
        assertEquals(2, (field("serviceState").get(service) as AtomicInteger).get())
    }

    @Test
    fun oldWorkerCannotInstallResourcesOrStateIntoReplacement() = withReplacement { service, old, replacement, _, _, _ ->
        val install = CommunicationService::class.java.getDeclaredMethod(
            "withConnection", java.lang.Long.TYPE, kotlin.jvm.functions.Function0::class.java
        ).apply { isAccessible = true }
        var installed = false
        val action: () -> Unit = { installed = true }
        var rejected = false
        try {
            install.invoke(service, old, action)
        } catch (failure: InvocationTargetException) {
            assertTrue(failure.cause is InterruptedIOException)
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(installed)
        install.invoke(service, replacement, action)
        assertTrue(installed)
    }
}
