package com.blackandblue.justshare.presentation

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.CommunicationService
import com.blackandblue.justshare.data.UserPreferencesDataStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRetryStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for actual transfer state", predicate())
    }

    private fun update(generation: Long, progress: Int) {
        val delivered = CountDownLatch(1)
        val intent = Intent(CommunicationService.BROADCAST_SENDING_UPDATE).apply {
            setPackage(application.packageName)
            putExtra(CommunicationService.EXTRAS_PROGRESS_GENERATION, generation)
            putExtra(CommunicationService.EXTRAS_PROGRESS_STATE, progress)
            putExtra(CommunicationService.EXTRAS_FILE_NAME, "queued-file.txt")
            putExtra(CommunicationService.EXTRAS_FILE_SIZE, 32L)
            putExtra(CommunicationService.EXTRAS_CURRENT_FILE_INDEX, 0)
            putExtra(CommunicationService.EXTRAS_TOTAL_FILES, 1)
        }
        application.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                delivered.countDown()
            }
        }, Handler(application.mainLooper), 0, null, null)
        assertTrue("Progress broadcast was not delivered", delivered.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun withViewModel(block: (TransferViewModel, Uri) -> Unit) {
        val store = ViewModelStore()
        val file = File.createTempFile("transfer-retry-", ".txt", application.cacheDir)
        val bytes = "Original fictional transfer file".toByteArray()
        file.writeBytes(bytes)
        val uri = Uri.fromFile(file)
        lateinit var viewModel: TransferViewModel
        try {
            instrumentation.runOnMainSync {
                CommunicationService.clearTransferUpdate()
                viewModel = TransferViewModel(application, UserPreferencesDataStore(application))
                store.put("retry-test", viewModel)
                viewModel.setUris(listOf(uri))
            }
            block(viewModel, uri)
            assertTrue(bytes.contentEquals(file.readBytes()))
        } finally {
            instrumentation.runOnMainSync {
                store.clear()
                CommunicationService.clearTransferUpdate()
            }
            if (file.exists()) assertTrue(file.delete())
        }
    }

    @Test
    fun cancelledAndQueuedOldProgressCannotBlockSelectedFileRetry() = withViewModel { vm, uri ->
        var old = 0L
        instrumentation.runOnMainSync {
            old = CommunicationService.beginTransferUpdateSession()
            vm.markTransferStarted()
        }
        update(old, 35)
        waitUntil { vm.state.value.progressPercent == 35f }
        val selected = vm.state.value.fileInfos
        instrumentation.runOnMainSync { vm.clearTransferProgress() }
        update(old, 100)
        assertFalse(vm.state.value.hasTransferStarted)
        assertFalse(vm.state.value.isTransferComplete)
        assertEquals(0f, vm.state.value.progressPercent, 0f)
        assertEquals(listOf(uri), vm.state.value.urisToShare)
        assertEquals(selected, vm.state.value.fileInfos)
        var replacement = 0L
        instrumentation.runOnMainSync {
            replacement = CommunicationService.beginTransferUpdateSession()
            vm.markTransferStarted()
        }
        update(old, -1)
        update(replacement, 45)
        waitUntil { vm.state.value.progressPercent == 45f }
        assertTrue(vm.state.value.hasTransferStarted)
        assertEquals(listOf(uri), vm.state.value.urisToShare)
        assertEquals(selected, vm.state.value.fileInfos)
    }

    @Test
    fun fastIncomingCompletionSurvivesTheNavigationStart() = withViewModel { vm, _ ->
        var current = 0L
        instrumentation.runOnMainSync { current = CommunicationService.beginTransferUpdateSession() }
        update(current, 100)
        waitUntil { vm.state.value.isTransferComplete }
        assertFalse(vm.state.value.hasTransferStarted)
        instrumentation.runOnMainSync { vm.markTransferStarted() }
        assertTrue(vm.state.value.hasTransferStarted)
        assertTrue(vm.state.value.isTransferComplete)
        assertEquals(100f, vm.state.value.progressPercent, 0f)
    }
}
