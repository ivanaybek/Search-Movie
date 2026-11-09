package com.ayaz.viewer.ui.files

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.ayazApp
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.FolderEntry
import com.ayaz.viewer.data.GrantedFolder
import com.ayaz.viewer.ui.components.AyazCard
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.FileTypeIcon
import com.ayaz.viewer.ui.components.IconBadge
import com.ayaz.viewer.ui.components.SectionHeader
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.main.InitialLocations
import com.ayaz.viewer.ui.main.MainActions
import com.ayaz.viewer.ui.main.MainViewModel
import com.ayaz.viewer.xlsx.SheetSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Klasör gezinme yığınındaki bir adım. */
data class Crumb(val treeUri: String, val documentId: String, val name: String)

private val CrumbsSaver = listSaver<List<Crumb>, String>(
    save = { list -> list.flatMap { listOf(it.treeUri, it.documentId, it.name) } },
    restore = { flat -> flat.chunked(3).map { Crumb(it[0], it[1], it[2]) } },
)

@Composable
fun FilesScreen(vm: MainViewModel, actions: MainActions) {
    var crumbs by rememberSaveable(stateSaver = CrumbsSaver) { mutableStateOf(emptyList<Crumb>()) }
    BackHandler(enabled = crumbs.isNotEmpty()) { crumbs = crumbs.dropLast(1) }
    if (crumbs.isEmpty()) {
        FilesHome(vm, actions, onBrowse = { folder ->
            val root = runCatching {
                android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(folder.treeUri))
            }.getOrNull()
            if (root != null) crumbs = listOf(Crumb(folder.treeUri, root, folder.name))
        })
    } else {
        FolderBrowser(
            crumbs = crumbs,
            onBack = { crumbs = crumbs.dropLast(1) },
            onEnter = { e -> crumbs = crumbs + Crumb(crumbs.last().treeUri, e.documentId, e.name) },
            onOpenFile = { e -> actions.openUri(e.uri) },
        )
    }
}

@Composable
private fun FilesHome(vm: MainViewModel, actions: MainActions, onBrowse: (GrantedFolder) -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Dosyalar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
        item { SectionHeader("Hızlı erişim") }
        item {
            QuickRow("Tüm dosyalar", "Sistem dosya seçicisini aç", AyazIcons.FolderOpen) { actions.pick(null, null) }
        }
        item {
            QuickRow("İndirilenler", "Download klasöründeki PDF ve Excel dosyaları", AyazIcons.Download) {
                actions.pick(null, InitialLocations.DOWNLOADS)
            }
        }
        item {
            QuickRow("Belgeler", "Documents klasörü", AyazIcons.Description) { actions.pick(null, InitialLocations.DOCUMENTS) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Klasörlerim", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                FilledTonalButton(onClick = actions.pickFolder) {
                    Icon(AyazIcons.CreateNewFolder, null, modifier = Modifier.padding(end = 6.dp))
                    Text("Klasör ekle")
                }
            }
        }
        if (folders.isEmpty()) {
            item {
                AyazCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Sık kullandığınız bir klasörü (ör. Download/WhatsApp Documents veya Belgeler) ekleyerek içindeki " +
                            "PDF ve Excel dosyalarını burada listeleyebilirsiniz. Ayaz Viewer geniş depolama izni istemez; " +
                            "yalnızca sizin seçtiğiniz klasörü okuyabilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(folders, key = { it.treeUri }) { f ->
                AyazCard(Modifier.fillMaxWidth().animateItem(), onClick = { onBrowse(f) }) {
                    Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AyazIcons.Folder, MaterialTheme.colorScheme.primary)
                        SpacerW(12)
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.removeFolder(f) }) { Icon(Icons.Filled.Close, "Klasör erişimini kaldır: ${f.name}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AyazCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.secondary)
            SpacerW(12)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderBrowser(
    crumbs: List<Crumb>,
    onBack: () -> Unit,
    onEnter: (FolderEntry) -> Unit,
    onOpenFile: (FolderEntry) -> Unit,
) {
    val context = LocalContext.current
    val current = crumbs.last()
    var entries by remember(current) { mutableStateOf<List<FolderEntry>?>(null) }
    var error by remember(current) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(current.documentId) { mutableStateOf("") }
    var filter by rememberSaveable(current.documentId) { mutableStateOf<FileKind?>(null) }

    LaunchedEffect(current) {
        try {
            entries = withContext(Dispatchers.IO) {
                context.ayazApp.folders.listChildren(Uri.parse(current.treeUri), current.documentId)
            }
        } catch (e: Exception) {
            error = "Klasör okunamadı. Erişim izni kaldırılmış olabilir; klasörü yeniden ekleyin."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Column(Modifier.weight(1f)) {
                Text(current.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    crumbs.joinToString("  ›  ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Bu klasörde ara") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Temizle") } },
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
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
            }
        }
        val list = entries
        when {
            error != null -> EmptyState(AyazIcons.Folder, "Klasör açılamadı", error!!)
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val q = SheetSearch.normalize(query.trim())
                val visible = list.filter { e ->
                    (q.isEmpty() || SheetSearch.normalize(e.name).contains(q)) &&
                        (filter == null || e.isDirectory || e.kind == filter)
                }
                if (visible.isEmpty()) {
                    EmptyState(AyazIcons.FolderOpen, "Gösterilecek dosya yok", "Bu klasörde PDF veya Excel dosyası bulunamadı.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { it.documentId }) { e ->
                            AyazCard(Modifier.fillMaxWidth(), onClick = { if (e.isDirectory) onEnter(e) else onOpenFile(e) }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (e.isDirectory) IconBadge(AyazIcons.Folder, MaterialTheme.colorScheme.primary) else FileTypeIcon(e.kind, size = 40.dp)
                                    SpacerW(12)
                                    Column(Modifier.weight(1f)) {
                                        Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!e.isDirectory) {
                                            SpacerH(2)
                                            Text(
                                                buildString {
                                                    append(Formatters.size(e.size))
                                                    if (e.lastModified > 0) append("  •  ").append(Formatters.relativeDate(e.lastModified))
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (e.isDirectory) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
