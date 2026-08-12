package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.blusnu.data.CompassManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.LocationManager
import com.hereliesaz.blusnu.data.TandemManager
import com.hereliesaz.blusnu.data.TargetDevice
import android.content.SharedPreferences
import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class FindViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @Before
    fun setupCoroutines() {
        Dispatchers.setMain(testDispatcher)
    }

    @org.junit.After
    fun tearDownCoroutines() {
        Dispatchers.resetMain()
    }

    private lateinit var viewModel: FindViewModel
    private lateinit var application: Application
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var hardwareManager: HardwareManager
    private lateinit var locationManager: LocationManager
    private lateinit var compassManager: CompassManager
    private lateinit var tandemManager: TandemManager
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        application = mock()
        deviceRepository = mock()
        hardwareManager = mock()
        locationManager = mock()
        compassManager = mock()
        tandemManager = mock()
        sharedPreferences = mock()

        // Mock SharedPreferences
        `when`(application.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(sharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false)

        // Mock state flow for hardwareManager
        val hardwareStateFlow = MutableStateFlow(HardwareState.DISCONNECTED)
        `when`(hardwareManager.hardwareState).thenReturn(hardwareStateFlow)

        // Mock getSecondaryRssi
        kotlinx.coroutines.runBlocking {
            `when`(hardwareManager.getSecondaryRssi(anyString())).thenReturn(null)
        }

        // Mock tandem manager state
        val tandemDataFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.hereliesaz.blusnu.data.TandemData>()
        `when`(tandemManager.tandemData).thenReturn(tandemDataFlow)

        // Mock repository
        `when`(deviceRepository.allDevices).thenReturn(flowOf(emptyList()))

        // Mock location and compass flows with a location so userLocation is set
        val location = mock<Location>()
        `when`(location.latitude).thenReturn(10.0)
        `when`(location.longitude).thenReturn(20.0)
        `when`(locationManager.locationFlow()).thenReturn(flowOf(location))
        `when`(compassManager.azimuthFlow()).thenReturn(flowOf())

        viewModel = FindViewModel(application, deviceRepository, hardwareManager, locationManager, compassManager, tandemManager)
        viewModel.startTracking() // To start collecting location

        // Wait for FindViewModel init blocks and tracking to finish
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `toggleTandemMode toggles state`() {
        // Initial state is false
        assertEquals(false, viewModel.uiState.value.isTandemModeEnabled)

        viewModel.toggleTandemMode()
        assertEquals(true, viewModel.uiState.value.isTandemModeEnabled)

        viewModel.toggleTandemMode()
        assertEquals(false, viewModel.uiState.value.isTandemModeEnabled)
    }

    @Test
    fun `onDeviceRssiUpdated updates direction finding state`() = runTest {
        val device = TargetDevice(macAddress = "AA:BB:CC:DD:EE:FF", name = "Test Device", rssi = -50, protocol = com.hereliesaz.blusnu.data.Protocol.BLE)
        viewModel.selectDevice(device)

        // onDeviceRssiUpdated now runs asynchronously due to coroutines, so advance time
        viewModel.onDeviceRssiUpdated(device, -40)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value

        // The reported RSSI must be reflected on the tracked device.
        assertEquals(-40, state.selectedDevice?.rssi)

        // An RSSI reading must produce a path-loss distance estimate.
        assertNotNull(state.rssiDistance)
        assertTrue("rssiDistance should be positive", state.rssiDistance!! > 0.0)

        // A strong signal (-40) exceeds the confidence threshold, so a bearing must be set.
        // The compass azimuth defaults to 0f in this test (compass flow is empty), so the
        // strongest bucket is index 0 -> bearing 0f.
        assertNotNull(state.estimatedBearing)
        assertEquals(0f, state.estimatedBearing!!, 0.001f)
    }

    @Test
    fun `onDeviceRssiUpdated produces a normalized bearing in the 0-360 range`() = runTest {
        val device = TargetDevice(macAddress = "AA:BB:CC:DD:EE:FF", name = "Test Device", rssi = -50, protocol = com.hereliesaz.blusnu.data.Protocol.BLE)
        viewModel.selectDevice(device)

        // Feed several updates to accumulate weight in the direction buckets. Regardless of
        // azimuth sign, the normalization `((azimuth % 360) + 360) % 360` must keep the
        // resulting bearing a valid heading: a multiple of 10 within [0, 360).
        viewModel.onDeviceRssiUpdated(device, -45)
        viewModel.onDeviceRssiUpdated(device, -50)
        viewModel.onDeviceRssiUpdated(device, -40)
        testScheduler.advanceUntilIdle()

        val bearing = viewModel.uiState.value.estimatedBearing
        assertNotNull(bearing)
        assertTrue("bearing should be >= 0", bearing!! >= 0f)
        assertTrue("bearing should be < 360", bearing < 360f)
        assertEquals("bearing should snap to a 10-degree bucket", 0f, bearing % 10f, 0.001f)
    }
}
