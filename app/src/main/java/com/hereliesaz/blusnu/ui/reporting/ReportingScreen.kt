package com.hereliesaz.blusnu.ui.reporting

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.ui.components.ScreenTitle

@Composable
fun ReportingScreen(viewModel: ReportingViewModel = viewModel()) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val reportContent = viewModel.generateMarkdownReport()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(reportContent.toByteArray())
                }
                Toast.makeText(context, "Report exported successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/markdown"
                putExtra(Intent.EXTRA_TITLE, "blusnu_report.md")
            }
            createDocumentLauncher.launch(intent)
        }) {
            Text("Export Report")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                Text("${log.timestamp}: ${log.message}")
            }
        }
    }
    }
}
