package com.tunombre.tvbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioLauncherTest {

    @Test
    fun movieUsesNuvioMovieDeepLink() {
        val match = TmdbMatch("tt0371746", MediaType.MOVIE)

        assertEquals("nuvio://movie/tt0371746", NuvioLauncher.deepLink(match))
    }

    @Test
    fun seriesUsesNuvioTvDeepLink() {
        val match = TmdbMatch("tt0944947", MediaType.SERIES)

        assertEquals("nuvio://detail/tv/tt0944947", NuvioLauncher.deepLink(match))
    }
}
