package com.example.samsungremote

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Raster PNG icons are bundled locally from Homarr dashboard-icons and website favicons.
 * Unknown TV apps still use Samsung-delivered imagery or a readable fallback.
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
    fun has(vararg aliases: String) = aliases.any(name::contains)
    val dark = Color(0xFF171B28)
    return when {
        id == "11101200001" || has("netflix") ->
            BrandStyle(R.drawable.brand_png_netflix, dark)
        id == "111299001912" || has("youtube") ->
            BrandStyle(R.drawable.brand_png_youtube, dark)
        id == "3202102009185" || has("crunchyroll") ->
            BrandStyle(R.drawable.brand_png_crunchyroll, dark)
        id == "3201606009684" || has("spotify") ->
            BrandStyle(R.drawable.brand_png_spotify, dark)
        has("twitch") -> BrandStyle(R.drawable.brand_png_twitch, dark)
        has("plex") -> BrandStyle(R.drawable.brand_png_plex, dark)
        has("appletv", "apple+") -> BrandStyle(R.drawable.brand_png_appletv, dark)
        has("dazn") -> BrandStyle(R.drawable.brand_png_dazn, dark)
        has("paramount") -> BrandStyle(R.drawable.brand_png_paramountplus, dark)
        has("samsungtvplus", "tvplus") -> BrandStyle(R.drawable.brand_png_samsung, dark)
        has("zdf") -> BrandStyle(R.drawable.brand_png_zdf, dark)
        id == "3201901017640" || has("disney") ->
            BrandStyle(R.drawable.brand_png_disneyplus, dark)
        id == "3201512006785" || has("primevideo", "amazonprime", "prime") ->
            BrandStyle(R.drawable.brand_png_primevideo, dark)
        has("canal+", "canalplus", "canal") ->
            BrandStyle(R.drawable.brand_png_canalplus, dark)
        has("joyn") -> BrandStyle(R.drawable.brand_png_joyn, dark)
        has("waipu") -> BrandStyle(R.drawable.brand_png_waipu, dark)
        has("ard", "daserste") -> BrandStyle(R.drawable.brand_png_ard, dark)
        has("rtl+") -> BrandStyle(R.drawable.brand_png_rtlplus, dark)
        has("magenta", "telekom") -> BrandStyle(R.drawable.brand_png_magenta, dark)
        has("tiktok") -> BrandStyle(R.drawable.brand_png_tiktok, dark)
        has("arte") -> BrandStyle(R.drawable.brand_png_arte, dark)
        has("wow") -> BrandStyle(R.drawable.brand_png_wow, dark)
        has("sky") -> BrandStyle(R.drawable.brand_png_sky, dark)
        has("hulu") -> BrandStyle(R.drawable.brand_png_hulu, dark)
        else -> null
    }
}
