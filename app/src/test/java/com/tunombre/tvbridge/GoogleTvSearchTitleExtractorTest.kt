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

    @Test
    fun extractsTitleFromGoogleTvEntityDetailsScreen() {
        assertEquals(
            "SpongeBob SquarePants",
            GoogleTvSearchTitleExtractor.entityDetailsTitle(
                query = "SpongeBob must recover the secret Krabby Patty formula",
                visibleTexts = listOf(
                    "SpongeBob SquarePants",
                    "TRENDING",
                    "TV Y7",
                    "Cartoon",
                    "1999 - Present",
                    "What it's about"
                )
            )
        )
        assertEquals(
            null,
            GoogleTvSearchTitleExtractor.entityDetailsTitle(
                query = "SpongeBob must recover the secret Krabby Patty formula",
                visibleTexts = listOf(
                    "SpongeBob must recover the secret Krabby Patty formula",
                    "The mystery of the Krabby Patty formula is a major theme in SpongeBob SquarePants.",
                    "View details"
                )
            )
        )
    }

    @Test
    fun rejectsStaleSemanticResponseFromPriorSearch() {
        val spongeBobQuery = "SpongeBob must recover the secret Krabby Patty formula"

        assertEquals(
            false,
            GoogleTvSearchTitleExtractor.isSemanticResponseForQuery(
                spongeBobQuery,
                listOf(
                    spongeBobQuery,
                    "Iron Man is a 2008 superhero film starring Robert Downey Jr.",
                    "Watch now Hulu",
                    "View details"
                )
            )
        )
        assertEquals(
            true,
            GoogleTvSearchTitleExtractor.isSemanticResponseForQuery(
                spongeBobQuery,
                listOf(
                    spongeBobQuery,
                    "The mystery of the Krabby Patty formula is a major theme in SpongeBob SquarePants.",
                    "Watch now Prime Video",
                    "View details"
                )
            )
        )
        assertEquals(
            true,
            GoogleTvSearchTitleExtractor.isSemanticResponseForQuery(
                "Two lighthouse keepers lose their sanity on an island",
                listOf(
                    "Two lighthouse keepers lose their sanity on an island",
                    "The Lighthouse (2019) is a psychological horror film.",
                    "View details"
                )
            )
        )
    }
}
