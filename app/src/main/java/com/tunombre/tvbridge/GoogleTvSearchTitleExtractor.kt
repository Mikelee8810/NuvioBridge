package com.tunombre.tvbridge

object GoogleTvSearchTitleExtractor {
    private const val GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"

    fun extract(visibleTexts: List<String>): String? =
        visibleTexts.firstOrNull { it.isNotBlank() }?.trim()

    fun isDetailsAction(packageName: String, eventText: List<String>): Boolean =
        packageName == GOOGLE_TV_LAUNCHER_PACKAGE &&
            eventText.any { it.equals("View details", ignoreCase = true) }

    fun isProviderAction(packageName: String, contentDescription: String?): Boolean =
        packageName == GOOGLE_TV_LAUNCHER_PACKAGE &&
            contentDescription?.startsWith("Watch now ", ignoreCase = true) == true
}
