package com.leaf.hyperdragshare.codex

/** The edge is captured before the tray moves. All positions are in display coordinates. */
internal class StashDragEdge(
    private val edgeX: Float,
    private val onLeft: Boolean,
    density: Float,
    expandedWidth: Int,
    private val startedAt: Long,
) {
    private val enterDistance = 24f * density
    private val exitDistance = minOf(130f * density, expandedWidth * .8f)
        .coerceAtLeast(enterDistance)
    var aside = true
        private set

    fun move(screenX: Float, now: Long): Boolean {
        if (now - startedAt < 260L) return false
        val distance = if (onLeft) screenX - edgeX else edgeX - screenX
        val nextAside = if (aside) distance > enterDistance else distance > exitDistance
        if (nextAside == aside) return false
        aside = nextAside
        return true
    }
}
