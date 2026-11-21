package com.leaf.hyperdragshare.codex

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent

/** Uses the module's press detector and timeouts for the interactive settings example. */
internal class TutorialPress(
    private val singleMillis: Int,
    private val doubleMillis: Int,
    private val slop: Float,
    private val onPreview: () -> Unit,
    private val onWords: () -> Unit,
    private val onMove: (Float, Float) -> Unit,
    private val onFinish: (Boolean) -> Unit,
) {
    private val timer = Handler(Looper.getMainLooper())
    private val detector = PressTriggerDetector()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val confirm = Runnable {
        if (detector.fire(SystemClock.uptimeMillis())) {
            if (detector.isTwoFinger) onWords() else onPreview()
        }
    }

    fun stop() {
        timer.removeCallbacks(confirm)
        if (detector.isActive && detector.isFired && !detector.isTwoFinger) onFinish(false)
        detector.cancel()
        dragging = false
    }

    fun touch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stop()
                downX = event.x
                downY = event.y
                detector.start(downX, downY, event.eventTime, singleMillis.toLong())
                timer.postAtTime(confirm, detector.deadline())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount != 2 || !detector.isActive || detector.isFired) stop()
                else {
                    timer.removeCallbacks(confirm)
                    detector.startTwoFinger(event.getX(0), event.getY(0), event.getX(1), event.getY(1),
                        event.eventTime, doubleMillis.toLong(), slop)
                    timer.postAtTime(confirm, detector.deadline())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (detector.isTwoFinger && event.pointerCount == 2) {
                    detector.onTwoPointerEvent(2, event.getX(0), event.getY(0), event.getX(1), event.getY(1), 2)
                } else if (detector.isActive && detector.isFired && !detector.isTwoFinger) {
                    if (!dragging) dragging = kotlin.math.hypot(event.x - downX, event.y - downY) >= slop
                    if (dragging) onMove(event.x, event.y)
                } else {
                    detector.onPointerEvent(2, event.x, event.y, event.pointerCount)
                }
                if (!detector.isActive) timer.removeCallbacks(confirm)
            }
            MotionEvent.ACTION_UP -> {
                if (detector.isActive && detector.isFired && !detector.isTwoFinger) {
                    if (dragging) onMove(event.x, event.y)
                    onFinish(dragging)
                }
                detector.cancel()
                timer.removeCallbacks(confirm)
                dragging = false
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> stop()
        }
        return true
    }
}
