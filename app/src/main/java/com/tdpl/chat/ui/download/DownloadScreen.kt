package com.tdpl.chat.ui.download

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.ui.theme.*

@Composable
fun DownloadScreen(state: ModelState) {
    val infinite = rememberInfiniteTransition(label = "ember-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(InkSurfaceRaised, InkVoid),
                    center = Offset(0.5f, 0.35f),
                    radius = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            val progress = (state as? ModelState.Downloading)?.let {
                if (it.bytesTotal > 0) it.bytesDone.toFloat() / it.bytesTotal else 0f
            } ?: 0f

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = InkBorder, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = stroke
                    )
                    drawArc(
                        color = EmberCore, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false, style = stroke
                    )
                }
                Box(
                    modifier = Modifier
                        .size((90 * pulse).dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(EmberDim, InkSurface)))
                )
                if (state is ModelState.Downloading && state.bytesTotal > 0) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Nyra is settling in",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                statusLine(state),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = TextSecondary
            )

            if (state is ModelState.Downloading) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${mb(state.bytesDone)} / ${mb(state.bytesTotal)} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            if (state is ModelState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Setup hit a snag: ${state.message}",
                    color = DangerCore,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "This only happens once — the model stays on your device after this.",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

private fun mb(bytes: Long) = "%.0f".format(bytes / (1024.0 * 1024.0))

private fun statusLine(state: ModelState): String = when (state) {
    is ModelState.NotReady -> "Getting ready…"
    is ModelState.CheckingForUpdate -> state.message
    is ModelState.RestoringFromBackup -> state.message
    is ModelState.Downloading -> "Bringing the model onto your device"
    is ModelState.Verifying -> "Verifying integrity"
    is ModelState.Ready -> "Almost there…"
    is ModelState.Error -> "Please check your connection and reopen the app"
}
