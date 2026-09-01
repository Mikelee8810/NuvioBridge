package com.tunombre.tvbridge

object ForegroundRedirectPolicy {
    private val youtubePackages = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.leanback"
    )

    private val allowedDuringNuvioRedirect = setOf(
        "com.nuvio.tv",
        "com.tunombre.tvbridge",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.amazon.tv.launcher",
        "org.smarttube.stable",
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv"
    )

    fun isYoutubePackage(packageName: String): Boolean = packageName in youtubePackages

    fun isYoutubeInstallScreen(packageName: String, visibleTexts: List<String>): Boolean =
        packageName == "com.android.vending" &&
            visibleTexts.any { it.contains("YouTube for Android TV", ignoreCase = true) }

    fun shouldBlockUnexpectedApp(packageName: String, redirectPending: Boolean): Boolean =
        redirectPending && packageName !in allowedDuringNuvioRedirect
}
