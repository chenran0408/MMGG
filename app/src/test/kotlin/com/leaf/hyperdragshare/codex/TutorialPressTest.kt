package com.leaf.hyperdragshare.codex

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class TutorialPressTest {
    private val events = mutableListOf<String>()
    private val press = TutorialPress(600, 300, 8f,
        { events += "preview" }, { events += "words" },
        { x, _ -> events += "move:${x.toInt()}" }, { dropped -> events += "finish:$dropped" })
    private val downTime = SystemClock.uptimeMillis()

    private fun touch(action: Int, vararg xs: Float) {
        val props = Array(xs.size) { index -> MotionEvent.PointerProperties().apply { id = index } }
        val coords = Array(xs.size) { index -> MotionEvent.PointerCoords().apply { x = xs[index]; y = 10f } }
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, xs.size, props, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0)
        try { press.touch(e) } finally { e.recycle() }
    }
    private fun waitFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun releaseAloneCancelsTheHeldPreview() {
        touch(MotionEvent.ACTION_DOWN, 10f)
        waitFor(600)
        assertEquals(listOf("preview"), events)
        touch(MotionEvent.ACTION_UP, 10f)
        assertEquals(listOf("preview", "finish:false"), events)
    }

    @Test fun dragInTheSamePressCanDropAndCancelCannotDrop() {
        touch(MotionEvent.ACTION_DOWN, 10f)
        waitFor(600)
        touch(MotionEvent.ACTION_MOVE, 40f)
        touch(MotionEvent.ACTION_UP, 50f)
        touch(MotionEvent.ACTION_DOWN, 11f)
        waitFor(600)
        touch(MotionEvent.ACTION_MOVE, 46f)
        touch(MotionEvent.ACTION_CANCEL, 46f)
        assertEquals(listOf("preview", "move:40", "move:50", "finish:true", "preview", "move:46", "finish:false"), events)
    }

    @Test fun secondTouchUsesItsOwnDeadlineAndOpensWordsWhileHeld() {
        touch(MotionEvent.ACTION_DOWN, 10f)
        waitFor(500)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 10f, 30f)
        waitFor(299)
        assertEquals(emptyList<String>(), events)
        waitFor(1)
        assertEquals(listOf("words"), events)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 10f, 30f)
        touch(MotionEvent.ACTION_UP, 10f)
        assertEquals(listOf("words"), events)
    }

    @Test fun leavingTheExampleCancelsItsTimer() {
        touch(MotionEvent.ACTION_DOWN, 10f)
        press.stop()
        waitFor(800)
        assertEquals(emptyList<String>(), events)
    }
}
