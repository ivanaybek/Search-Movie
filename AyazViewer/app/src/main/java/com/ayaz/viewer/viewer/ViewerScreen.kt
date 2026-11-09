package com.ayaz.viewer.viewer

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.FileKinds
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.share.ShareSheet
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.viewer.pdf.PdfViewerScreen
import com.ayaz.viewer.viewer.xlsx.XlsxViewerScreen

@Composable
fun ViewerScreen(vm: ViewerViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var shareFile by remember { mutableStateOf<RecentFile?>(null) }
    val context = LocalContext.current

    // ---- kaydetme akışı (her seferinde sorulur)
    var choiceFor by remember { mutableStateOf<RecentFile?>(null) }
    val current = (state as? ViewerState.Pdf)?.file ?: (state as? ViewerState.Xlsx)?.file
    val mime = if (current?.kind == FileKind.PDF) FileKinds.MIME_PDF else FileKinds.MIME_XLSX
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val req = vm.pendingSave
        vm.pendingSave = null
        if (uri != null && req != null) vm.saveAsNew(uri, req, toast)
    }
    val requestSave: (SaveRequest) -> Unit = { req ->
        vm.pendingSave = req
        choiceFor = current
    }
    // "Telefona kaydet": değişiklik olmasa da belgenin bir kopyası kullanıcının seçtiği yere kaydedilir.
    val saveCopy: (SaveRequest) -> Unit = { req ->
        vm.pendingSave = req
        try {
            createLauncher.launch(current?.name ?: "belge")
        } catch (_: ActivityNotFoundException) {
            vm.pendingSave = null
            toast("Bu cihazda kaydetme konumu seçici bulunamadı.")
        }
    }
    choiceFor?.let { f ->
        SaveChoiceDialog(
            fileName = f.name,
            canOverwrite = remember(f.uri) { vm.canOverwrite(f) },
            onOverwrite = {
                choiceFor = null
                val req = vm.pendingSave
                vm.pendingSave = null
                if (req != null) vm.saveOverwrite(f, req, toast)
            },
            onNewCopy = {
                choiceFor = null
                try {
                    createLauncher.launch(editedCopyName(f.name))
                } catch (_: ActivityNotFoundException) {
                    vm.pendingSave = null
                    toast("Bu cihazda kaydetme konumu seçici bulunamadı.")
                }
            },
            onDismiss = { choiceFor = null; vm.pendingSave = null },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            is ViewerState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    SpacerH(14)
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ViewerState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Info,
                    title = "Dosya açılamadı",
                    message = s.message,
                    action = { Button(onClick = onClose) { Text("Kapat") } },
                )
            }
            is ViewerState.Pdf -> PdfViewerScreen(
                file = s.file,
                controller = s.controller,
                initialPage = remember(s.file.id) { vm.initialPage(s.file) },
                onBack = onClose,
                onShare = { shareFile = s.file },
                onPageChanged = { vm.savePdfPage(s.file, it) },
                edit = remember(s.file.id) { vm.pdfEditActions(s.file, requestSave) },
                onSaveCopy = { saveCopy(vm.pdfCopyRequest(s.file)) },
                startInEdit = vm.startInEdit,
            )
            is ViewerState.Xlsx -> XlsxViewerScreen(
                file = s.file,
                controller = s.controller,
                initialSheet = remember(s.file.id) { s.file.lastSheet },
                lightSheets = settings.lightSheets,
                onToggleLightSheets = vm::toggleLightSheets,
                onBack = onClose,
                onShare = { shareFile = s.file },
                onSheetChanged = { vm.saveSheet(s.file, it) },
                onSaveRequest = requestSave,
                onSaveCopy = { saveCopy(SaveRequest(produce = { s.controller.currentFile }, onOverwritten = {})) },
                startInEdit = vm.startInEdit,
            )
        }
    }
    shareFile?.let { ShareSheet(file = it, onDismiss = { shareFile = null }) }
}
