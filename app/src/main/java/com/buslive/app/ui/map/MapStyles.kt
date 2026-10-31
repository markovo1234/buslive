package com.buslive.app.ui.map

import androidx.compose.runtime.staticCompositionLocalOf
import org.maplibre.android.maps.Style

/** Which base map to draw. TomTom when a key is set and we are online, else free OpenFreeMap (also used offline). */
data class MapSource(val tomtomKey: String?, val online: Boolean) {
    val useTomTom: Boolean get() = !tomtomKey.isNullOrBlank() && online

    fun style(dark: Boolean): Style.Builder =
        if (useTomTom) Style.Builder().fromJson(tomtomStyleJson(tomtomKey!!.trim(), dark))
        else Style.Builder().fromUri(if (dark) BusMapController.STYLE_DARK else BusMapController.STYLE_LIGHT)

    companion object {
        /** TomTom Map Display API raster tiles (fresh commercial map data; names in local language). */
        fun tileUrl(key: String, dark: Boolean): String =
            "https://api.tomtom.com/map/1/tile/basic/${if (dark) "night" else "main"}/{z}/{x}/{y}.png?tileSize=512&key=$key"

        fun tomtomStyleJson(key: String, dark: Boolean): String = """
            {
              "version": 8,
              "name": "TomTom",
              "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
              "sources": {
                "tomtom": {
                  "type": "raster",
                  "tiles": ["${tileUrl(key, dark)}"],
                  "tileSize": 512,
                  "minzoom": 0,
                  "maxzoom": 22,
                  "attribution": "© TomTom"
                }
              },
              "layers": [
                { "id": "bg", "type": "background", "paint": { "background-color": "${if (dark) "#1b2121" else "#eef0f0"}" } },
                { "id": "tomtom", "type": "raster", "source": "tomtom" }
              ]
            }
        """.trimIndent()
    }
}

val LocalMapSource = staticCompositionLocalOf { MapSource(tomtomKey = null, online = true) }
