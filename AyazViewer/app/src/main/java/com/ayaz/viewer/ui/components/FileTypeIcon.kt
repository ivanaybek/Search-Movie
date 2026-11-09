package com.ayaz.viewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.ui.theme.AyazColors

fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.PDF -> AyazColors.PdfRed
    FileKind.XLSX -> AyazColors.ExcelGreen
    FileKind.UNKNOWN -> AyazColors.Blue
}

/** Özgün dosya türü simgesi: köşesi kıvrık belge + tür etiketi. */
@Composable
fun FileTypeIcon(kind: FileKind, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = kindColor(kind)
    val label = when (kind) {
        FileKind.PDF -> "PDF"
        FileKind.XLSX -> "XLS"
        FileKind.UNKNOWN -> "?"
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            // yumuşak arka plan rozeti
            drawRoundRect(color.copy(alpha = 0.14f), cornerRadius = CornerRadius(w * 0.28f))
            // belge
            val left = w * 0.27f
            val top = h * 0.16f
            val right = w * 0.73f
            val bottom = h * 0.84f
            val fold = w * 0.15f
            val doc = Path().apply {
                moveTo(left + w * 0.05f, top)
                lineTo(right - fold, top)
                lineTo(right, top + fold)
                lineTo(right, bottom - w * 0.05f)
                quadraticTo(right, bottom, right - w * 0.05f, bottom)
                lineTo(left + w * 0.05f, bottom)
                quadraticTo(left, bottom, left, bottom - w * 0.05f)
                lineTo(left, top + w * 0.05f)
                quadraticTo(left, top, left + w * 0.05f, top)
                close()
            }
            drawPath(doc, color)
            val corner = Path().apply {
                moveTo(right - fold, top)
                lineTo(right - fold, top + fold * 0.8f)
                quadraticTo(right - fold, top + fold, right - fold * 0.8f, top + fold)
                lineTo(right, top + fold)
                close()
            }
            drawPath(corner, Color.White.copy(alpha = 0.45f))
            if (kind == FileKind.XLSX) {
                // küçük tablo çizgileri
                val gx = left + w * 0.08f
                val gw = (right - left) - w * 0.16f
                val gy = top + h * 0.22f
                for (i in 0..2) {
                    drawRect(
                        Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(gx, gy + i * h * 0.075f),
                        size = Size(gw, h * 0.022f),
                    )
                }
            } else if (kind == FileKind.PDF) {
                val gx = left + w * 0.08f
                val gy = top + h * 0.22f
                for (i in 0..2) {
                    drawRect(
                        Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(gx, gy + i * h * 0.075f),
                        size = Size((right - left) * (if (i == 2) 0.45f else 0.72f), h * 0.022f),
                    )
                }
            }
        }
        Text(
            label,
            modifier = Modifier.align(Alignment.BottomCenter),
            style = TextStyle(
                color = Color.White,
                fontSize = (size.value * 0.2f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}
