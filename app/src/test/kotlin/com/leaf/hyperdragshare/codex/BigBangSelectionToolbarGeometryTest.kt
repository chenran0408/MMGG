package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class BigBangSelectionToolbarGeometryTest {
    @Test
    fun headerStacksWhenWidthOrFontScaleLeavesTooLittleSpaceForItsControls() {
        assertTrue(bigBangHeaderNeedsStack(250.dp, 0.dp, 1.3f))
        assertFalse(bigBangHeaderNeedsStack(380.dp, 0.dp, 1.3f))
        assertTrue(bigBangHeaderNeedsStack(380.dp, 40.dp, 1.4f))
        assertTrue(bigBangHeaderNeedsStack(530.dp, 40.dp, 2f))
        assertFalse(bigBangHeaderNeedsStack(570.dp, 40.dp, 2f))
    }

    @Test
    fun toolbarVisibilityDoesNotChangeDuringAnActiveSelectionGesture() {
        assertEquals(false, selectionToolbarVisibleAfterGesture(false, true, true))
        assertEquals(true, selectionToolbarVisibleAfterGesture(true, false, true))
    }

    @Test
    fun toolbarVisibilityFollowsFinalSelectionAfterGesture() {
        assertEquals(true, selectionToolbarVisibleAfterGesture(false, true, false))
        assertEquals(false, selectionToolbarVisibleAfterGesture(true, false, false))
    }
}
