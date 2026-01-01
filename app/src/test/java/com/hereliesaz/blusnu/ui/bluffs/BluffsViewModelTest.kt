package com.hereliesaz.blusnu.ui.bluffs

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.blusnu.data.BluffsMode
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class BluffsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: BluffsViewModel
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var bluffsModule: BluffsModule

    @Before
    fun setup() {
        deviceRepository = mock()
        bluffsModule = mock()

        `when`(deviceRepository.allDevices).thenReturn(flowOf(emptyList()))
        `when`(bluffsModule.logs).thenReturn(MutableStateFlow(emptyList()))

        viewModel = BluffsViewModel(deviceRepository, bluffsModule)
    }

    @Test
    fun `initial state is correct`() {
        assertEquals(BluffsMode.A1_SPOOF_LSC_CENTRAL, viewModel.selectedMode.value)
    }

    @Test
    fun `onModeSelected updates state`() {
        viewModel.onModeSelected(BluffsMode.A3_MITM_LSC)
        assertEquals(BluffsMode.A3_MITM_LSC, viewModel.selectedMode.value)
    }
}
