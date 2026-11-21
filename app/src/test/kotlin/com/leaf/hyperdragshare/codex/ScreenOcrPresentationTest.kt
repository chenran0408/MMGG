package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOcrPresentationTest {
    @Test
    fun resultSummaryKeepsLineCountAndSelectionTextInDisplayOrder() {
        val summary = screenOcrResultSummary(listOf("第一行", "第二行", "第三行"))

        assertEquals(3, summary.lineCount)
        assertEquals("第一行\n第二行\n第三行", summary.allText)
    }

    @Test
    fun emptyResultSummaryDoesNotInventSelectionText() {
        val summary = screenOcrResultSummary(emptyList())

        assertEquals(0, summary.lineCount)
        assertEquals("", summary.allText)
    }
}
