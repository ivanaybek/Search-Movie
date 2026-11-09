package com.ayaz.viewer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.icons.AyazIcons

/** Son açılan dosya satırı: ad, boyut, son açılma tarihi ve üç nokta menüsü. */
@Composable
fun RecentFileRow(
    file: RecentFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    AyazCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeIcon(file.kind)
            SpacerW(12)
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SpacerH(2)
                Text(
                    "${Formatters.size(file.size)}  •  ${Formatters.relativeDate(file.lastOpened)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Seçenekler: ${file.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Aç") },
                        leadingIcon = { Icon(AyazIcons.FolderOpen, null) },
                        onClick = { menu = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Paylaş") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Listeden kaldır") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}
