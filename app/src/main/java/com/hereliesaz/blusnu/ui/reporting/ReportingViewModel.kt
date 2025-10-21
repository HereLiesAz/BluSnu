package com.hereliesaz.blusnu.ui.reporting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.LogEntry
import kotlinx.coroutines.flow.StateFlow

class ReportingViewModel(application: Application) : AndroidViewModel(application) {

    val logs: StateFlow<List<LogEntry>> = ActionLogger.logs

    fun generateMarkdownReport(): String {
        val builder = StringBuilder()
        builder.append("# BluSnu Security Report\n\n")
        builder.append("## Logged Actions\n\n")
        logs.value.forEach { log ->
            builder.append("- **${log.timestamp}**: ${log.message}\n")
        }
        return builder.toString()
    }

}
