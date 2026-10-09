package com.apex.tracker.sensor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationAvailabilityManagerTest {

    @Test
    fun testLocationAvailabilityStateFlows_GpsDisabled() {
        val manager = LocationAvailabilityManager(
            context = null,
            locationManager = null,
            gpsEnabledProvider = { false },
            permissionGrantedProvider = { true }
        )

        assertThat(manager.isLocationServicesEnabled.value).isFalse()
        assertThat(manager.isLocationPermissionGranted.value).isTrue()
        assertThat(manager.checkLocationServicesEnabled()).isFalse()
        assertThat(manager.checkLocationPermissionGranted()).isTrue()
    }

    @Test
    fun testLocationAvailabilityStateFlows_PermissionMissing() {
        val manager = LocationAvailabilityManager(
            context = null,
            locationManager = null,
            gpsEnabledProvider = { true },
            permissionGrantedProvider = { false }
        )

        assertThat(manager.isLocationServicesEnabled.value).isTrue()
        assertThat(manager.isLocationPermissionGranted.value).isFalse()
        assertThat(manager.checkLocationServicesEnabled()).isTrue()
        assertThat(manager.checkLocationPermissionGranted()).isFalse()
    }

    @Test
    fun testLocationAvailabilityStateFlows_BothEnabled() {
        val manager = LocationAvailabilityManager(
            context = null,
            locationManager = null,
            gpsEnabledProvider = { true },
            permissionGrantedProvider = { true }
        )

        assertThat(manager.isLocationServicesEnabled.value).isTrue()
        assertThat(manager.isLocationPermissionGranted.value).isTrue()
        assertThat(manager.checkLocationServicesEnabled()).isTrue()
        assertThat(manager.checkLocationPermissionGranted()).isTrue()
    }

    @Test
    fun testRefreshUpdatesStateFlows() {
        var gpsStatus = false
        var permStatus = false

        val manager = LocationAvailabilityManager(
            context = null,
            locationManager = null,
            gpsEnabledProvider = { gpsStatus },
            permissionGrantedProvider = { permStatus }
        )

        assertThat(manager.isLocationServicesEnabled.value).isFalse()
        assertThat(manager.isLocationPermissionGranted.value).isFalse()

        // Toggle to true and refresh
        gpsStatus = true
        permStatus = true
        manager.refresh()

        assertThat(manager.isLocationServicesEnabled.value).isTrue()
        assertThat(manager.isLocationPermissionGranted.value).isTrue()
    }

    @Test
    fun testUnregisterDoesNotThrow() {
        val manager = LocationAvailabilityManager(
            context = null,
            locationManager = null,
            gpsEnabledProvider = { true },
            permissionGrantedProvider = { true }
        )
        manager.unregister()
        manager.unregister() // Multiple calls idempotent
    }
}
