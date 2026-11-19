package com.leaf.hyperdragshare.codex

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.State
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BigBangPanelSessionTest {
    @Test
    fun undoRestoresTextWithoutChangingHowThePanelWasOpened() {
        for (dragPreview in listOf(true, false)) {
            val session = BigBangPanelSession(RuntimeEnvironment.getApplication(), object : BigBangPanelSession.Actions {
                override fun dismiss() = error("The restored text should stay open")
                override fun collapse() = Unit
                override fun searchDirect(text: String) = Unit
                override fun search(text: String, type: Int) = Unit
                override fun dragText(source: View, text: String) = false
                override fun openExternal(intent: Intent) = Unit
            })
            try {
                val state = savedText("edited").apply {
                    putBundle(BigBangPanelSession.UNDO_EDIT, savedText("original"))
                }
                session.open("edited", -1, -1, state, dragPreview = dragPreview)
                BigBangPanelSession::class.java
                    .getDeclaredMethod("handleMoreAction", BigBangMoreAction::class.java)
                    .apply { isAccessible = true }
                    .invoke(session, BigBangMoreAction.UndoEdit)

                val restored = Bundle().also(session::saveState)
                assertEquals("original", restored.getString("saved_text"))
                val mode = BigBangPanelSession::class.java.getDeclaredField("dragPreviewEnabled\$delegate")
                    .apply { isAccessible = true }.get(session) as State<*>
                assertEquals(dragPreview, mode.value)
            } finally {
                session.close()
            }
        }
    }

    private fun savedText(text: String) = Bundle().apply {
        putString("saved_text", text)
        putIntArray("saved_segment", intArrayOf(0, text.length - 1, -1))
    }
}
