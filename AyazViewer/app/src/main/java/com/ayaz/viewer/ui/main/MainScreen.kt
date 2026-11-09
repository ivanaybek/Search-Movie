package com.ayaz.viewer.ui.main

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.FileKinds
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.share.ShareSheet
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.files.FilesScreen
import com.ayaz.viewer.ui.home.HomeScreen
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.recents.RecentsScreen
import com.ayaz.viewer.ui.settings.SettingsScreen

enum class MainTab(val label: String) {
    HOME("Ana Sayfa"),
    RECENTS("Son Açılanlar"),
    FILES("Dosyalar"),
    SETTINGS("Ayarlar"),
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.HOME -> Icons.Filled.Home
    MainTab.RECENTS -> AyazIcons.History
    MainTab.FILES -> AyazIcons.Folder
    MainTab.SETTINGS -> Icons.Filled.Settings
}

/** Ekranlar arası paylaşılan eylemler. */
class MainActions(
    val pick: (FileKind?, Uri?) -> Unit,
    val pickFolder: () -> Unit,
    val openUri: (Uri) -> Unit,
    val openRecent: (RecentFile) -> Unit,
    val share: (RecentFile) -> Unit,
    val remove: (RecentFile) -> Unit,
    val goTo: (MainTab) -> Unit,
    val create: () -> Unit,
)

@Composable
fun MainScreen(vm: MainViewModel, openViewer: (String, Boolean) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var shareTarget by remember { mutableStateOf<RecentFile?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var composeOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<RecentFile?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(OpenDocumentContract()) { uri -> if (uri != null) vm.openUri(uri) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is MainEvent.OpenViewer -> openViewer(e.id, e.edit)
                is MainEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    val actions = remember {
        MainActions(
            pick = { kind, initial ->
                val types = when (kind) {
                    FileKind.PDF -> arrayOf(FileKinds.MIME_PDF)
                    FileKind.XLSX -> arrayOf(FileKinds.MIME_XLSX)
                    else -> FileKinds.PICKER_ALL
                }
                try {
                    picker.launch(PickRequest(types, initial))
                } catch (_: ActivityNotFoundException) {
                    vm.postMessage("Bu cihazda sistem dosya seçicisi bulunamadı.")
                }
            },
            pickFolder = {
                try {
                    treePicker.launch(null)
                } catch (_: ActivityNotFoundException) {
                    vm.postMessage("Bu cihazda klasör seçici bulunamadı.")
                }
            },
            openUri = vm::openUri,
            openRecent = vm::openRecent,
            share = { shareTarget = it },
            remove = { f -> if (f.created) confirmRemove = f else vm.remove(f) },
            goTo = { tab = it },
            create = { createOpen = true },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 }) togetherWith fadeOut(tween(120))
                },
                label = "tab",
            ) { current ->
                when (current) {
                    MainTab.HOME -> HomeScreen(recents = recents, actions = actions)
                    MainTab.RECENTS -> RecentsScreen(recents = recents, actions = actions, onClearAll = vm::clearRecents)
                    MainTab.FILES -> FilesScreen(vm = vm, actions = actions)
                    MainTab.SETTINGS -> SettingsScreen(vm = vm)
                }
            }
            AnimatedVisibility(visible = busy, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        SpacerH(12)
                        Text("Dosya hazırlanıyor…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    shareTarget?.let { f -> ShareSheet(file = f, onDismiss = { shareTarget = null }) }
    if (createOpen) {
        CreateSheet(
            onDismiss = { createOpen = false },
            onSpreadsheet = { createOpen = false; vm.createSpreadsheet() },
            onTextPdf = { createOpen = false; composeOpen = true },
            onBlankPdf = { pages -> createOpen = false; vm.createBlankPdf(pages) },
        )
    }
    if (composeOpen) {
        TextPdfComposer(
            onDismiss = { composeOpen = false },
            onCreate = { title, body, size -> composeOpen = false; vm.createTextPdf(title, body, size) },
        )
    }
    confirmRemove?.let { f ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Belge silinsin mi?") },
            text = {
                Text(
                    "\"${f.name}\" Ayaz Viewer içinde oluşturuldu ve yalnızca uygulamada kayıtlı. Listeden kaldırırsanız " +
                        "kalıcı olarak silinir. Saklamak için önce açıp ⋮ menüsünden \"Telefona kaydet\"i seçin.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = null; vm.remove(f) }) { Text("Sil") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = null }) { Text("Vazgeç") }
            },
        )
    }
}
