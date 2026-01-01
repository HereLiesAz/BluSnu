package com.hereliesaz.blusnu.ui.braktooth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.blusnu.data.BrakToothModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class BrakToothViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: BrakToothViewModel
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var brakToothModule: BrakToothModule

    @Before
    fun setup() {
        deviceRepository = mock()
        brakToothModule = mock()

        `when`(deviceRepository.allDevices).thenReturn(flowOf(emptyList()))
        `when`(brakToothModule.connectionStatus).thenReturn(MutableStateFlow("Disconnected"))
        `when`(brakToothModule.logs).thenReturn(MutableStateFlow(emptyList()))

        viewModel = BrakToothViewModel(deviceRepository, brakToothModule)
    }

    @Test
    fun `initial state is correct`() {
        assertNull(viewModel.selectedDevice.value)
    }
}
