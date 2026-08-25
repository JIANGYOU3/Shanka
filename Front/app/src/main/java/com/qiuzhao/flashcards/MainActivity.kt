package com.qiuzhao.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiuzhao.flashcards.ui.AutumnFlashcardsTheme
import com.qiuzhao.flashcards.ui.FlashcardsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val appViewModel = viewModel<com.qiuzhao.flashcards.ui.AppViewModel>()
            AutumnFlashcardsTheme {
                FlashcardsApp(appViewModel)
            }
        }
    }
}
