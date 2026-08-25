package com.example.signtalk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.signtalk.ui.theme.ErrorRed
import com.example.signtalk.ui.theme.SuccessGreen
import com.example.signtalk.ui.theme.WarnAmber

/** Recognition confidence display: a labeled progress bar that changes color by confidence band. */
@Composable
fun ConfidenceMeter(confidence: Float, modifier: Modifier = Modifier) {
    val percent = (confidence * 100).toInt()
    val color = when {
        confidence >= 0.8f -> SuccessGreen
        confidence >= 0.6f -> WarnAmber
        else -> ErrorRed
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Confidence: $percent%", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
