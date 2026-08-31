package com.tunombre.tvbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/**
 * Servicio de accesibilidad que escucha clics en el launcher de Google TV
 * (com.google.android.apps.tv.launcherx) y en el de Fire TV
 * (com.amazon.tv.launcher).
 *
 * Comportamiento:
 *  - No hace nada si no hay una suscripción verificada vigente (ver
 *    [LicenseManager]).
 *  - Si el nodo pulsado es una tarjeta de película/serie recomendada
 *    (detectado por patrones típicos del content-desc, como "cuesta:" o
 *    "puntuación:"), extrae el título, lo resuelve a un IMDb ID vía TMDb,
 *    y abre la app elegida (Nuvio o Stremio) directamente en la ficha de
 *    esa película o serie.
 *  - Para cualquier otro clic (iconos de apps, fila "Tus aplicaciones",
 *    etc.) no hace absolutamente nada: el sistema procesa el clic con su
 *    comportamiento normal.
 */
class TvRecommendationAccessibilityService : AccessibilityService() {

    // Un solo hilo de fondo para las llamadas de red (TMDb), para no
    // bloquear nunca el hilo principal del servicio de accesibilidad.
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Hasta cuándo seguimos vigilando qué app aparece en primer plano tras
    // detectar un clic en una recomendación (ver PENDING_REDIRECT_WINDOW_MS).
    @Volatile private var pendingRedirectDeadline: Long = 0L

    // Para el debounce del redirect de YouTube.
    @Volatile private var lastYoutubeRedirectAt: Long = 0L

    companion object {
        private const val TAG = "TvRecService"

        // Marcadores que separan el título del resto del content-desc en las
        // tarjetas de fila. Usarlos para cortar (en vez de la primera coma)
        // evita truncar títulos que ya traen coma de por sí, como
        // "Monstruos, S.A." (cortar por la primera coma daría solo "Monstruos").
        private val TITLE_MARKERS = listOf("cuesta:", "se necesita una suscripción a", "puntuación:")

        private const val AMAZON_LAUNCHER_PACKAGE = "com.amazon.tv.launcher"
        private const val GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"

        // Cuánto tiempo, tras detectar el clic en una tarjeta de
        // película/serie, seguimos vigilando qué app aparece en primer
        // plano. Si en ese margen aparece algo que no es ni el launcher ni
        // la app de destino elegida, la mandamos de vuelta a Home: significa
        // que la app original (Netflix, Prime Video, etc.) ganó la carrera
        // contra nuestra resolución de TMDb y estaba a punto de reproducir
        // en segundo plano.
        private const val PENDING_REDIRECT_WINDOW_MS = 6000L

        // Tiempo que le damos a la pulsación de Home para completarse antes
        // de lanzar SmartTube encima; si lanzamos demasiado rápido, a veces
        // la animación de Home se come el startActivity.
        private const val HOME_TO_LAUNCH_DELAY_MS = 350L

        // Evita relanzar SmartTube en bucle si YouTube tarda en cerrarse del
        // todo y dispara varios TYPE_WINDOW_STATE_CHANGED seguidos.
        private const val YOUTUBE_REDIRECT_DEBOUNCE_MS = 3000L

        // En las tarjetas de contenido del launcher de Fire TV, el título vive
        // en el content-desc de este ImageView hijo, no en el nodo pulsado
        // (que siempre tiene content-desc vacío). Los iconos de apps normales
        // usan el mismo resource-id pero con content-desc vacío, lo que sirve
        // para distinguir tarjetas de contenido real de iconos de apps sin
        // necesitar una lista de apps conocidas.
        private const val FIRE_TV_MAIN_IMAGE_ID = "com.amazon.tv.launcher:id/main_image"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Configuración programática del servicio: en este dispositivo (TCL,
        // Android 12) el meta-data de accessibility_service_config.xml no se
        // estaba aplicando en tiempo de ejecución (dumpsys accessibility
        // mostraba capabilities=0, eventTypes= vacío pese a que el XML
        // compilado en el APK era correcto). Configurarlo aquí evita
        // depender de ese parseo.
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // Sin packageNames (null = todas las apps): antes solo
            // escuchábamos al launcher, pero para poder detectar que
            // YouTube/Netflix/etc. pasaron a primer plano (y mandarlas de
            // vuelta a Home) necesitamos ver los cambios de ventana de
            // cualquier app, no solo del launcher.

            // Necesario para poder interceptar el botón físico de Power del
            // mando (ver onKeyEvent) y mostrar el menú de apagado/reinicio en
            // vez de dejar que apague la pantalla.
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        // Refresca la verificación de suscripción en segundo plano al
        // arrancar el servicio, para que la caché (usada por isLikelyValid)
        // no dependa solo de que el usuario abra MainActivity.
        LicenseManager.getSavedEmail(this)?.let { email ->
            backgroundExecutor.execute { LicenseManager.verifyNow(this, email) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClicked(event)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == GOOGLE_TV_LAUNCHER_PACKAGE || pkg == AMAZON_LAUNCHER_PACKAGE) return
        if (!LicenseManager.isLikelyValid(this)) return

        if (YoutubeRedirect.isYoutubePackage(pkg)) {
            if (!Preferences.isYoutubeRedirectEnabled(this)) return
            val now = System.currentTimeMillis()
            if (now - lastYoutubeRedirectAt < YOUTUBE_REDIRECT_DEBOUNCE_MS) return
            lastYoutubeRedirectAt = now
            Log.d(TAG, "YouTube en primer plano ($pkg), redirigiendo a SmartTube")
            performGlobalAction(GLOBAL_ACTION_HOME)
            mainHandler.postDelayed({ YoutubeRedirect.redirectToSmartTube(this) }, HOME_TO_LAUNCH_DELAY_MS)
            return
        }

        // Red de seguridad para películas/series: si hace poco detectamos un
        // clic en una recomendación y de repente aparece en primer plano una
        // app que no es la elegida por el usuario (Nuvio/Stremio), es que la
        // app original iba a reproducir en segundo plano. La mandamos a Home
        // para que nunca llegue a verse ni a sonar.
        if (System.currentTimeMillis() > pendingRedirectDeadline) return
        if (YoutubeRedirect.isSmartTubePackage(pkg)) return
        val selectedPackage = Preferences.getSelectedApp(this).packageName
        if (pkg == selectedPackage) return
        Log.d(TAG, "App inesperada en primer plano durante una redirección ($pkg), volviendo a Home")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        if (!LicenseManager.isLikelyValid(this)) return
        if (event.packageName != AMAZON_LAUNCHER_PACKAGE && event.packageName != GOOGLE_TV_LAUNCHER_PACKAGE) return

        if (event.packageName == AMAZON_LAUNCHER_PACKAGE) {
            val title = extractFireTvTitle(event)
            if (!title.isNullOrBlank()) {
                Log.d(TAG, "Película/serie detectada (Fire TV): $title")
                handleMovieClick(title)
            }
            return
        }

        // El content-desc de las tarjetas de recomendación del launcher viaja
        // en event.contentDescription, NO en event.source.contentDescription
        // (que siempre es null para estas tarjetas). Confirmado con logging
        // en dispositivo real.
        val desc = event.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            if (isMovieOrShowCard(event, desc)) {
                val title = extractTitle(desc)
                if (title.isNotBlank()) {
                    Log.d(TAG, "Película/serie detectada: $title")
                    handleMovieClick(title)
                }
            }
            return
        }

        // Cartel grande con autoplay (fila superior de "Inicio"): el título
        // viaja en event.text, no en contentDescription. Formato:
        // [Título, subtítulo, sinopsis, CTA]. Los patrocinados van primero
        // con "Patrocinado" y se ignoran (no son recomendaciones reales).
        val heroTitle = extractHeroTitle(event)
        if (heroTitle != null) {
            Log.d(TAG, "Película/serie detectada (cartel grande): $heroTitle")
            handleMovieClick(heroTitle)
            return
        }

        // Algunas filas (p.ej. RTVE en "Recomendaciones destacadas" de
        // Inicio) rellenan el content-desc del nodo con retraso tras el
        // clic: en el momento del evento aún está vacío. Reintentamos una
        // vez, poco después, releyendo el nodo.
        val source = event.source ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            source.refresh()
            val delayedDesc = source.contentDescription?.toString()
            if (!delayedDesc.isNullOrBlank() && isMovieOrShowCard(event, delayedDesc)) {
                val title = extractTitle(delayedDesc)
                if (title.isNotBlank()) {
                    Log.d(TAG, "Película/serie detectada (retraso): $title")
                    handleMovieClick(title)
                }
            }
        }, 600)
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
        if (TITLE_MARKERS.any { contentDesc.contains(it) }) {
            return true
        }

