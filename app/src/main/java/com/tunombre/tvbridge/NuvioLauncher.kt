package com.tunombre.tvbridge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Opens resolved Google TV recommendations directly in Nuvio. */
object NuvioLauncher {

    private const val TAG = "NuvioLauncher"
    private const val NUVIO_PACKAGE = "com.nuvio.app"

    fun deepLink(match: TmdbMatch): String = if (match.type == MediaType.MOVIE) {
        "nuvio://movie/${match.imdbId}"
    } else {
        "nuvio://detail/tv/${match.imdbId}"
    }

    fun open(service: AccessibilityService, match: TmdbMatch) {
        val uri = Uri.parse(deepLink(match))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            setPackage(NUVIO_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        try {
            service.startActivity(intent)
            Log.d(TAG, "Opening Nuvio: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Nuvio package launch failed; retrying by URI", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                service.startActivity(fallbackIntent)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Unable to open Nuvio. Is it installed?", fallbackError)
            }
        }
    }
}
