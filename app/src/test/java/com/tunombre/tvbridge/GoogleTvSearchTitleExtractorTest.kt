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

    @Test
    fun recognizesGoogleTvWatchProviderAction() {
        assertEquals(
            true,
            GoogleTvSearchTitleExtractor.isProviderAction(
                packageName = "com.google.android.apps.tv.launcherx",
                contentDescription = "Watch now Hulu"
            )
        )
    }

    @Test
    fun extractsPlotFromHomeProviderCard() {
        assertEquals(
            "Two lighthouse keepers lose their sanity on an island",
            GoogleTvSearchTitleExtractor.homeProviderPlot(
                packageName = "com.google.android.apps.tv.launcherx",
                eventText = listOf(
                    "Recommended For You",
                    "Two lighthouse keepers lose their sanity on an island",
                    "Watch Now"
                )
            )
        )
    }

    @Test
    fun extractsTitleFromGoogleTvSemanticSearchResponse() {
        assertEquals(
            "The Lighthouse",
            GoogleTvSearchTitleExtractor.semanticResultTitle(
                query = "Two lighthouse keepers lose their sanity on an island",
                visibleTexts = listOf(
                    "Two lighthouse keepers lose their sanity on an island",
                    "The Lighthouse (2019) is a psychological horror film starring Robert Pattinson and Willem Dafoe.",
                    "2019dotHorrordot1 hr 50 min",
                    "View details"
                )
            )
        )
    }
}
