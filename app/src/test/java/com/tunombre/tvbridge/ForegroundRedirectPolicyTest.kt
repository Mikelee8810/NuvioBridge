package com.tunombre.tvbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRedirectPolicyTest {

    @Test
    fun `blocks an original provider while a Nuvio redirect is pending`() {
        assertTrue(
            ForegroundRedirectPolicy.shouldBlockUnexpectedApp(
                packageName = "com.netflix.ninja",
                redirectPending = true
            )
        )
    }

    @Test
    fun `does not block Nuvio while a redirect is pending`() {
        assertFalse(
            ForegroundRedirectPolicy.shouldBlockUnexpectedApp(
                packageName = "com.nuvio.tv",
                redirectPending = true
            )
        )
    }

    @Test
    fun `does not block providers after the redirect window ends`() {
        assertFalse(
            ForegroundRedirectPolicy.shouldBlockUnexpectedApp(
                packageName = "com.netflix.ninja",
                redirectPending = false
            )
        )
    }

    @Test
    fun `recognizes official YouTube TV packages`() {
        assertTrue(ForegroundRedirectPolicy.isYoutubePackage("com.google.android.youtube.tv"))
        assertTrue(ForegroundRedirectPolicy.isYoutubePackage("com.google.android.apps.youtube.leanback"))
        assertFalse(ForegroundRedirectPolicy.isYoutubePackage("org.smarttube.stable"))
    }

    @Test
    fun `formats accessibility text even when an app supplies null entries`() {
        assertTrue(
            AccessibilityEventTextFormatter.format(listOf("Video", null, "Playing"))
                .contains("Video")
        )
    }

    @Test
    fun `recognizes the Play Store YouTube install screen opened by a Home card`() {
        assertTrue(
            ForegroundRedirectPolicy.isYoutubeInstallScreen(
                packageName = "com.android.vending",
                visibleTexts = listOf("YouTube for Android TV", "Install")
            )
        )
        assertFalse(
            ForegroundRedirectPolicy.isYoutubeInstallScreen(
                packageName = "com.android.vending",
                visibleTexts = listOf("Netflix", "Install")
            )
        )
    }
}
