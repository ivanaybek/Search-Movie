package com.ayaz.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayaz.viewer.ui.main.MainScreen
import com.ayaz.viewer.ui.main.MainViewModel
import com.ayaz.viewer.ui.theme.AyazTheme
import com.ayaz.viewer.viewer.ViewerActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            AyazTheme(settings.themeMode) {
                MainScreen(vm = vm, openViewer = { id, edit -> startActivity(ViewerActivity.intentFor(this, id, edit)) })
            }
        }
    }
}
