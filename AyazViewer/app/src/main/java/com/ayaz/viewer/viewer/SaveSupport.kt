package com.ayaz.viewer.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/** Görüntüleyiciden gelen kaydetme isteği: [produce] kaydedilecek dosyayı hazırlar. */
class SaveRequest(
    val produce: suspend () -> File,
    /** Asıl dosyanın üzerine yazıldıktan sonra çağrılır. (Yeni kopyada görüntüleyici yeni dosyayla açılır.) */
    val onOverwritten: () -> Unit,
)

/** "Üzerine yaz / Yeni kopya" seçimi (her kaydetmede sorulur). */
@Composable
fun SaveChoiceDialog(
    fileName: String,
    canOverwrite: Boolean,
    onOverwrite: () -> Unit,
    onNewCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Değişiklikleri kaydet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("\"$fileName\" için nasıl kaydedilsin?")
                if (canOverwrite) {
                    Text(
                        "Üzerine yazarsanız asıl dosya değişir; bu işlem geri alınamaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "Bu dosyaya yazma izni yok (örneğin başka bir uygulamadan salt okunur açıldı). Yeni kopya olarak kaydedebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onNewCopy, modifier = Modifier.fillMaxWidth()) { Text("Yeni kopya olarak kaydet") }
                OutlinedButton(onClick = onOverwrite, enabled = canOverwrite, modifier = Modifier.fillMaxWidth()) {
                    Text("Asıl dosyanın üzerine yaz")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 4.dp)) { Text("Vazgeç") } },
    )
}

/** Kaydedilmemiş değişiklikle çıkarken uyarı. */
@Composable
fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kaydedilmemiş değişiklikler") },
        text = { Text("Yaptığınız değişiklikler henüz kaydedilmedi.") },
        confirmButton = { TextButton(onClick = onSave) { Text("Kaydet") } },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = onDiscard) { Text("Kaydetmeden çık") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

/** "Rapor.xlsx" → "Rapor (düzenlendi).xlsx" */
fun editedCopyName(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) + " (düzenlendi)" + name.substring(dot) else "$name (düzenlendi)"
}
