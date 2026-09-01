package com.tunombre.tvbridge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log

object YoutubeRedirect {
    private const val TAG = "YoutubeRedirect"

    private val smartTubePackages = listOf(
        "org.smarttube.stable",
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv"
    )

    fun openSmartTube(service: AccessibilityService) {
        val packageManager = service.packageManager
        val installedPackage = smartTubePackages.firstOrNull { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        } ?: run {
            Log.w(TAG, "SmartTube is not installed")
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(installedPackage) ?: run {
            Log.w(TAG, "SmartTube has no launch activity: $installedPackage")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        service.startActivity(launchIntent)
        Log.d(TAG, "Opened SmartTube: $installedPackage")
    }
}
