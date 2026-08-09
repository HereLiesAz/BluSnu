package com.hereliesaz.blusnu.ui.reporting

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for the surviving reporting implementation ([ReportingViewModel]).
 *
 * The previous dead `ui/report/ReportViewModel` was removed; this test targets the wired
 * ViewModel instead. It exercises the stable [ReportingViewModel.generateMarkdownReport]
 * API and the action-log accumulation that feeds it.
 */
class ReportingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ReportingViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        application = mock()
        viewModel = ReportingViewModel(application)
    }

    @Test
    fun `generateMarkdownReport for an empty log has header and no-actions notice`() {
        val report = viewModel.generateMarkdownReport()

        assertTrue("report should start with the document header", report.startsWith("# BluSnu Report"))
        assertTrue("empty report should state that nothing was logged", report.contains("No actions logged."))
        // No table should be emitted when there is nothing to tabulate.
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
