package com.tunombre.tvbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleTvSearchTitleExtractorTest {

    @Test
    fun extractsMovieTitleFromGoogleTvEntityScreen() {
        val visibleTexts = listOf(
            "Iron Man",
            "What it's about",
            "A wealthy industrialist builds an armored suit",
            "Trailer",
            "Watchlist"
        )

        assertEquals("Iron Man", GoogleTvSearchTitleExtractor.extract(visibleTexts))
    }

    @Test
    fun recognizesGoogleTvViewDetailsAction() {
        assertEquals(
            true,
            GoogleTvSearchTitleExtractor.isDetailsAction(
                packageName = "com.google.android.apps.tv.launcherx",
                eventText = listOf("View details")
            )
        )
    }
}
