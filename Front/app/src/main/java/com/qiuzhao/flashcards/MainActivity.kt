package com.qiuzhao.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiuzhao.flashcards.ui.AppViewModel
import com.qiuzhao.flashcards.ui.AutumnFlashcardsTheme
import com.qiuzhao.flashcards.ui.FlashcardsApp
import com.qiuzhao.flashcards.ui.LoginScreen
import com.qiuzhao.flashcards.ui.RegisterScreen
import com.qiuzhao.flashcards.ui.auth.AuthLoadingScreen
import com.qiuzhao.flashcards.ui.auth.AuthState
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            // AppViewModel 注入缝走显式工厂（AndroidViewModelFactory 只认单参数 (Application) 构造）。
            ShankaRoot(viewModel(factory = AppViewModel.Factory))
        }
    }
}

/**
 * 启动路由根组合：会话校验中 → 占位屏；未登录 → 默认登录/注册页（登录门，登录后才能用）；
 * 已登录 → 主界面导航。设置里退出登录后回到未登录状态，同样回到默认登录页。
 * 从 MainActivity 抽出，instrumented 测试可用注入的 AppViewModel 直接渲染同一启动路由。
 */
@Composable
fun ShankaRoot(appViewModel: AppViewModel) {
    AutumnFlashcardsTheme {
        val authState by appViewModel.authState.collectAsState()
        when (authState) {
            is AuthState.CheckingSession -> AuthLoadingScreen()
            is AuthState.LoggedOut -> AuthEntry(appViewModel)
            is AuthState.LoggedIn -> {
                val authError = (authState as AuthState.LoggedIn).error
                val actionError by appViewModel.uiMessage.collectAsState()
                val message = actionError ?: authError
                ObservationLifecycleEffect(appViewModel)
                LaunchedEffect(message) {
                    if (message == null) return@LaunchedEffect
                    // Release error feedback is transient: failures never look like a completed action.
                    delay(3_000)
                    if (actionError != null) appViewModel.clearUiMessage() else appViewModel.clearAuthError()
                }
                Box(Modifier.fillMaxSize()) {
                    FlashcardsApp(appViewModel)
                    if (message != null) {
                        Snackbar(Modifier.align(Alignment.BottomCenter)) { Text(message) }
                    }
                }
            }
        }
    }
}

/** The signed-out state uses the default login surface (no 「直接进入」 button) as the gate. */
@Composable
private fun AuthEntry(appViewModel: AppViewModel) {
    val navigationState = rememberAppNavigationState()
    val navigator = remember { AppNavigator(navigationState) }
    when (navigationState.currentRoute) {
        AppRoute.Register -> RegisterScreen(appViewModel, navigator)
        else -> LoginScreen(appViewModel, navigator, showBack = false)
    }
}

/**
 * The observation engine rides the Activity lifecycle (V25-D-34): every return to RESUMED
 * reconciles all in-flight resources (parsing projects, non-terminal tasks) once and re-arms
 * its pollers; going to the background pauses them. The periodic ProcessingSyncWorker is the
 * background twin of this effect.
 */
@Composable
private fun ObservationLifecycleEffect(appViewModel: AppViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        appViewModel.observationEngine.attach(lifecycleOwner.lifecycle)
    }
}
