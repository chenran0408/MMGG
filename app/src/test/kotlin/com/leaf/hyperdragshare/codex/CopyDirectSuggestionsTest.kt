package com.leaf.hyperdragshare.codex

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CopyDirectSuggestionsTest {
    @Test
    fun panelOffersOnlyTheActionForTheCompleteTarget() {
        fun destination(text: String) = requireNotNull(directTextDestination(text, wholeTextOnly = true))
        val url = destination(" https://example.com/a?q=1 ")
        assertEquals(Intent.ACTION_VIEW, url.intent.action)
        assertEquals("https://example.com/a?q=1", url.intent.dataString)
        assertEquals("https://www.example.com/test", destination("www.example.com/test").intent.dataString)
        for (text in listOf("user@example.org", "mailto:user@example.org")) {
            val email = destination(text)
            assertEquals(Intent.ACTION_SENDTO, email.intent.action)
            assertEquals("mailto:user@example.org", email.intent.dataString)
        }
        for (text in listOf("+86 138 0013 8000", "tel:+86 138 0013 8000")) {
            val phone = destination(text)
            assertEquals(Intent.ACTION_DIAL, phone.intent.action)
            assertEquals("+8613800138000", phone.intent.data?.schemeSpecificPart)
        }
    }

    @Test
    fun panelLeavesAmbiguousTextForManualRecognition() {
        val texts = listOf(
            "这是一段普通文字",
            "详情见 https://example.com",
            "user@example.org 或 https://example.com",
            "https://one.example.com https://two.example.com",
            "https://one.example.com/path、https://two.example.com/path",
            "01012345678\n02112345678",
            "1234567 7654321",
            "2026-11-21",
            "content://example.com/photo",
        )
        for (value in texts) assertNull(value, directTextDestination(value, wholeTextOnly = true))
        assertEquals("https://example.com", directTextDestination("详情见 https://example.com")?.intent?.dataString)
    }
}
