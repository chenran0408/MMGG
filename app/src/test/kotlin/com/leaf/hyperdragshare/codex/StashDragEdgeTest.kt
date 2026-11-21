package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StashDragEdgeTest {
    @Test fun leftEdgeMovesAsideUntilTheDragReturnsAndUsesHysteresis() {
        val edge = StashDragEdge(edgeX = 12f, onLeft = true, density = 2f,
            expandedWidth = 400, startedAt = 1_000L)
        assertTrue(edge.aside)
        assertFalse(edge.move(12f, 1_259L))
        assertTrue(edge.aside)
        assertFalse(edge.move(61f, 1_260L))

        assertTrue(edge.move(60f, 1_260L))
        assertFalse(edge.aside)
        assertFalse(edge.move(272f, 1_300L))
        assertFalse(edge.aside)
        assertTrue(edge.move(273f, 1_301L))
        assertTrue(edge.aside)
        assertFalse(edge.move(62f, 1_400L))
    }

    @Test fun rightEdgeUsesScreenCoordinatesAndCanLeaveANarrowTray() {
        val edge = StashDragEdge(edgeX = 980f, onLeft = false, density = 2f,
            expandedWidth = 150, startedAt = 5_000L)
        assertFalse(edge.move(931f, 5_300L))
        assertTrue(edge.move(932f, 5_301L))
        assertFalse(edge.aside)
        assertFalse(edge.move(860f, 5_350L))
        assertTrue(edge.move(859f, 5_351L))
        assertTrue(edge.aside)
    }
}
