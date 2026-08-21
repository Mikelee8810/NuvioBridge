package com.tunombre.tvbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/**
 * Accessibility service that listens for recommendation clicks in Google TV
 * and Fire TV launchers, resolves the selected title through TMDB, and opens
 * the matching movie or series directly in Nuvio.
 */
class TvRecommendationAccessibilityService : AccessibilityService() {

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var pendingGoogleTvSearchTitle: String? = null
    private var pendingSemanticDetailsClick = false

    companion object {
        private const val TAG = "TvRecService"
        private val TITLE_MARKERS = listOf("cuesta:", "se necesita una suscripción a", "puntuación:")
        private const val AMAZON_LAUNCHER_PACKAGE = "com.amazon.tv.launcher"
        private const val GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"
        private const val LEGACY_GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.tvlauncher"
        private const val GOOGLE_TV_RECOMMENDATIONS_PACKAGE = "com.google.android.tvrecommendations"
        private const val GOOGLE_TV_ASSISTANT_PACKAGE = "com.google.android.katniss"
        private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val FIRE_TV_MAIN_IMAGE_ID = "com.amazon.tv.launcher:id/main_image"
        private const val SEARCH_DETAILS_RETRY_DELAY_MS = 250L
        private const val SEARCH_DETAILS_MAX_ATTEMPTS = 8
        private const val HOME_PROVIDER_SEARCH_START_DELAY_MS = 700L
        private const val HOME_PROVIDER_SEARCH_RETRY_DELAY_MS = 500L
        private const val HOME_PROVIDER_SEARCH_MAX_ATTEMPTS = 30
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            packageNames = arrayOf(
                GOOGLE_TV_LAUNCHER_PACKAGE,
                LEGACY_GOOGLE_TV_LAUNCHER_PACKAGE,
                GOOGLE_TV_RECOMMENDATIONS_PACKAGE,
                GOOGLE_TV_ASSISTANT_PACKAGE,
                GOOGLE_SEARCH_PACKAGE,
                AMAZON_LAUNCHER_PACKAGE
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SELECTED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) return
        val packageName = event.packageName?.toString() ?: return
        Log.d(
            TAG,
            "Event type=$eventType package=$packageName class=${event.className} " +
                "description=${event.contentDescription?.toString()?.take(160)} " +
                "text=${event.text.joinToString(limit = 3, truncated = "…") { it.toString() }}"
        )
        if (packageName !in setOf(
                GOOGLE_TV_LAUNCHER_PACKAGE,
                LEGACY_GOOGLE_TV_LAUNCHER_PACKAGE,
                GOOGLE_TV_RECOMMENDATIONS_PACKAGE,
                GOOGLE_TV_ASSISTANT_PACKAGE,
                GOOGLE_SEARCH_PACKAGE,
                AMAZON_LAUNCHER_PACKAGE
            )) return
        val homeProviderPlot = GoogleTvSearchTitleExtractor.homeProviderPlot(
            packageName,
            event.text.map { it.toString() }
        )
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && homeProviderPlot != null) {
            Log.d(TAG, "Google TV home provider plot detected: $homeProviderPlot")
            routeHomeProviderPlot(homeProviderPlot)
            return
        }
        val isSearchRouteAction = GoogleTvSearchTitleExtractor.isDetailsAction(
                packageName,
                event.text.map { it.toString() }
            ) || GoogleTvSearchTitleExtractor.isProviderAction(
                packageName,
                event.contentDescription?.toString()
            )
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            if (isSearchRouteAction) {
                pendingGoogleTvSearchTitle = readActiveGoogleTvSearchTitle()
                Log.d(
                    TAG,
                    "Google TV search title cached: $pendingGoogleTvSearchTitle " +
                        "instance=${System.identityHashCode(this)} thread=${Thread.currentThread().name}"
                )
            }
            return
        }
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isSearchRouteAction) {
            if (pendingSemanticDetailsClick && GoogleTvSearchTitleExtractor.isDetailsAction(
                    packageName,
                    event.text.map { it.toString() }
                )
            ) {
                pendingSemanticDetailsClick = false
                Log.d(TAG, "Google TV semantic View details click consumed")
                return
            }
            val cachedTitle = pendingGoogleTvSearchTitle
            pendingGoogleTvSearchTitle = null
            Log.d(
                TAG,
                "Google TV search title consumed: $cachedTitle " +
                    "instance=${System.identityHashCode(this)} thread=${Thread.currentThread().name}"
            )
            handleGoogleTvSearchRoute(cachedTitle)
            return
        }
        val isSearchSelection = eventType == AccessibilityEvent.TYPE_VIEW_SELECTED &&
            packageName in setOf(GOOGLE_TV_ASSISTANT_PACKAGE, GOOGLE_SEARCH_PACKAGE)
        if (eventType == AccessibilityEvent.TYPE_VIEW_SELECTED && !isSearchSelection) return
        if (event.packageName == AMAZON_LAUNCHER_PACKAGE) {
            val title = extractFireTvTitle(event)
            if (!title.isNullOrBlank()) {
                Log.d(TAG, "Movie/show detected (Fire TV): $title")
                handleMovieClick(title)
            }
            return
        }

        val desc = event.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            if (isMovieOrShowCard(event, desc) || (isSearchSelection && desc.length > 1)) {
                val title = if (isSearchSelection) extractSearchTitle(desc) else extractTitle(desc)
                if (title.isNotBlank()) {
                    Log.d(TAG, "Movie/show detected: $title")
                    handleMovieClick(title)
                }
            }
            return
        }

        val heroTitle = extractHeroTitle(event)
        if (heroTitle != null) {
            Log.d(TAG, "Movie/show detected (hero): $heroTitle")
            handleMovieClick(heroTitle)
            return
        }

        val source = event.source ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            source.refresh()
            val delayedDesc = source.contentDescription?.toString()
            if (!delayedDesc.isNullOrBlank() && isMovieOrShowCard(event, delayedDesc)) {
                val title = extractTitle(delayedDesc)
                if (title.isNotBlank()) {
                    Log.d(TAG, "Movie/show detected (delayed): $title")
                    handleMovieClick(title)
                }
            }
        }, 600)
    }

    private fun handleGoogleTvSearchRoute(cachedTitle: String? = null, attempt: Int = 0) {
        val title = cachedTitle ?: readActiveGoogleTvSearchTitle()
        if (title != null) {
            Log.d(TAG, "Movie/show detected (Google TV search): $title")
            handleMovieClick(title)
        } else if (attempt + 1 < SEARCH_DETAILS_MAX_ATTEMPTS) {
            Handler(Looper.getMainLooper()).postDelayed(
                { handleGoogleTvSearchRoute(attempt = attempt + 1) },
                SEARCH_DETAILS_RETRY_DELAY_MS
            )
        } else {
            Log.w(TAG, "Unable to read title from Google TV search details")
        }
    }

    private fun routeHomeProviderPlot(plot: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val searchIntent = Intent("android.search.action.GLOBAL_SEARCH").apply {
                setPackage(GOOGLE_TV_ASSISTANT_PACKAGE)
                putExtra("query", plot)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(searchIntent)
                Log.d(TAG, "Google TV semantic search started: $plot")
                readSemanticSearchResult(plot)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to start Google TV semantic search", error)
            }
        }, HOME_PROVIDER_SEARCH_START_DELAY_MS)
    }

    private fun readSemanticSearchResult(
        plot: String,
        attempt: Int = 0,
        detailsRequested: Boolean = false
    ) {
        val visibleTexts = mutableListOf<String>()
        val root = rootInActiveWindow
        root?.let { collectVisibleTexts(it, visibleTexts) }
        val title = GoogleTvSearchTitleExtractor.semanticResultTitle(plot, visibleTexts)
            ?: if (detailsRequested) {
                GoogleTvSearchTitleExtractor.entityDetailsTitle(plot, visibleTexts)
            } else {
                null
            }
        if (title != null) {
            Log.d(TAG, "Google TV semantic search resolved: $plot -> $title")
            handleMovieClick(title)
        } else if (attempt + 1 < HOME_PROVIDER_SEARCH_MAX_ATTEMPTS) {
            var nextDetailsRequested = detailsRequested
            if (!detailsRequested && root != null && clickViewDetails(root)) {
                nextDetailsRequested = true
                Log.d(TAG, "Google TV semantic View details opened")
            }
            Handler(Looper.getMainLooper()).postDelayed(
                { readSemanticSearchResult(plot, attempt + 1, nextDetailsRequested) },
                HOME_PROVIDER_SEARCH_RETRY_DELAY_MS
            )
        } else {
            Log.w(TAG, "Unable to resolve Google TV home provider plot: $plot")
        }
    }

    private fun clickViewDetails(root: AccessibilityNodeInfo): Boolean {
        val detailsButton = root.findAccessibilityNodeInfosByText("View details")
            .firstOrNull {
                it.text?.toString()?.equals("View details", ignoreCase = true) == true &&
                    it.isClickable
            } ?: return false
        pendingSemanticDetailsClick = true
        val clicked = detailsButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) pendingSemanticDetailsClick = false
        return clicked
    }

    private fun readActiveGoogleTvSearchTitle(): String? {
        val visibleTexts = mutableListOf<String>()
        rootInActiveWindow?.let { collectVisibleTexts(it, visibleTexts) }
        return GoogleTvSearchTitleExtractor.extract(visibleTexts)
    }

    private fun collectVisibleTexts(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectVisibleTexts(it, output) }
        }
    }

    private fun extractSearchTitle(contentDesc: String): String {
        return contentDesc
            .substringBefore("\n")
            .substringBefore(",")
            .trim()
    }

    private fun extractFireTvTitle(event: AccessibilityEvent): String? {
        val source = event.source ?: return null
        return findFireTvMainImageDescription(source)
    }

    private fun findFireTvMainImageDescription(node: AccessibilityNodeInfo): String? {
        if (node.viewIdResourceName == FIRE_TV_MAIN_IMAGE_ID) {
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrBlank()) return desc
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFireTvMainImageDescription(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun extractHeroTitle(event: AccessibilityEvent): String? {
        if (event.className != "android.view.ViewGroup") return null
        val parts = event.text
        if (parts.isNullOrEmpty()) return null
        val first = parts[0]?.toString()?.trim() ?: return null
        if (first.isBlank() || first.equals("Patrocinado", ignoreCase = true)) return null
        return first
    }

    private fun isMovieOrShowCard(event: AccessibilityEvent, contentDesc: String): Boolean {
        if (TITLE_MARKERS.any { contentDesc.contains(it) }) return true
        if (event.className != "android.view.View" &&
            event.className != "android.view.ViewGroup"
        ) return false
        if (!event.text.isNullOrEmpty()) return false
        val commaIndex = contentDesc.indexOf(',')
        if (commaIndex <= 0) return false
        return contentDesc.substring(commaIndex + 1).isNotBlank()
    }

    private fun extractTitle(contentDesc: String): String {
        val markerIndex = TITLE_MARKERS
            .map { contentDesc.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()

        if (markerIndex != null) {
            return contentDesc.substring(0, markerIndex).trim().trimEnd(',').trim()
        }

        return contentDesc.substringBefore(",").trim()
    }

    private fun handleMovieClick(title: String) {
        backgroundExecutor.execute {
            val match = TmdbClient.findImdbId(title)
            if (match == null) {
                Log.w(TAG, "Unable to resolve IMDb ID for: $title")
                return@execute
            }
            Log.d(TAG, "IMDb ID resolved: $title -> ${match.imdbId} (${match.type})")
            NuvioLauncher.open(this, match)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
