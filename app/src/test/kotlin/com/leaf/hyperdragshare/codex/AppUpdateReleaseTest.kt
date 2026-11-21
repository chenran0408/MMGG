package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AppUpdateReleaseTest {
    private val releases = "https://github.com/chenran0408/MMGG/releases"
    private val asset = "MMGG-2.0.0-arm64-v8a.apk"
    @Test fun candidateFindsStableArm64ApkAtCurrentRepository() {
        val seen = mutableListOf<String>()
        val fragments = "$releases/expanded_assets/v2.0.0"
        val info = fetchLatestRelease { url ->
            seen += url
            when (url) {
                "$releases/latest" -> "<include-fragment src='$fragments'>" to "$releases/tag/v2.0.0"
                fragments -> "<a href='/chenran0408/MMGG/releases/download/v2.0.0/$asset'>" to fragments
                else -> error("Unexpected release request $url")
            }
        }
        assertEquals(listOf("$releases/latest", fragments), seen)
        assertEquals("$releases/download/v2.0.0/$asset", info.downloadUrl)
        assertEquals("2.0.0", info.versionName)
    }
    @Test fun aDifferentRepositoryAfterRedirectIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            fetchLatestRelease { "" to "https://github.com/another/MMGG/releases/tag/v2.0.0" }
        }
    }
}
