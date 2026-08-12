package com.hereliesaz.blusnu.ui.reporting

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.blusnu.data.ActionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ReportingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ReportingViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ActionLogger.clearLogs()
        application = mock()
        viewModel = ReportingViewModel(application)
    }

    @After
    fun tearDown() {
        ActionLogger.clearLogs()
        Dispatchers.resetMain()
    }

    @Test
    fun `generateMarkdownReport for an empty log has header and no-actions notice`() {
        val report = viewModel.generateMarkdownReport()

        assertTrue("report should start with the document header", report.startsWith("# BluSnu Report"))
        assertTrue("empty report should state that nothing was logged", report.contains("No actions logged."))
        assertFalse("empty report should not contain a log table", report.contains("| Timestamp | Action |"))
        assertTrue("log should start empty", viewModel.logs.value.isEmpty())
    }

    @Test
    fun `addLog appends an entry that appears in the markdown report`() {
        viewModel.addLog("Started BLE scan")
        viewModel.addLog("Connected to AA:BB:CC:DD:EE:FF")

        assertEquals(2, viewModel.logs.value.size)
        assertEquals("Started BLE scan", viewModel.logs.value[0].message)

        val report = viewModel.generateMarkdownReport()

        assertTrue("report should render a markdown table header", report.contains("| Timestamp | Action |"))
        assertTrue("report should include the first logged action", report.contains("Started BLE scan"))
        assertTrue("report should include the second logged action", report.contains("Connected to AA:BB:CC:DD:EE:FF"))
        assertFalse("report with entries should not show the empty notice", report.contains("No actions logged."))
    }
}
