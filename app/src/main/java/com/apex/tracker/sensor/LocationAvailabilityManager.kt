package com.apex.tracker.sensor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated monitor for GNSS location provider availability and fine location permissions.
 *
 * Exposes reactive [StateFlow] streams for UI banners, guard checks, and background notifications.
 */
class LocationAvailabilityManager(
    private val context: Context? = null,
    private val locationManager: LocationManager? = try {
        context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (_: Exception) {
        null
    },
    private val gpsEnabledProvider: (() -> Boolean)? = null,
    private val permissionGrantedProvider: (() -> Boolean)? = null
) {
    private val _isLocationServicesEnabled = MutableStateFlow(checkLocationServicesEnabled())
    val isLocationServicesEnabled: StateFlow<Boolean> = _isLocationServicesEnabled.asStateFlow()

    private val _isLocationPermissionGranted = MutableStateFlow(checkLocationPermissionGranted())
    val isLocationPermissionGranted: StateFlow<Boolean> = _isLocationPermissionGranted.asStateFlow()

    @Volatile
    private var receiverRegistered = false

    private val providersChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(recvContext: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        registerReceiver()
        refresh()
    }

    /**
     * Checks whether GPS provider is enabled in system settings.
     */
    fun checkLocationServicesEnabled(): Boolean {
        if (gpsEnabledProvider != null) {
            return gpsEnabledProvider.invoke()
        }
        val lm = locationManager ?: return false
        return try {
            LocationManagerCompat.isLocationEnabled(lm) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Checks whether ACCESS_FINE_LOCATION permission has been granted.
     */
    fun checkLocationPermissionGranted(): Boolean {
        if (permissionGrantedProvider != null) {
            return permissionGrantedProvider.invoke()
        }
        val ctx = context ?: return false
        return try {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Re-evaluates location settings and permissions, updating reactive StateFlows.
     */
    fun refresh() {
        val enabled = checkLocationServicesEnabled()
        val granted = checkLocationPermissionGranted()
        _isLocationServicesEnabled.value = enabled
        _isLocationPermissionGranted.value = granted
    }

    @Synchronized
    private fun registerReceiver() {
        val ctx = context ?: return
        if (!receiverRegistered) {
            try {
                val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(
                        ctx,
                        providersChangedReceiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                } else {
                    ctx.registerReceiver(providersChangedReceiver, filter)
                }
                receiverRegistered = true
            } catch (_: Exception) {
                // Ignore in isolated unit test environments without registered receiver support
            }
        }
    }

    /**
     * Unregisters broadcast receiver to prevent memory leaks.
     */
    @Synchronized
    fun unregister() {
        val ctx = context ?: return
        if (receiverRegistered) {
            try {
                ctx.unregisterReceiver(providersChangedReceiver)
            } catch (_: Exception) {
                // Ignore
            }
            receiverRegistered = false
        }
    }
}
