package com.leaf.hyperdragshare.codex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], shadows = [StashImportActivityTest.Notices::class])
class StashImportActivityTest {
    @Implements(value = StashOverlayPanel::class, isInAndroidSdk = false)
    class Notices {
        companion object {
            val counts = java.util.concurrent.CopyOnWriteArrayList<Int>()
            @JvmStatic @Implementation
            fun contentAdded(context: android.content.Context, count: Int) { counts.add(count) }
        }
    }

    @Test fun recreationDuringSlowReadImportsOnceAndANewShareOfTheSameUriIsIndependent() {
        Notices.counts.clear()
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://stash-test/slow-image")
        val streamOpened = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val opens = AtomicInteger()
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            opens.incrementAndGet()
            streamOpened.countDown()
            check(allowRead.await(8, TimeUnit.SECONDS))
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        val share = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        val original = Robolectric.buildActivity(StashActivity::class.java, share).setup()
        val saved = Bundle()
        val restored = Robolectric.buildActivity(StashActivity::class.java, Intent(share))
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(streamOpened.await(5, TimeUnit.SECONDS))
            original.pause().saveInstanceState(saved).stop().destroy()
            restored.create(saved).start().restoreInstanceState(saved).resume()
            allowRead.countDown()
            awaitFinish(restored.get())
            assertEquals(1, opens.get())
            assertEquals(listOf(1), Notices.counts.toList())
            StashStore(context).use { assertEquals(1, it.listEntries().size) }
        } finally {
            allowRead.countDown()
            restored.pause().stop().destroy()
        }
        val another = Robolectric.buildActivity(StashActivity::class.java, Intent(share)).setup()
        try {
            awaitFinish(another.get())
            assertEquals(2, opens.get())
            assertEquals(listOf(1, 1), Notices.counts.toList())
            StashStore(context).use { store ->
                assertEquals(2, store.listEntries().map { it.batchId }.distinct().size)
            }
        } finally {
            another.pause().stop().destroy()
        }
    }

    private fun awaitFinish(activity: StashActivity) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
        while (!activity.isFinishing && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("share receiver finishes after the transaction", activity.isFinishing)
    }
}
