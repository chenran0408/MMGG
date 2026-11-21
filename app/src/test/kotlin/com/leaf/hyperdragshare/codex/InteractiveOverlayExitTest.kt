package com.leaf.hyperdragshare.codex

import android.app.Dialog
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.ViewTreeObserver
import androidx.lifecycle.ViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.lang.reflect.Array

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(
    manifest = Config.NONE,
    sdk = [35],
)
class InteractiveOverlayExitTest {
    private val createdWindows = mutableListOf<InteractiveOverlayWindow>()

    @After
    fun cleanUpWindows() {
        createdWindows.forEach { dialog(it).dismiss() }
        ShadowLooper.shadowMainLooper().idle()
        createdWindows.forEach { it.dismiss() }
        ShadowLooper.shadowMainLooper().idle()
        createdWindows.clear()
    }

    @Test
    fun dismissBeforeFirstDrawRemovesOwnedListenerAndMakesCapturedCallbackInert() {
        var dismissed = 0
        val overlay = window { dismissed++ }
        val decor = overlay.window.decorView
        val initialFeedback = shadowOf(decor).lastHapticFeedbackPerformed()
        val storeModel = ClearableModel()
        overlay.viewModelStore.put("selection", storeModel)

        assertTrue(overlay.show(PRESS_CONFIRMED_AT))
        val observer = decor.viewTreeObserver
        val ownedListener = requireNotNull(drawListeners(observer).singleOrNull())
        assertTrue(dialog(overlay).isShowing)

        overlay.dismiss()

        assertFalse(dialog(overlay).isShowing)
        assertFalse(decor.isAttachedToWindow)
        assertFalse(drawListeners(decor.viewTreeObserver).any { it === ownedListener })
        assertEquals(1, dismissed)
        assertTrue(storeModel.cleared)

        // Exercise the already captured callback as if a draw arrived after disposal.
        ownedListener.onDraw()
        ShadowLooper.shadowMainLooper().idle()

        assertNull(field(overlay, "pressConfirmedPostedCallback"))
        assertEquals(initialFeedback, shadowOf(decor).lastHapticFeedbackPerformed())
        assertFalse(dialog(overlay).isShowing)
        assertFalse(decor.isAttachedToWindow)

        overlay.dismiss()
        assertEquals(1, dismissed)
        assertEquals(1, storeModel.clearCount)
    }

    @Test
    fun firstDrawConfirmsOnceAndRemovesListenerAfterPostedCallback() {
        val overlay = window()
        val decor = overlay.window.decorView

        assertTrue(overlay.show(PRESS_CONFIRMED_AT))
        val observer = decor.viewTreeObserver
        val ownedListener = requireNotNull(drawListeners(observer).singleOrNull())
        observer.dispatchOnDraw()
        val callback = postedCallback(overlay)
        observer.dispatchOnDraw()
        assertSame(callback, postedCallback(overlay))
        ShadowLooper.shadowMainLooper().idle()

        assertNull(field(overlay, "pressConfirmedPostedCallback"))
        assertEquals(HapticFeedbackConstants.CONFIRM, shadowOf(decor).lastHapticFeedbackPerformed())
        assertFalse(drawListeners(decor.viewTreeObserver).any { it === ownedListener })
        assertTrue(dialog(overlay).isShowing)

        overlay.dismiss()
    }

    @Test
    fun dismissAfterDrawPostCancelsCallbackAndCapturedRunnableCannotConfirmLater() {
        val overlay = window()
        val decor = overlay.window.decorView
        val initialFeedback = shadowOf(decor).lastHapticFeedbackPerformed()

        assertTrue(overlay.show(PRESS_CONFIRMED_AT))
        val observer = decor.viewTreeObserver
        val ownedListener = requireNotNull(drawListeners(observer).singleOrNull())

        observer.dispatchOnDraw()
        val capturedCallback = postedCallback(overlay)

        overlay.dismiss()
        assertFalse(dialog(overlay).isShowing)
        assertFalse(drawListeners(decor.viewTreeObserver).any { it === ownedListener })

        capturedCallback.run()
        ShadowLooper.shadowMainLooper().idle()

        assertNull(field(overlay, "pressConfirmedPostedCallback"))
        assertEquals(initialFeedback, shadowOf(decor).lastHapticFeedbackPerformed())
        assertFalse(dialog(overlay).isShowing)
        assertFalse(decor.isAttachedToWindow)
    }

    @Test
    fun dismissRemovesGlobalLayoutListenerFromCurrentLiveObserver() {
        val overlay = window()
        val decor = overlay.window.decorView

        assertTrue(overlay.show())
        val registeredObserver = observer(overlay, "observedTree")
        val ownedListener = requireNotNull(
            field(overlay, "touchRegionListener") as? ViewTreeObserver.OnGlobalLayoutListener,
        )
        assertTrue(globalLayoutListeners(registeredObserver).any { it === ownedListener })

        ShadowLooper.shadowMainLooper().idle()
        val liveObserver = decor.viewTreeObserver
        assertTrue(liveObserver.isAlive)
        assertTrue(globalLayoutListeners(liveObserver).any { it === ownedListener })

        overlay.dismiss()

        val liveObserverAfterDismiss = decor.viewTreeObserver
        assertTrue(liveObserverAfterDismiss.isAlive)
        assertFalse(globalLayoutListeners(liveObserverAfterDismiss).any { it === ownedListener })
        if (liveObserver.isAlive) assertFalse(globalLayoutListeners(liveObserver).any { it === ownedListener })
        if (registeredObserver.isAlive) {
            assertFalse(globalLayoutListeners(registeredObserver).any { it === ownedListener })
        }
    }

