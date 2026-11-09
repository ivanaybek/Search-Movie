package com.ayaz.viewer.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.ui.theme.AyazTheme

/**
 * Belge görüntüleyici. Uygulama içinden (son açılanlar / seçici) veya diğer uygulamalardan
 * "Birlikte aç" (ACTION_VIEW) ve "Paylaş" (ACTION_SEND) ile açılır.
 */
class ViewerActivity : ComponentActivity() {
    private val vm: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.handle(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            AyazTheme(settings.themeMode) {
                ViewerScreen(vm = vm, onClose = { finish() })
            }
        }
    }

    companion object {
        fun intentFor(context: Context, recentId: String, edit: Boolean = false): Intent =
            Intent(context, ViewerActivity::class.java)
                .putExtra(ViewerViewModel.EXTRA_RECENT_ID, recentId)
                .putExtra(ViewerViewModel.EXTRA_START_EDIT, edit)
    }
}
