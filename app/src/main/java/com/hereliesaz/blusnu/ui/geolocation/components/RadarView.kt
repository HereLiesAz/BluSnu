package com.hereliesaz.blusnu.ui.geolocation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice

@Composable
fun RadarView(
    modifier: Modifier = Modifier,
    devices: List<TargetDevice>,
    distances: Map<String, Double>
) {
    Canvas(modifier = modifier.size(300.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        // Draw concentric circles
        for (i in 1..4) {
            drawCircle(
                color = Color.Gray,
                radius = radius * i / 4,
                center = center,
                style = Stroke(width = 2f)
            )
        }

        // Draw devices
        devices.forEach { device ->
            val distance = distances[device.macAddress] ?: 0.0
            val angle = (device.macAddress.hashCode() % 360).toDouble()
            val x = center.x + (distance * 10 * kotlin.math.cos(Math.toRadians(angle))).toFloat()
            val y = center.y + (distance * 10 * kotlin.math.sin(Math.toRadians(angle))).toFloat()
            drawCircle(
                color = Color.Blue,
                radius = 10f,
                center = Offset(x, y)
            )
        }
    }
}
