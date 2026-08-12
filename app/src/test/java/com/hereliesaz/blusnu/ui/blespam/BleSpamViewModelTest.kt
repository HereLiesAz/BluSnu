package com.hereliesaz.blusnu.ui.blespam

import androidx.lifecycle.ViewModel
import com.hereliesaz.blusnu.data.BleSpamModule
import com.hereliesaz.blusnu.data.PayloadType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class BleSpamViewModelTest {

    private lateinit var viewModel: BleSpamViewModel
    private lateinit var module: BleSpamModule
    private val isAdvertisingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        module = mock()
        `when`(module.isAdvertising).thenReturn(isAdvertisingFlow)
        `when`(module.error).thenReturn(errorFlow)
        viewModel = BleSpamViewModel(module)
    }

    @Test
    fun `initial state is correct`() {
        assertEquals(PayloadType.APPLE_CONTINUITY, viewModel.selectedPayloadType.value)
        assertFalse(viewModel.isAdvertising.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `selectPayload updates state`() {
        viewModel.selectPayload(PayloadType.GOOGLE_FAST_PAIR)
        assertEquals(PayloadType.GOOGLE_FAST_PAIR, viewModel.selectedPayloadType.value)
    }

    @Test
    fun `selectPayload is ignored while advertising`() {
        isAdvertisingFlow.value = true
        viewModel.selectPayload(PayloadType.GOOGLE_FAST_PAIR)
        assertEquals(PayloadType.APPLE_CONTINUITY, viewModel.selectedPayloadType.value)
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

    @Test
    fun `error state is exposed from module`() {
        assertNull(viewModel.error.value)
        errorFlow.value = "Advertising failed: data too large"
        assertEquals("Advertising failed: data too large", viewModel.error.value)
    }

    @Test
    fun `clearError delegates to module`() {
        viewModel.clearError()
        verify(module).clearError()
    }

    @Test
    fun `onCleared calls close on module to release resources`() {
        // Use reflection to invoke the protected onCleared method.
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        verify(module).close()
    }

    @Test
    fun `onCleared calls close not just stopSpam`() {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        // close() internally calls stopSpam(), so we verify close() is called
        // but stopSpam() is NOT called directly by onCleared.
        verify(module).close()
        verify(module, never()).stopSpam()
    }
}
