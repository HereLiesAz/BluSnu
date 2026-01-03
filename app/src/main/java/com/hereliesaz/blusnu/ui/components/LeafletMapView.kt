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

data class MapDevice(val mac: String, val lat: Double, val lng: Double, val name: String?)

@Composable
fun LeafletMapView(
    modifier: Modifier = Modifier,
    userLocation: Pair<Double, Double>?,
    devices: List<TargetDevice>
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Ensure dark background if HTML didn't set it in time
                        view?.setBackgroundColor(Color.BLACK)
                    }
                    // TODO: Add onReceivedError if needed for debugging
                }
                loadUrl("file:///android_asset/leaflet_map.html")
            }
        },
        update = { webView ->
            // Update User Location
            userLocation?.let { (lat, lng) ->
                webView.evaluateJavascript("updateUserLocation($lat, $lng)", null)
            }

            // Update Devices
            val mapDevices = devices.mapNotNull { device ->
                if (device.latitude != null && device.longitude != null) {
                    MapDevice(device.macAddress, device.latitude, device.longitude, device.name)
                } else {
                    null
                }
            }
            val json = Gson().toJson(mapDevices)
            val base64Json = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            webView.evaluateJavascript("updateDevices('$base64Json')", null)
        }
    )
}
