package com.hereliesaz.blusnu.ui.components

import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.hereliesaz.blusnu.data.TargetDevice

/**
 * Data model for passing device info to the JavaScript map layer.
 *
 * @param mac Device MAC address (unique ID).
 * @param lat Latitude.
 * @param lng Longitude.
 * @param name Optional device name.
 */
data class MapDevice(val mac: String, val lat: Double, val lng: Double, val name: String?)

/**
 * A Composable that renders an interactive map using Leaflet.js inside a WebView.
 *
 * We use a local HTML/JS asset (`leaflet_map.html`) to ensure offline capability
 * and avoid API key requirements for basic tiles (using OpenStreetMap).
 *
 * @param modifier Modifier for layout.
 * @param userLocation The user's current GPS coordinates (lat, lng).
 * @param devices List of devices to plot on the map.
 */
@Composable
fun LeafletMapView(
    modifier: Modifier = Modifier,
    userLocation: Pair<Double, Double>?,
    devices: List<TargetDevice>
) {
    // AndroidView allows embedding legacy Views (WebView) into Compose.
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Ensure the WebView doesn't flash white on load.
                setBackgroundColor(Color.TRANSPARENT)

                // Enable JS for Leaflet interaction.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Allow loading local assets (file:///android_asset/).
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Ensure dark background matches app theme.
                        view?.setBackgroundColor(Color.BLACK)
                    }
                }
                // Load the local HTML file bundled in assets.
                loadUrl("file:///android_asset/leaflet_map.html")
            }
        },
        update = { webView ->
            // This block runs whenever recomposition happens (i.e., new data available).

            // 1. Update User Marker
            userLocation?.let { (lat, lng) ->
                // Call the JavaScript function defined in leaflet_map.html
                webView.evaluateJavascript("updateUserLocation($lat, $lng)", null)
            }

            // 2. Update Device Markers
            // Filter out devices with no location data.
            val mapDevices = devices.mapNotNull { device ->
                if (device.latitude != null && device.longitude != null) {
                    MapDevice(device.macAddress, device.latitude, device.longitude, device.name)
                } else {
                    null
                }
            }

            // Serialize list to JSON.
            val json = Gson().toJson(mapDevices)

            // Encode to Base64 to safely pass complex strings/characters to JS function.
            val base64Json = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // Invoke JS update function.
            webView.evaluateJavascript("updateDevices('$base64Json')", null)
        }
    )
}