        // Otros formatos sin marcador de precio/puntuación:
        //  - Carteles grandes ("Google TV") de Películas/Series: "{Título}, {sinopsis}".
        //  - Plataformas gratuitas, p.ej. RTVE Play: "{Título}, RTVE Play".
        // Ambos son "{Título}, {resto}" en una tarjeta real android.view.View
        // sin texto propio. Los banners/anuncios (p.ej. "Netflix, Ver ahora")
        // son android.view.ViewGroup y sí traen texto ("VER AHORA") — así los
        // distinguimos sin necesitar una lista de plataformas conocidas.
        if (event.className != "android.view.View") return false
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

        // Formato de cartel grande sin marcador: "{Título}, {sinopsis}".
        return contentDesc.substringBefore(",").trim()
    }

    private fun handleMovieClick(title: String) {
        // Nos vamos a Home inmediatamente, antes de esperar a la resolución
        // de TMDb (que es una llamada de red y puede tardar): así la app
        // original que el launcher iba a abrir (Netflix, Prime Video, Disney+,
        // etc.) nunca llega a quedarse reproduciendo en segundo plano
        // mientras nosotros todavía estamos resolviendo el título. El
        // vigilante de handleWindowStateChanged cubre el resto de la carrera
        // durante PENDING_REDIRECT_WINDOW_MS.
        pendingRedirectDeadline = System.currentTimeMillis() + PENDING_REDIRECT_WINDOW_MS
        performGlobalAction(GLOBAL_ACTION_HOME)

        backgroundExecutor.execute {
            val match = TmdbClient.findImdbId(title)
            if (match == null) {
                Log.w(TAG, "No se pudo resolver IMDb ID para: $title")
                return@execute
            }
            Log.d(TAG, "IMDb ID resuelto: $title -> ${match.imdbId} (${match.type})")
            StremioLauncher.open(this, match)
        }
    }

    // Intercepta el botón físico de Power del mando. Por defecto, mantenerlo
    // pulsado (o incluso una pulsación corta en algunos mandos de Chromecast
    // con Google TV) apaga la pantalla, lo que hace imposible capturarlo en
    // apps como Button Mapper (al pulsarlo para "grabarlo", la pantalla se
    // apaga antes de que puedan detectar la pulsación). Como servicio de
    // accesibilidad sí podemos leer el evento de tecla antes que el sistema,
    // así que lo consumimos y mostramos directamente el menú de
    // apagado/reinicio en su lugar.
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_POWER) return super.onKeyEvent(event)
        if (!Preferences.isPowerButtonRemapEnabled(this)) return super.onKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
        // Devolver true en todas las acciones (down/up) de esta tecla evita
        // que el sistema procese también su comportamiento normal (apagar
        // pantalla).
        return true
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
