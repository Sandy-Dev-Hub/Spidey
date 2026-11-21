package com.spidey.assistant

import com.spidey.assistant.apps.matcher.AppMatchResult
import com.spidey.assistant.apps.matcher.AppMatcher
import com.spidey.assistant.apps.model.AppRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {

    private val matcher = AppMatcher()

    private val installedApps = listOf(
        AppRecord(label = "WhatsApp", packageName = "com.whatsapp"),
        AppRecord(label = "Google Chrome", packageName = "com.android.chrome"),
        AppRecord(label = "YouTube", packageName = "com.google.android.youtube"),
        AppRecord(label = "Instagram", packageName = "com.instagram.android"),
        AppRecord(label = "Spotify", packageName = "com.spotify.music"),
        AppRecord(label = "Settings", packageName = "com.android.settings"),
        AppRecord(label = "Camera", packageName = "com.sec.android.app.camera"),
        AppRecord(label = "Phone", packageName = "com.samsung.android.dialer"),
        AppRecord(label = "Contacts", packageName = "com.samsung.android.app.contacts"),
        AppRecord(label = "HDFC Bank", packageName = "com.snapwork.hdfc"),
        AppRecord(label = "Calculator", packageName = "com.sec.android.app.popupcalculator")
    )

    @Test
    fun testExactMatch() {
        val res = matcher.match("Instagram", installedApps)
        assertTrue(res is AppMatchResult.Resolved)
        val resolved = res as AppMatchResult.Resolved
        assertEquals("Instagram", resolved.app.label)
        assertEquals("com.instagram.android", resolved.app.packageName)
        assertEquals(1.0, resolved.score, 0.01)
    }

    @Test
    fun testAliasMatching() {
        // "insta" -> Instagram
        val insta = matcher.match("insta", installedApps)
        assertTrue(insta is AppMatchResult.Resolved)
        assertEquals("com.instagram.android", (insta as AppMatchResult.Resolved).app.packageName)

        // "wa" -> WhatsApp
        val wa = matcher.match("wa", installedApps)
        assertTrue(wa is AppMatchResult.Resolved)
        assertEquals("com.whatsapp", (wa as AppMatchResult.Resolved).app.packageName)

        // "yt" -> YouTube
        val yt = matcher.match("yt", installedApps)
        assertTrue(yt is AppMatchResult.Resolved)
        assertEquals("com.google.android.youtube", (yt as AppMatchResult.Resolved).app.packageName)

        // "calc" -> Calculator
        val calc = matcher.match("calc", installedApps)
        assertTrue(calc is AppMatchResult.Resolved)
        assertEquals("com.sec.android.app.popupcalculator", (calc as AppMatchResult.Resolved).app.packageName)

        // "cam" -> Camera
        val cam = matcher.match("cam", installedApps)
        assertTrue(cam is AppMatchResult.Resolved)
        assertEquals("com.sec.android.app.camera", (cam as AppMatchResult.Resolved).app.packageName)

        // "browser" -> Chrome
        val browser = matcher.match("browser", installedApps)
        assertTrue(browser is AppMatchResult.Resolved)
        assertEquals("com.android.chrome", (browser as AppMatchResult.Resolved).app.packageName)
    }

    @Test
    fun testFuzzyMatching() {
        // Misspelled "whatsap" -> WhatsApp
        val whatsap = matcher.match("whatsap", installedApps)
        assertTrue("Expected Resolved for 'whatsap', got $whatsap", whatsap is AppMatchResult.Resolved)
        assertEquals("WhatsApp", (whatsap as AppMatchResult.Resolved).app.label)

        // Misspelled "spotfy" -> Spotify
        val spotfy = matcher.match("spotfy", installedApps)
        assertTrue("Expected Resolved for 'spotfy', got $spotfy", spotfy is AppMatchResult.Resolved)
        assertEquals("Spotify", (spotfy as AppMatchResult.Resolved).app.label)

        // Misspelled "youtub" -> YouTube
        val youtub = matcher.match("youtub", installedApps)
        assertTrue("Expected Resolved for 'youtub', got $youtub", youtub is AppMatchResult.Resolved)
        assertEquals("YouTube", (youtub as AppMatchResult.Resolved).app.label)

        // Misspelled "chrom" -> Google Chrome
        val chrom = matcher.match("chrom", installedApps)
        assertTrue("Expected Resolved for 'chrom', got $chrom", chrom is AppMatchResult.Resolved)
        assertEquals("Google Chrome", (chrom as AppMatchResult.Resolved).app.label)
    }

    @Test
    fun testAmbiguousMatches() {
        val appsWithTie = listOf(
            AppRecord(label = "WhatsApp", packageName = "com.whatsapp"),
            AppRecord(label = "WhatsApp Business", packageName = "com.whatsapp.w4b")
        )

        val res = matcher.match("whatsapp", appsWithTie)
        // Exact match takes precedence
        assertTrue(res is AppMatchResult.Resolved)

        // Very close tie when query is partial or misheard
        val tieApps = listOf(
            AppRecord(label = "Paytm Money", packageName = "com.paytm.money"),
            AppRecord(label = "Paytm Payments", packageName = "com.paytm.payments")
        )
        val ambiguousRes = matcher.match("paytm", tieApps)
        assertTrue("Expected Ambiguous, got $ambiguousRes", ambiguousRes is AppMatchResult.Ambiguous)
        val amb = ambiguousRes as AppMatchResult.Ambiguous
        assertEquals(2, amb.candidates.size)
    }

    @Test
    fun testNonExistentAppReturnsNotFound() {
        val res = matcher.match("Flappy Bird 2026 NonExistentApp", installedApps)
        assertTrue("Expected NotFound, got $res", res is AppMatchResult.NotFound)
    }
}

