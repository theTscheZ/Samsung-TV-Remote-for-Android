package com.example.samsungremote

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Offline, recognizable icons for installed Samsung TV apps.
 * Resolve by app ID first (when verified/known), then the name supplied by the TV.
 * Some apps cannot provide their own image over the Samsung WebSocket API.
 * Fallback wordmarks are intentionally readable instead of one-letter grey circles.
 */
internal data class BrandStyle(
    @DrawableRes val iconRes: Int? = null,
    val background: Color,
    val foreground: Color = Color.White,
    val wordmark: String? = null
)

internal fun brandFor(app: TvApp): BrandStyle? {
    val name = app.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9+]"), "")
    val id = app.id.trim()
    fun has(vararg words: String): Boolean = words.any { name.contains(it) }

    return when {
        id == "11101200001" || has("netflix") ->
            BrandStyle(R.drawable.brand_netflix, Color(0xFF101010), Color(0xFFE50914))
        id == "111299001912" || has("youtube", "youtubetv") ->
            BrandStyle(R.drawable.brand_youtube, Color(0xFFFF0033))
        id == "3202102009185" || has("crunchyroll") ->
            BrandStyle(R.drawable.brand_crunchyroll, Color(0xFFFF6A00))
        id == "3201606009684" || has("spotify") ->
            BrandStyle(R.drawable.brand_spotify, Color(0xFF1DB954), Color.Black)
        has("twitch") ->
            BrandStyle(R.drawable.brand_twitch, Color(0xFF9146FF))
        has("plex") ->
            BrandStyle(R.drawable.brand_plex, Color(0xFF121212), Color(0xFFE5A00D))
        has("appletv", "appletv+") ->
            BrandStyle(R.drawable.brand_appletv, Color(0xFF181818))
        has("dazn") ->
            BrandStyle(R.drawable.brand_dazn, Color(0xFF161616))
        has("paramount") ->
            BrandStyle(R.drawable.brand_paramountplus, Color(0xFF0064FF))
        has("samsungtvplus", "tvplus") ->
            BrandStyle(R.drawable.brand_samsung, Color(0xFF1428A0))
        has("zdf") ->
            BrandStyle(wordmark = "ZDF", background = Color(0xFFFF8000))
        id == "3201901017640" || has("disney") ->
            BrandStyle(wordmark = "Disney+", background = Color(0xFF122A77))
        id == "3201512006785" || has("primevideo", "amazonprime", "prime") ->
            BrandStyle(wordmark = "prime\nvideo", background = Color(0xFF0F171E), foreground = Color(0xFF00A8E1))
        has("joyn") ->
            BrandStyle(wordmark = "joyn", background = Color(0xFF3B19A7))
        has("waipu") ->
            BrandStyle(wordmark = "waipu.tv", background = Color(0xFF101010))
        has("ard", "daserste") ->
            BrandStyle(wordmark = "ARD", background = Color(0xFF0052A5))
        has("rtl+") ->
            BrandStyle(wordmark = "RTL+", background = Color(0xFF7F00FF))
        has("magenta", "telekom") ->
            BrandStyle(wordmark = "MAGENTA", background = Color(0xFFE20074))
        has("tiktok") ->
            BrandStyle(wordmark = "TikTok", background = Color(0xFF080808))
        has("arte") ->
            BrandStyle(wordmark = "ARTE", background = Color(0xFFFF4D00))
        has("sky", "wow") ->
            BrandStyle(wordmark = if (has("wow")) "WOW" else "sky", background = Color(0xFF172051))
        else -> null
    }
}
