package com.tunombre.tvbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

/**
 * Minimal setup screen for NuvioBridge.
 *
 * The app has one job: enable the Accessibility service so Google TV
 * recommendation clicks can be resolved through TMDB and opened in Nuvio.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button_accessibility_settings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    R.string.main_accessibility_settings_unavailable,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
