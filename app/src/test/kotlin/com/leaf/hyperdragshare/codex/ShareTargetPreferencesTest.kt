package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTargetPreferencesTest {
    @Test
    fun bulkVisibilityChangesOnlyTheTargetsInTheCurrentCatalog() {
        val previouslyHidden = setOf("not-installed", "app-a")
        val catalog = listOf("app-a", "app-b")

        assertEquals(setOf("not-installed"), updateTargetVisibility(previouslyHidden, catalog, true))
        assertEquals(
            setOf("not-installed", "app-a", "app-b"),
            updateTargetVisibility(previouslyHidden, catalog, false),
        )
    }

    @Test
    fun sortingWithAnAppHiddenKeepsItsSlotWhenTheAppReturns() {
        val reordered = reorderVisibleTargets(
            listOf("app-a", "hidden-app", "app-b", "not-installed"),
            listOf("app-b", "new-app", "app-a"),
        )

        assertEquals(listOf("app-b", "hidden-app", "new-app", "not-installed", "app-a"), reordered)
        assertEquals(listOf("app-b", "new-app", "app-a"), reordered.filter { it !in setOf("hidden-app", "not-installed") })
    }
}
