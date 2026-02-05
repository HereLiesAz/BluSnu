package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manager class for handling Android Location Services (GPS/Network).
 *
 * This class uses the Google Play Services FusedLocationProviderClient to obtain
 * high-accuracy location updates. These updates are exposed as a Kotlin Flow
 * for easy consumption by the UI or other modules (like [TandemManager]).
 *
 * @param context The application context.
 */
class LocationManager(context: Context) {

    // The entry point to the Fused Location Provider API.
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Returns a Flow that emits Location updates.
     *
     * The flow starts by trying to emit the last known location immediately (for responsiveness),
     * and then requests continuous high-accuracy updates.
     *
     * @return A Flow<android.location.Location>
     */
    @SuppressLint("MissingPermission") // Permissions are checked in the UI/ViewModel layer before collecting.
    fun locationFlow(): Flow<android.location.Location> = callbackFlow {
        // Attempt to get the last known location from the cache.
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            // If available, emit it immediately so the map centers quickly.
            location?.let { trySend(it) }
        }

        // Configure the location request.
        // Priority.PRIORITY_HIGH_ACCURACY: Use GPS + WiFi + Cell to get best possible fix.
        // Interval: 5000ms (5 seconds) between updates.
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        // Define the callback to handle new location results.
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Emit the new location to the flow collector.
                locationResult.lastLocation?.let { trySend(it) }
            }
        }

        // Request location updates.
        // We use the main looper for the callback thread (standard practice for GMS Location).
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // When the flow collector is cancelled (e.g., screen closed), remove updates to save battery.
        awaitClose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }
}
