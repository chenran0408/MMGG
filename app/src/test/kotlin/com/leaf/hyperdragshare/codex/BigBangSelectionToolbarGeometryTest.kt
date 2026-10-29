package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class BigBangSelectionToolbarGeometryTest {
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
