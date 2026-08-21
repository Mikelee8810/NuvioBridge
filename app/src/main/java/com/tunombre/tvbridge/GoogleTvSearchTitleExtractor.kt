package com.tunombre.tvbridge

object GoogleTvSearchTitleExtractor {
    private const val GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"
    private val GOOGLE_TV_SEARCH_PACKAGES = setOf(
        GOOGLE_TV_LAUNCHER_PACKAGE,
        "com.google.android.googlequicksearchbox",
        "com.google.android.tvlauncher"
    )
    private val SEMANTIC_STOP_WORDS = setOf(
        "about", "after", "their", "there", "these", "those", "watch", "where", "which"
    )

    fun extract(visibleTexts: List<String>): String? =
        visibleTexts.firstOrNull { it.isNotBlank() }?.trim()

    fun isDetailsAction(packageName: String, eventText: List<String>): Boolean =
        packageName in GOOGLE_TV_SEARCH_PACKAGES &&
            eventText.any { it.equals("View details", ignoreCase = true) }

    fun isProviderAction(packageName: String, contentDescription: String?): Boolean =
        packageName in GOOGLE_TV_SEARCH_PACKAGES &&
            contentDescription?.contains(
                Regex("\\b(watch now|watch on|play on|available on)\\b", RegexOption.IGNORE_CASE)
            ) == true

    fun homeProviderPlot(packageName: String, eventText: List<String>): String? {
        if (packageName != GOOGLE_TV_LAUNCHER_PACKAGE) return null
        if (!eventText.firstOrNull().equals("Recommended For You", ignoreCase = true)) return null
        if (!eventText.lastOrNull().equals("Watch Now", ignoreCase = true)) return null
        return eventText.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun semanticResultTitle(query: String, visibleTexts: List<String>): String? {
        val titlePattern = Regex("^(.+?) \\(\\d{4}\\) (?:is|was)\\b", RegexOption.IGNORE_CASE)
        return visibleTexts
            .asSequence()
            .filterNot { it.equals(query, ignoreCase = true) }
            .mapNotNull { titlePattern.find(it.trim())?.groupValues?.getOrNull(1) }
            .firstOrNull()
    }

    fun entityDetailsTitle(query: String, visibleTexts: List<String>): String? {
        if (visibleTexts.any { it.equals("View details", ignoreCase = true) }) return null
        return visibleTexts
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeUnless { it.equals(query, ignoreCase = true) }
    }

    fun isSemanticResponseForQuery(query: String, visibleTexts: List<String>): Boolean {
        val queryTokens = semanticTokens(query)
        if (queryTokens.isEmpty()) return false
        val responseTokens = visibleTexts
            .asSequence()
            .filterNot { it.equals(query, ignoreCase = true) }
            .flatMap { semanticTokens(it).asSequence() }
            .toSet()
        return queryTokens.any(responseTokens::contains)
    }

    private fun semanticTokens(value: String): Set<String> =
        value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 5 && it !in SEMANTIC_STOP_WORDS }
            .toSet()
}
