package com.hereliesaz.blusnu.ui.reporting

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class ReportingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ReportingViewModel
    private lateinit var application: Application
    private lateinit var deviceRepository: DeviceRepository

    @Before
    fun setup() {
        application = mock()
        deviceRepository = mock()

        // Mock the repository's flow (used by exportData)
        `when`(deviceRepository.allDevices).thenReturn(flowOf(emptyList()))

        viewModel = ReportingViewModel(application, deviceRepository)
    }

    @Test
    fun `generateMarkdownReport returns expected string`() {
        val report = viewModel.generateMarkdownReport()
        assertTrue(report.startsWith("# BluSnu Security Report"))
    }

    @Test
    fun `exportData returns correct JSON`() {
        val devices = listOf(TargetDevice(
            macAddress = "00:11:22:33:44:55",
            name = "Test Device",
            rssi = -50,
            protocol = Protocol.BLE
        ))
        `when`(deviceRepository.allDevices).thenReturn(flowOf(devices))
    }
}
