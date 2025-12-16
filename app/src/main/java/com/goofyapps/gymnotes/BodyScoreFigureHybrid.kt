package com.goofyapps.gymnotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min


// -------------------- FIGURE (better silhouette) --------------------

@Composable
fun BodyScoreFigureHybrid(
    scores: Map<String, Int>,
    showBack: Boolean,
    onToggleSide: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    fun score01(id: String): Float = ((scores[id] ?: 0).coerceIn(0, 100) / 100f)
    fun heatAlpha(v: Float): Float = (0.06f + v * 0.22f).coerceIn(0.06f, 0.30f)

    Card(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(12.dp)
        ) {
            Canvas(Modifier.fillMaxSize().align(Alignment.Center)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f

                val chest = score01("chest")
                val back = score01("back")
                val shoulders = score01("shoulders")
                val arms = score01("arms")
                val legs = score01("legs")
                val core = score01("core")

                val emphasizeChest = !showBack
                val chestOrBack = if (emphasizeChest) chest else back

                val headR = min(w, h) * 0.06f
                val headCY = headR * 1.35f

                val neckY = headCY + headR * 1.15f
                val shoulderY = neckY + headR * 0.55f
                val chestY = h * 0.44f
                val waistY = h * 0.62f
                val hipY = h * 0.73f
                val footY = h * 0.95f

                val shoulderHalf = (w * 0.19f) * (1f + shoulders * 0.35f)
                val chestHalf = (w * 0.165f) * (1f + chestOrBack * 0.22f)
                val waistHalf = (w * 0.13f) * (1f - core * 0.14f)
                val hipHalf = (w * 0.155f) * (1f + legs * 0.22f)

                val armX = shoulderHalf + w * 0.065f
                val armTopY = shoulderY + headR * 0.25f
                val armBottomY = waistY + (h * 0.02f)
                val armThickness = (w * 0.030f) * (1f + arms * 0.28f)

                val legGap = w * 0.055f
                val legHalf = max(1f, (hipHalf - legGap) / 2f)
                val legThickness = (legHalf * 0.88f) * (1f + legs * 0.15f)

                // Head
                drawCircle(
                    color = onSurface.copy(alpha = 0.16f),
                    radius = headR,
                    center = Offset(cx, headCY)
                )

                // Torso outline (rounded-ish silhouette using a curvy path)
                val torso = Path().apply {
                    moveTo(cx - shoulderHalf, shoulderY)
                    quadraticBezierTo(cx, shoulderY - headR * 0.55f, cx + shoulderHalf, shoulderY)
                    quadraticBezierTo(cx + chestHalf, (shoulderY + chestY) / 2f, cx + chestHalf, chestY)
                    quadraticBezierTo(cx + waistHalf, (chestY + waistY) / 2f, cx + waistHalf, waistY)
                    quadraticBezierTo(cx + hipHalf, (waistY + hipY) / 2f, cx + hipHalf, hipY)

                    lineTo(cx - hipHalf, hipY)

                    quadraticBezierTo(cx - waistHalf, (waistY + hipY) / 2f, cx - waistHalf, waistY)
                    quadraticBezierTo(cx - chestHalf, (chestY + waistY) / 2f, cx - chestHalf, chestY)
                    quadraticBezierTo(cx - chestHalf, (shoulderY + chestY) / 2f, cx - shoulderHalf, shoulderY)
                    close()
                }

                drawPath(torso, onSurface.copy(alpha = 0.10f), style = Fill)
                drawPath(torso, onSurface.copy(alpha = 0.18f), style = Stroke(width = 2f))

                // Arms (tapered by drawing 2 lines each)
                drawLine(
                    color = onSurface.copy(alpha = 0.14f),
                    start = Offset(cx - armX, armTopY),
                    end = Offset(cx - armX, armBottomY),
                    strokeWidth = armThickness
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.14f),
                    start = Offset(cx + armX, armTopY),
                    end = Offset(cx + armX, armBottomY),
                    strokeWidth = armThickness
                )

                // Legs
                val leftLegX = cx - legGap / 2f - legHalf
                val rightLegX = cx + legGap / 2f + legHalf

                drawLine(
                    color = onSurface.copy(alpha = 0.14f),
                    start = Offset(leftLegX, hipY),
                    end = Offset(leftLegX, footY),
                    strokeWidth = legThickness
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.14f),
                    start = Offset(rightLegX, hipY),
                    end = Offset(rightLegX, footY),
                    strokeWidth = legThickness
                )

                // Heat overlays (segments)
                val shoulderBand = Path().apply {
                    moveTo(cx - shoulderHalf, shoulderY)
                    lineTo(cx + shoulderHalf, shoulderY)
                    lineTo(cx + chestHalf, chestY)
                    lineTo(cx - chestHalf, chestY)
                    close()
                }
                drawPath(shoulderBand, primary.copy(alpha = heatAlpha(shoulders)), style = Fill)

                val chestBackBand = Path().apply {
                    moveTo(cx - chestHalf, chestY)
                    lineTo(cx + chestHalf, chestY)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx - waistHalf, waistY)
                    close()
                }
                drawPath(chestBackBand, primary.copy(alpha = heatAlpha(chestOrBack)), style = Fill)

                val coreBand = Path().apply {
                    moveTo(cx - waistHalf, waistY)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx + hipHalf, hipY)
                    lineTo(cx - hipHalf, hipY)
                    close()
                }
                drawPath(coreBand, primary.copy(alpha = heatAlpha(core)), style = Fill)

                // Arms / legs heat
                drawLine(
                    color = primary.copy(alpha = heatAlpha(arms)),
                    start = Offset(cx - armX, armTopY),
                    end = Offset(cx - armX, armBottomY),
                    strokeWidth = armThickness * 1.04f
                )
                drawLine(
                    color = primary.copy(alpha = heatAlpha(arms)),
                    start = Offset(cx + armX, armTopY),
                    end = Offset(cx + armX, armBottomY),
                    strokeWidth = armThickness * 1.04f
                )
                drawLine(
                    color = primary.copy(alpha = heatAlpha(legs)),
                    start = Offset(leftLegX, hipY),
                    end = Offset(leftLegX, footY),
                    strokeWidth = legThickness * 1.04f
                )
                drawLine(
                    color = primary.copy(alpha = heatAlpha(legs)),
                    start = Offset(rightLegX, hipY),
                    end = Offset(rightLegX, footY),
                    strokeWidth = legThickness * 1.04f
                )
            }

            IconButton(
                onClick = onToggleSide,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.FlipCameraAndroid,
                    contentDescription = if (showBack) "Show front" else "Show back"
                )
            }

            Text(
                text = if (showBack) "Back" else "Front",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp)
                    .alpha(0.65f)
            )
        }
    }
}
