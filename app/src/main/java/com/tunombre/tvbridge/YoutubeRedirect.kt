package com.tunombre.tvbridge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log

/**
 * Redirige la reproducción de YouTube a SmartTube, el cliente de terceros que
 * el usuario prefiere en lugar de la app oficial de YouTube.
 *
 * No tenemos forma de extraer el ID del vídeo concreto desde el evento de
 * accesibilidad (a diferencia de las tarjetas de película/serie, aquí solo
 * detectamos que la ventana en primer plano pertenece a YouTube), así que
 * simplemente traemos SmartTube al frente y dejamos que el usuario elija el
 * vídeo ahí. Esto es justo lo que se pide: nunca dejar que la app oficial de
 * YouTube se abra o reproduzca.
 */
object YoutubeRedirect {

    private const val TAG = "YoutubeRedirect"

    // Todos los paquetes conocidos de la app oficial de YouTube en Android TV
    // / Google TV que queremos bloquear.
    val YOUTUBE_PACKAGES: Set<String> = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.leanback"
    )

    // Variantes conocidas del paquete de SmartTube (la build "estable" y la
    // rama "beta/next" de liskovsoft), probadas en orden hasta encontrar una
    // instalada.
    private val SMARTTUBE_PACKAGES = listOf(
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv",
        "org.smarttube.stable"
    )

    fun isYoutubePackage(packageName: String?): Boolean =
        packageName != null && YOUTUBE_PACKAGES.contains(packageName)

    fun isSmartTubePackage(packageName: String?): Boolean =
        packageName != null && SMARTTUBE_PACKAGES.contains(packageName)

    fun redirectToSmartTube(service: AccessibilityService) {
        val pm = service.packageManager
        val installedPackage = SMARTTUBE_PACKAGES.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        if (installedPackage == null) {
            Log.w(TAG, "SmartTube no está instalado, no se puede redirigir")
            return
        }

        val launchIntent = pm.getLaunchIntentForPackage(installedPackage)
        if (launchIntent == null) {
            Log.w(TAG, "SmartTube ($installedPackage) no tiene activity de lanzamiento")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        try {
            service.startActivity(launchIntent)
            Log.d(TAG, "Redirigido a SmartTube ($installedPackage)")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir SmartTube", e)
        }
    }
}
