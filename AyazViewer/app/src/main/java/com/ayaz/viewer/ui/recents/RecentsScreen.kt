package com.ayaz.viewer.ui.recents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.RecentFileRow
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.main.MainActions
import com.ayaz.viewer.xlsx.SheetSearch

@Composable
fun RecentsScreen(recents: List<RecentFile>, actions: MainActions, onClearAll: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<FileKind?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val normalized = SheetSearch.normalize(query.trim())
    val visible = recents.filter { f ->
        (filter == null || f.kind == filter) && (normalized.isEmpty() || SheetSearch.normalize(f.name).contains(normalized))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Son Açılanlar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (recents.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, contentDescription = "Listeyi temizle") }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Dosya adına göre ara") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Aramayı temizle") }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<FileKind?, String>>(null to "Tümü", FileKind.PDF to "PDF", FileKind.XLSX to "Excel").forEach { (k, label) ->
                FilterChip(
                    selected = filter == k,
                    onClick = { filter = k },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                icon = if (recents.isEmpty()) AyazIcons.History else Icons.Filled.Search,
                title = if (recents.isEmpty()) "Liste boş" else "Sonuç bulunamadı",
                message = if (recents.isEmpty()) {
                    "Açtığınız dosyalar burada görünür. Uygulamayı kapatıp açsanız da liste korunur."
                } else "\"$query\" ile eşleşen dosya yok.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { f ->
                    RecentFileRow(
                        file = f,
                        onOpen = { actions.openRecent(f) },
                        onShare = { actions.share(f) },
                        onRemove = { actions.remove(f) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Liste temizlensin mi?") },
            text = {
                Text(
                    "Son açılanlar listesi temizlenir. Telefonunuzdaki asıl dosyalar silinmez. " +
                        "Ayaz Viewer'da oluşturduğunuz belgeler listede kalır.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearAll() }) { Text("Temizle") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}
