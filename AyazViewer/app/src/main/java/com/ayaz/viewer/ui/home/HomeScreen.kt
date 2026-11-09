package com.ayaz.viewer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.components.AyazCard
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.FileTypeIcon
import com.ayaz.viewer.ui.components.RecentFileRow
import com.ayaz.viewer.ui.components.SectionHeader
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.components.pressScale
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.main.InitialLocations
import com.ayaz.viewer.ui.main.MainActions
import com.ayaz.viewer.ui.main.MainTab
import com.ayaz.viewer.ui.theme.AyazColors

@Composable
fun HomeScreen(recents: List<RecentFile>, actions: MainActions) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HomeHeader() }
        item { OpenFileHero(onClick = { actions.pick(null, null) }) }
        item {
            AyazCard(Modifier.fillMaxWidth().semantics { contentDescription = "Oluştur" }, onClick = actions.create) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.ayaz.viewer.ui.components.IconBadge(androidx.compose.material.icons.Icons.Filled.Add, MaterialTheme.colorScheme.primary)
                    SpacerW(12)
                    Column(Modifier.weight(1f)) {
                        Text("Oluştur", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Yeni Excel tablosu veya PDF belgesi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Modifier.weight(1f), FileKind.PDF, "PDF aç", "Belgeler") { actions.pick(FileKind.PDF, null) }
                QuickTile(Modifier.weight(1f), FileKind.XLSX, "Excel aç", "XLSX tablolar") { actions.pick(FileKind.XLSX, null) }
            }
        }
        item {
            AyazCard(Modifier.fillMaxWidth(), onClick = { actions.pick(null, InitialLocations.DOWNLOADS) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.ayaz.viewer.ui.components.IconBadge(AyazIcons.Download, MaterialTheme.colorScheme.secondary)
                    SpacerW(12)
                    Column(Modifier.weight(1f)) {
                        Text("İndirilenler", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "İndirilenler klasöründen dosya seç",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(
                "Son dosyalar",
                actionLabel = if (recents.size > 5) "Tümü" else null,
                onAction = { actions.goTo(MainTab.RECENTS) },
            )
        }
        if (recents.isEmpty()) {
            item {
                EmptyState(
                    icon = AyazIcons.History,
                    title = "Henüz dosya açmadınız",
                    message = "Açtığınız PDF ve Excel dosyaları burada listelenir. Başlamak için \"Dosya Aç\"a dokunun.",
                )
            }
        } else {
            items(recents.take(5), key = { it.id }) { f ->
                RecentFileRow(
                    file = f,
                    onOpen = { actions.openRecent(f) },
                    onShare = { actions.share(f) },
                    onRemove = { actions.remove(f) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item { SpacerH(8) }
    }
}

@Composable
private fun HomeHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(AyazColors.brandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        SpacerW(12)
        Column {
            Text("Ayaz Viewer", style = MaterialTheme.typography.headlineSmall)
            Text(
                "PDF ve Excel • çevrimdışı ve güvenli",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Büyük "Dosya Aç" düğmesi. */
@Composable
private fun OpenFileHero(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(26.dp))
            .background(AyazColors.brandGradient)
            .clickable(interaction, indication = androidx.compose.material3.ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Dosya Aç" }
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AyazIcons.NoteAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            SpacerW(16)
            Column(Modifier.weight(1f)) {
                Text("Dosya Aç", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                SpacerH(2)
                Text(
                    "PDF veya Excel (XLSX) dosyası seçin",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun QuickTile(modifier: Modifier, kind: FileKind, title: String, subtitle: String, onClick: () -> Unit) {
    AyazCard(modifier, onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeIcon(kind, size = 40.dp)
            SpacerW(10)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
