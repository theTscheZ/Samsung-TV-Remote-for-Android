package com.example.samsungremote

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings

/**
 * Stores the preferred TV WLAN by SSID. Android controls WLAN selection:
 * a suggestion can influence auto-join but cannot force an immediate switch.
 * Passwords are passed directly to the OS suggestion service and never written
 * to our app preferences.
 */
internal class WifiPreference(private val context: Context) {
    private val prefs = context.getSharedPreferences("tv_wifi", Context.MODE_PRIVATE)
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var preferredSsid: String
        get() = prefs.getString("ssid", "").orEmpty()
        set(value) { prefs.edit().putString("ssid", value.trim().trim('"')).apply() }

    @Suppress("DEPRECATION")
    fun currentSsid(): String? = try {
        val ssid = wifi.connectionInfo?.ssid?.trim('"').orEmpty()
        ssid.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" }
    } catch (_: SecurityException) { null }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** Returns true if WLAN is available and it isn't known to be the wrong WLAN. */
    fun canReachTv(): Boolean {
        if (!isWifiConnected()) return false
        val expected = preferredSsid
        val actual = currentSsid()
        return expected.isBlank() || actual == null || actual == expected
    }

    fun wifiSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)

    fun suggestNetwork(ssid: String, password: String): String {
        if (Build.VERSION.SDK_INT < 29) return "WLAN in Android-Einstellungen auswählen"
        if (ssid.isBlank()) return "Zuerst den WLAN-Namen eingeben"
        if (password.isNotBlank() && password.length < 8) return "WLAN-Passwort muss mindestens 8 Zeichen haben"
        return try {
            val builder = WifiNetworkSuggestion.Builder().setSsid(ssid.trim().trim('"'))
            if (password.isNotBlank()) builder.setWpa2Passphrase(password)
            val suggestion = builder.build()
            // Existing suggestion is removed only when a new one is configured.
            val current = wifi.networkSuggestions
            if (current.isNotEmpty()) wifi.removeNetworkSuggestions(current)
            val result = wifi.addNetworkSuggestions(listOf(suggestion))
            when (result) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ->
                    "WLAN-Vorschlag eingerichtet. Android kann eine Freigabe verlangen."
                else -> "Android hat den WLAN-Vorschlag abgelehnt (Code $result)"
            }
        } catch (_: SecurityException) {
            "WLAN-Berechtigung fehlt"
        } catch (e: Exception) {
            "WLAN-Vorschlag fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}"
        }
    }
}
