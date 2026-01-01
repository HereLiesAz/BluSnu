package com.hereliesaz.blusnu.ui.blespam

import com.hereliesaz.blusnu.data.BleSpamModule
import com.hereliesaz.blusnu.data.PayloadType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class BleSpamViewModelTest {

    private lateinit var viewModel: BleSpamViewModel
    private lateinit var module: BleSpamModule
    private val isAdvertisingFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        module = mock()
        `when`(module.isAdvertising).thenReturn(isAdvertisingFlow)
        viewModel = BleSpamViewModel(module)
    }

    @Test
    fun `initial state is correct`() {
        assertEquals(PayloadType.APPLE_CONTINUITY, viewModel.selectedPayloadType.value)
        assertFalse(viewModel.isAdvertising.value)
    }

    @Test
    fun `selectPayload updates state`() {
        viewModel.selectPayload(PayloadType.GOOGLE_FAST_PAIR)
        assertEquals(PayloadType.GOOGLE_FAST_PAIR, viewModel.selectedPayloadType.value)
    }

    @Test
    fun `toggleSpam starts spam when not advertising`() {
        isAdvertisingFlow.value = false
        viewModel.toggleSpam()
        verify(module).startSpam(PayloadType.APPLE_CONTINUITY)
    }

    @Test
    fun `toggleSpam stops spam when advertising`() {
        isAdvertisingFlow.value = true
        viewModel.toggleSpam()
        verify(module).stopSpam()
    }
}
