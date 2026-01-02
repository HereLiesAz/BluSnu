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
        // brakToothModule is instantiated inside ViewModel

        `when`(deviceRepository.allDevices).thenReturn(flowOf(emptyList()))

        viewModel = BrakToothViewModel(deviceRepository)
    }

    @Test
    fun `initial state is correct`() {
        assertNull(viewModel.selectedDevice.value)
    }
}
