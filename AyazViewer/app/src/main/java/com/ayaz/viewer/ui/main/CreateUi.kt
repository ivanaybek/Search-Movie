package com.ayaz.viewer.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.ui.components.FileTypeIcon
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.theme.LocalAyazColors

/** "Oluştur" seçenekleri: yeni tablo, metinden PDF, boş PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    onDismiss: () -> Unit,
    onSpreadsheet: () -> Unit,
    onTextPdf: () -> Unit,
    onBlankPdf: (Int) -> Unit,
) {
    var pages by rememberSaveable { mutableIntStateOf(1) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Yeni belge oluştur", style = MaterialTheme.typography.titleLarge)
            Text(
                "Belge uygulamada saklanır; ⋮ menüsündeki \"Telefona kaydet\" ile telefona kaydedebilir, WhatsApp veya Telegram ile paylaşabilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpacerH(16)
            CreateOption(FileKind.XLSX, "Yeni Excel tablosu", "Boş tablo; hücrelere yazın, satır/sütun ekleyin", onSpreadsheet)
            SpacerH(10)
            CreateOption(FileKind.PDF, "Yeni PDF (metin yaz)", "Başlık ve metin yazın, sayfalara bölünmüş PDF oluşsun", onTextPdf)
            SpacerH(10)
            CreateOption(FileKind.PDF, "Boş PDF", "Kalem, vurgulayıcı ve metin notu için boş sayfa(lar)") { onBlankPdf(pages) }
            Row(Modifier.padding(start = 56.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5).forEach { n ->
                    FilterChip(selected = pages == n, onClick = { pages = n }, label = { Text("$n sayfa") })
                }
            }
        }
    }
}

@Composable
private fun CreateOption(kind: FileKind, title: String, subtitle: String, onClick: () -> Unit) {
    val extra = LocalAyazColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp)
            .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(kind, size = 44.dp)
        SpacerW(12)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Metinden PDF oluşturma ekranı. */
@Composable
fun TextPdfComposer(onDismiss: () -> Unit, onCreate: (title: String, body: String, size: Float) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableFloatStateOf(12f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Kapat") }
                    Text("Yeni PDF", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { onCreate(title, body, size) }, enabled = title.isNotBlank() || body.isNotBlank()) {
                        Text("PDF oluştur")
                    }
                }
                SpacerH(12)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Başlık" },
                    label = { Text("Başlık") },
                    singleLine = true,
                )
                SpacerH(10)
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Metin" },
                    label = { Text("Metin") },
                    placeholder = { Text("Yazınızı buraya girin. Uzun metinler otomatik olarak sayfalara bölünür.") },
                )
                SpacerH(10)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yazı boyutu", style = MaterialTheme.typography.labelLarge)
                    listOf(10f to "Küçük", 12f to "Normal", 16f to "Büyük").forEach { (v, label) ->
                        FilterChip(selected = size == v, onClick = { size = v }, label = { Text(label) })
                    }
                }
            }
        }
    }
}
