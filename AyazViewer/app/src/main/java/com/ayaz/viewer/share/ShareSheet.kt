package com.ayaz.viewer.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayaz.viewer.ayazApp
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.components.FileTypeIcon
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.theme.AyazColors
import com.ayaz.viewer.ui.theme.LocalAyazColors
import kotlinx.coroutines.launch

/**
 * Uygulamanın kendi paylaşım penceresi. Yalnızca iki seçenek gösterilir: WhatsApp ve Telegram.
 * Sistem paylaşım menüsü hiçbir zaman açılmaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(file: RecentFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.ayazApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<ShareApp?>(null) }
    val whatsapp = remember { ShareTargets.installedVariants(context, ShareApp.WHATSAPP) }
    val telegram = remember { ShareTargets.installedVariants(context, ShareApp.TELEGRAM) }

    fun start(variant: ShareVariant) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            when (val r = ShareTargets.share(context, app.documents, file, variant)) {
                ShareResult.Started -> {
                    busy = false
                    sheetState.hide()
                    onDismiss()
                }
                is ShareResult.Failed -> {
                    busy = false
                    message = r.message
                }
            }
        }
    }

    fun onAppTap(target: ShareApp, variants: List<ShareVariant>) {
        when {
            variants.isEmpty() -> {
                expanded = null
                message = ShareTargets.notInstalledMessage(target)
            }
            variants.size == 1 -> start(variants.first())
            else -> {
                message = null
                expanded = if (expanded == target) null else target
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Paylaş", style = MaterialTheme.typography.titleLarge)
            SpacerH(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(file.kind, size = 40.dp)
                SpacerW(12)
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Formatters.size(file.size)} • dosya olarak gönderilir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            SpacerH(20)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareOption(
                    modifier = Modifier.weight(1f),
                    title = "WhatsApp",
                    icon = AyazIcons.Chat,
                    color = AyazColors.WhatsAppGreen,
                    installed = whatsapp.isNotEmpty(),
                    selected = expanded == ShareApp.WHATSAPP,
                    onClick = { onAppTap(ShareApp.WHATSAPP, whatsapp) },
                )
                ShareOption(
                    modifier = Modifier.weight(1f),
                    title = "Telegram",
                    icon = Icons.AutoMirrored.Filled.Send,
                    color = AyazColors.TelegramBlue,
                    installed = telegram.isNotEmpty(),
                    selected = expanded == ShareApp.TELEGRAM,
                    onClick = { onAppTap(ShareApp.TELEGRAM, telegram) },
                )
            }
            AnimatedVisibility(
                visible = expanded != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val variants = if (expanded == ShareApp.WHATSAPP) whatsapp else telegram
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        "Hangi uygulama ile gönderilsin?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SpacerH(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        variants.forEach { v ->
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable(role = Role.Button) { start(v) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) { Text(v.label, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    SpacerW(10)
                    Text(message ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun ShareOption(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    installed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val extra = LocalAyazColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(extra.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else extra.cardBorder, shape)
            .clickable(role = Role.Button, onClickLabel = "$title ile paylaş", onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (installed) color else color.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        SpacerH(10)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (installed) "Dosya olarak gönder" else "Yüklü değil",
            style = MaterialTheme.typography.bodySmall,
            color = if (installed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}
