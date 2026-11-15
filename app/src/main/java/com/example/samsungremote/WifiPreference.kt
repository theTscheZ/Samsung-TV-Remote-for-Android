package com.example.samsungremote

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * WLAN selection is owned by Android. This app only opens the system Wi-Fi
 * settings and removes its own obsolete Wi-Fi suggestions from v0.11/v0.12.
 */
internal class WifiPreference(private val context: Context) {
    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun wifiSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)

    fun removeLegacySuggestions() {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val suggestions = wifi.networkSuggestions
            if (suggestions.isNotEmpty()) wifi.removeNetworkSuggestions(suggestions)
        } catch (_: Exception) {
            // Android may block the cleanup if Wi-Fi permissions were revoked.
        }
    }
}