    @Test
    fun directDialogDismissAndCancelRemoveGlobalLayoutListener() {
        val exits: List<(Dialog) -> Unit> = listOf({ it.dismiss() }, { it.cancel() })
        for (exit in exits) {
            val overlay = window()
            val decor = overlay.window.decorView
            assertTrue(overlay.show())
            ShadowLooper.shadowMainLooper().idle()

            val liveObserver = decor.viewTreeObserver
            val ownedListener = requireNotNull(
                field(overlay, "touchRegionListener") as? ViewTreeObserver.OnGlobalLayoutListener,
            )
            assertTrue(globalLayoutListeners(liveObserver).any { it === ownedListener })

            exit(dialog(overlay))
            ShadowLooper.shadowMainLooper().idle()

            assertFalse(dialog(overlay).isShowing)
            val liveObserverAfterExit = decor.viewTreeObserver
            assertTrue(liveObserverAfterExit.isAlive)
            assertFalse(globalLayoutListeners(liveObserverAfterExit).any { it === ownedListener })
            if (liveObserver.isAlive) {
                assertFalse(globalLayoutListeners(liveObserver).any { it === ownedListener })
            }
        }
    }

    private fun window(onDismissed: () -> Unit = {}): InteractiveOverlayWindow {
        val overlay = InteractiveOverlayWindow(
            RuntimeEnvironment.getApplication() as Context,
            OverlayWindowPolicy.portal(),
            onDismissed,
            { throw AssertionError("Unexpected window failure", it) },
            content = {},
        )
        createdWindows.add(overlay)
        return overlay
    }

    private fun dialog(overlay: InteractiveOverlayWindow): Dialog {
        val field = InteractiveOverlayWindow::class.java.getDeclaredField("dialog")
        field.isAccessible = true
        return field.get(overlay) as Dialog
    }

    private fun postedCallback(overlay: InteractiveOverlayWindow): Runnable {
        return requireNotNull(field(overlay, "pressConfirmedPostedCallback") as? Runnable)
    }

    private fun observer(overlay: InteractiveOverlayWindow, name: String): ViewTreeObserver {
        return requireNotNull(field(overlay, name) as? ViewTreeObserver)
    }

    private fun field(instance: Any, name: String): Any? {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(instance)
    }

    /** Reads the observer's actual CopyOnWriteArray registration storage. */
    private fun drawListeners(observer: ViewTreeObserver): List<ViewTreeObserver.OnDrawListener> {
        return observerListeners(observer, "mOnDrawListeners")
            .filterIsInstance<ViewTreeObserver.OnDrawListener>()
    }

    private fun globalLayoutListeners(observer: ViewTreeObserver): List<ViewTreeObserver.OnGlobalLayoutListener> {
        val listenerField = ViewTreeObserver::class.java.getDeclaredField("mOnGlobalLayoutListeners")
        listenerField.isAccessible = true
        val registrations = listenerField.get(observer) ?: return emptyList()
        val dataField = registrations.javaClass.getDeclaredField("mData")
        dataField.isAccessible = true
        val data = dataField.get(registrations) as? List<*>
            ?: error("Expected SDK 35 CopyOnWriteArray.mData to be a List")
        return data.filterIsInstance<ViewTreeObserver.OnGlobalLayoutListener>()
    }

    private fun observerListeners(observer: ViewTreeObserver, fieldName: String): List<Any> {
        val field = ViewTreeObserver::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        val registrations = field.get(observer)
        val snapshot = when {
            registrations == null -> return emptyList()
            registrations is Iterable<*> -> registrations.toList()
            registrations.javaClass.isArray -> {
                val count = Array.getLength(registrations)
                (0 until count).map { index -> Array.get(registrations, index) }
            }
            else -> {
                val get = registrations.javaClass.methods.firstOrNull {
                    it.name == "get" && it.parameterCount == 0
                } ?: registrations.javaClass.declaredMethods.firstOrNull {
                    it.name == "get" && it.parameterCount == 0
                } ?: return emptyList()
                get.isAccessible = true
                val values = get.invoke(registrations) ?: return emptyList()
                if (!values.javaClass.isArray) return emptyList()
                val count = Array.getLength(values)
                (0 until count).map { index -> Array.get(values, index) }
            }
        }
        return snapshot.filterNotNull()
    }

    private class ClearableModel : ViewModel() {
        var cleared = false
        var clearCount = 0

        override fun onCleared() {
            cleared = true
            clearCount++
        }
    }

    private companion object {
        const val PRESS_CONFIRMED_AT = 1L
    }
}
