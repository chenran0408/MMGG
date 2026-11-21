package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class StashShelfInteractionTest {
    @Test fun backLeavesChildLayersBeforeDismissingAndKeepsSelection() = withShelf { shelf ->
        val image = entry("image", "group", 0, image = true)
        val ids = setOf(image.id, "other")
        state(shelf, "selectedIds", ids)
        state(shelf, "selectionMode", true)
        state(shelf, "preview", image)
        state(shelf, "openedBatchId", "group")
        state(shelf, "confirmDelete", true)

        call(shelf, "back")
        assertEquals(false, state(shelf, "confirmDelete"))
        assertSame(image, state(shelf, "preview"))
        call(shelf, "back")
        assertNull(state(shelf, "preview"))
        call(shelf, "back")
        assertNull(state(shelf, "openedBatchId"))
        call(shelf, "back")
        assertEquals(ids, state(shelf, "selectedIds"))
        assertEquals(true, state(shelf, "selectionMode"))
        assertTrue(field(field(shelf, "overlay")!!, "disposed") as Boolean)
    }

    @Test fun collapseDismissesTheShelfWindow() = withShelf { shelf ->
        call(shelf, "collapse")
        assertTrue(field(field(shelf, "overlay")!!, "disposed") as Boolean)
    }

    @Test
    @Config(shadows = [OcrResultShadow::class, SegmentResultShadow::class])
    fun imageOcrKeepsPreviewUntilSegmentationAcceptsTheResult() = withShelf { shelf ->
        val image = entry("image", "group", 0, image = true)
        activatePreview(shelf, image)
        val overlay = field(shelf, "overlay")!!
        fun start(): (String?) -> Unit {
            OcrResultShadow.pending = null
            call(shelf, "recognizePreview", image)
            assertFalse(field(overlay, "disposed") as Boolean)
            return requireNotNull(OcrResultShadow.pending)
        }

        SegmentResultShadow.received.clear()
        start().invoke(null)
        assertSame(image, state(shelf, "preview"))
        assertTrue(SegmentResultShadow.received.isEmpty())
        SegmentResultShadow.accept = false
        start().invoke("第一次")
        assertFalse(field(overlay, "disposed") as Boolean)
        SegmentResultShadow.accept = true
        start().invoke("第二次")
        assertEquals(listOf("第一次", "第二次"), SegmentResultShadow.received)
        assertTrue(field(overlay, "disposed") as Boolean)
    }

    @Test
    @Config(shadows = [OcrResultShadow::class, SegmentResultShadow::class])
    fun dismissedShelfCannotOpenSegmentationFromALateOcrResult() = withShelf { shelf ->
        val image = entry("image", "group", 0, image = true)
        activatePreview(shelf, image)
        SegmentResultShadow.received.clear()
        OcrResultShadow.pending = null
        call(shelf, "recognizePreview", image)
        call(shelf, "collapse")
        requireNotNull(OcrResultShadow.pending).invoke("过期结果")
        assertTrue(SegmentResultShadow.received.isEmpty())
    }

    @Test
    @Config(shadows = [OcrResultShadow::class, SegmentResultShadow::class])
    fun changingPreviewBeforeOcrCompletesKeepsTheNewImage() = withShelf { shelf ->
        val first = entry("first", "group", 0, image = true)
        val second = entry("second", "group", 1, image = true)
        activatePreview(shelf, first)
        SegmentResultShadow.received.clear()
        call(shelf, "recognizePreview", first)
        state(shelf, "preview", second)
        requireNotNull(OcrResultShadow.pending).invoke("旧图片")
        assertTrue(SegmentResultShadow.received.isEmpty())
        assertSame(second, state(shelf, "preview"))
    }

    @Test
    @Config(shadows = [OcrResultShadow::class, RejectedDialog::class])
    fun rejectedSegmentationWindowKeepsBothImagePreviewAndTextList() = withShelf { shelf ->
        val image = entry("first", "group", 0, image = true)
        val text = entry("text", "group", 1)
        activatePreview(shelf, image)
        val source = field(shelf, "overlay")!!
        ShadowSettings.setCanDrawOverlays(true)
        try {
            RejectedDialog.attempts = 0
            call(shelf, "recognizePreview", image)
            requireNotNull(OcrResultShadow.pending).invoke("识别到文字")
            assertSame(image, state(shelf, "preview"))
            assertFalse(field(source, "disposed") as Boolean)
            state(shelf, "preview", null)
            call(shelf, "openText", text)
            assertFalse(field(source, "disposed") as Boolean)
            assertEquals(2, RejectedDialog.attempts)
        } finally {
            ShadowSettings.setCanDrawOverlays(false)
        }
    }

    @Implements(android.app.Dialog::class)
    class RejectedDialog {
        companion object { var attempts = 0 }
        @Implementation fun show() {
            attempts++
            throw WindowManager.BadTokenException("test window rejected")
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun releasingAndPressingTheSameCardCannotStartTheOldDrag() {
        val held = StashDragPress()
        val first = PressInteraction.Press(Offset.Zero)
        held.observe(first)
        val firstPreparation = requireNotNull(held.dragPermission())
        assertTrue(firstPreparation())
        val second = PressInteraction.Press(Offset.Zero)
        val activity = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().visible()
        val downloads = StashDragImageCacheTest.FakeDownloadsProvider()
        try {
            val view = activity.get().window.decorView
            assertTrue(view.isAttachedToWindow)
            val authority = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI.authority!!
            downloads.attachInfo(view.context, android.content.pm.ProviderInfo().apply { this.authority = authority })
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority, downloads)
            org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowMimeTypeMap>(
                android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png", "image/png")
            val bytes = java.io.ByteArrayOutputStream()
            val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            val image = StashStore(view.context).use { it.addImage(bytes.toByteArray().inputStream(), "image/png") }
            var dispatches = 0
            suspend fun complete(isHeld: () -> Boolean) {
                startStashDrag(view, listOf(image), 0, 0, { _, _, _, _ -> dispatches++; true }, isHeld)
            }
            runBlocking {
                val old = launch(start = CoroutineStart.UNDISPATCHED) { complete(firstPreparation) }
                assertTrue("image copied before dispatch", downloads.published.await(4, java.util.concurrent.TimeUnit.SECONDS))
                held.observe(PressInteraction.Release(first))
                held.observe(second)
                val secondPreparation = requireNotNull(held.dragPermission())
                held.observe(PressInteraction.Cancel(first))
                old.join()
                assertEquals(0, dispatches)
                complete(secondPreparation)
                assertEquals(ShadowToast.getTextOfLatestToast(), 1, dispatches)
                held.observe(PressInteraction.Cancel(second))
                assertFalse(secondPreparation())
                assertNull(held.dragPermission())
            }
        } finally {
            downloads.clearRows()
            activity.pause().stop().destroy()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun resumedModuleActivityKeepsStashOcrAboveTheShelfAndHandsOffToSegmentation() {
        val app = RuntimeEnvironment.getApplication()
        // Robolectric creates a new Application per test; bind the real lifecycle callback to this one.
        val tracker = ForegroundActivity::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        app.registerActivityLifecycleCallbacks(tracker)
        val activity = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        ShadowSettings.setCanDrawOverlays(true)
        try {
            assertSame(activity.get(), ForegroundActivity.resumed())
            withShelf { shelf ->
                val gif = java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
                val image = StashStore(app).use { it.addImage(gif.inputStream(), "image/gif") }
                val shelfWindow = field(shelf, "overlay") as InteractiveOverlayWindow
                activatePreview(shelf, image)
                assertTrue(shelfWindow.show())
                call(shelf, "recognizePreview", image)
                shadowOf(Looper.getMainLooper()).idle()
                val job = requireNotNull(field(ImageOcrOverlay, "active"))
                val ocrWindow = field(job, "window") as InteractiveOverlayWindow
                assertTrue(ocrWindow.window.decorView.isAttachedToWindow)
                assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, ocrWindow.window.attributes.type)
                call(job, "completed", "识别成功")
                shadowOf(Looper.getMainLooper()).idle()
                val segmentation = field(BigBangOverlayLauncher, "active") as InteractiveOverlayWindow
                assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, segmentation.window.attributes.type)
                assertTrue(segmentation.window.decorView.isAttachedToWindow)
                assertTrue(field(shelfWindow, "disposed") as Boolean)
            }
        } finally {
            (field(ImageOcrOverlay, "active")?.let { field(it, "window") } as? InteractiveOverlayWindow)?.dismiss()
            (field(BigBangOverlayLauncher, "active") as? InteractiveOverlayWindow)?.dismiss()
            ShadowSettings.setCanDrawOverlays(false)
            activity.pause().stop().destroy()
            app.unregisterActivityLifecycleCallbacks(tracker)
        }
    }

    private fun activatePreview(shelf: Any, image: StashStore.Entry) {
        StashOverlayPanel::class.java.getDeclaredField("active").apply { isAccessible = true }
            .set(StashOverlayPanel, shelf)
        state(shelf, "entries", listOf(image))
        state(shelf, "preview", image)
    }

    @Implements(value = ImageOcrOverlay::class, isInAndroidSdk = false)
    class OcrResultShadow {
        companion object {
            var pending: ((String?) -> Unit)? = null
            @JvmStatic @Implementation
            fun startWithResult(context: Context, uri: Uri, onResult: (String?) -> Unit): Boolean {
                pending = onResult
                return true
            }
        }
    }

    @Implements(value = BigBangOverlayLauncher::class, isInAndroidSdk = false)
    class SegmentResultShadow {
        companion object {
            val received = mutableListOf<String>()
            var accept = true
            @JvmStatic @Implementation
            fun showText(context: Context, text: String, x: Int, y: Int): Boolean {
                received.add(text)
                return accept
            }
        }
    }

    @Test fun rejectedEmbeddedDropExplainsThatTheStashItemWasKept() = withShelf { shelf ->
        val overlay = field(shelf, "overlay") as InteractiveOverlayWindow

        call(shelf, "finishEmbeddedDrag", false)

        assertFalse(field(overlay, "disposed") as Boolean)
        assertEquals("目标应用未确认接收，内容仍保留在中转站",
            ShadowToast.getTextOfLatestToast().toString())
    }

    @Test fun savingContentDoesNotCreateAnInactiveCollapsedOverlay() {
        val active = StashOverlayPanel::class.java.getDeclaredField("active").apply { isAccessible = true }
        active.set(StashOverlayPanel, null)
        StashOverlayPanel.contentAdded(RuntimeEnvironment.getApplication())
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(active.get(StashOverlayPanel))
    }

    @Test fun groupBrowsingDoesNotTruncateTheDragSelectionOrReverseItsOrder() = withShelf { shelf ->
        val first = entry("first", "group", 0)
        val second = entry("second", "group", 1)
        val other = entry("other", "other", 0)
        state(shelf, "entries", listOf(second, other, first))
        state(shelf, "selectedIds", setOf(first.id, second.id, other.id))
        state(shelf, "selectionMode", true)
        state(shelf, "openedBatchId", other.batchId)

        @Suppress("UNCHECKED_CAST")
        val selected = call(shelf, "selectedEntries") as List<StashStore.Entry>
        val clip = clipForStash(selected)
        assertEquals(3, clip.itemCount)
        assertEquals(listOf("first", "second", "other"),
            (0 until clip.itemCount).map { clip.getItemAt(it).text.toString() })
    }

    @Test fun cancelledExpandedWindowMoveRestoresPositionWithoutSavingANewEdge() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("stash_window_position", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("left", true).putFloat("center_y", .4f).commit()
        withShelf { shelf ->
            call(shelf, "resize", false)
            val overlay = field(shelf, "overlay") as InteractiveOverlayWindow
            val startX = overlay.window.attributes.x
            val startY = overlay.window.attributes.y
            fun move(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
                try { call(shelf, "handleMove", event) } finally { event.recycle() }
            }
            move(MotionEvent.ACTION_DOWN, startX + 5f, startY + 5f)
            move(MotionEvent.ACTION_MOVE, startX + 605f, startY + 105f)
            assertNotEquals(startX, overlay.window.attributes.x)
            move(MotionEvent.ACTION_CANCEL, startX + 605f, startY + 105f)

            assertEquals(startX, overlay.window.attributes.x)
            assertEquals(startY, overlay.window.attributes.y)
            assertEquals(.4f, field(shelf, "centerY") as Float, 0f)
            assertTrue(preferences.getBoolean("left", false))
            assertEquals(.4f, preferences.getFloat("center_y", 0f), 0f)
            assertNull(field(shelf, "dragLeft"))
            assertFalse(field(shelf, "moving") as Boolean)
        }
    }

    @Test fun selectingHundredsOfEntriesReusesGroupsUntilTheEntriesChange() = withShelf { shelf ->
        for (size in listOf(200, 500)) {
            val values = List(size) { entry("id$it", "id$it", 0) }
            var reads = 0
            val counted = object : AbstractList<StashStore.Entry>() {
                override val size get() = values.size
                override fun get(index: Int): StashStore.Entry { reads++; return values[index] }
            }
            state(shelf, "entries", counted)
            state(shelf, "selectedIds", values.map { it.id }.toSet())
            reads = 0
            assertEquals(size, (call(shelf, "selectedEntries") as List<*>).size)
            assertEquals(false, call(shelf, "canUngroupSelection"))
            assertEquals("one traversal at $size entries", size, reads)
            state(shelf, "selectedIds", setOf(values.first().id))
            assertEquals(false, call(shelf, "canUngroupSelection"))
            assertEquals(size, reads)
        }
        state(shelf, "entries", listOf(entry("a", "group", 0), entry("b", "group", 1)))
        state(shelf, "selectedIds", setOf("a"))
        assertEquals(true, call(shelf, "canUngroupSelection"))
        state(shelf, "entries", listOf(entry("a", "a", 0), entry("b", "b", 0)))
        assertEquals(false, call(shelf, "canUngroupSelection"))
    }

    private fun withShelf(test: (Any) -> Unit) {
        val constructor = Class.forName("com.leaf.hyperdragshare.codex.StashOverlayPanel\$Shelf")
            .getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val shelf = constructor.newInstance(RuntimeEnvironment.getApplication())
        // An unattached WindowContext has empty max bounds in Robolectric.
        val context = field(shelf, "context") as Context
        val configuration = ReflectionHelpers.getField<Any>(context.resources.configuration, "windowConfiguration")
        ReflectionHelpers.callInstanceMethod<Any>(configuration, "setMaxBounds",
            ClassParameter.from(Rect::class.java, Rect(0, 0, 1000, 2000)))
        try { test(shelf) } finally { (field(shelf, "overlay") as InteractiveOverlayWindow).dismiss() }
    }

    private fun entry(id: String, batch: String, order: Int, image: Boolean = false): StashStore.Entry {
        val constructor = StashStore.Entry::class.java.declaredConstructors.single().apply { isAccessible = true }
        return constructor.newInstance(id, if (image) StashStore.KIND_IMAGE else StashStore.KIND_TEXT,
            if (image) null else id, if (image) id else null, if (image) "image/png" else null,
            1L, batch, order) as StashStore.Entry
    }

    private fun call(shelf: Any, name: String, vararg arguments: Any?): Any? =
        shelf.javaClass.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
            .apply { isAccessible = true }.invoke(shelf, *arguments)

    private fun field(shelf: Any, name: String): Any? =
        shelf.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(shelf)

    private fun state(shelf: Any, name: String): Any? =
        (field(shelf, "$name\$delegate") as MutableState<*>).value

    @Suppress("UNCHECKED_CAST")
    private fun state(shelf: Any, name: String, value: Any?) {
        (field(shelf, "$name\$delegate") as MutableState<Any?>).value = value
    }
}
