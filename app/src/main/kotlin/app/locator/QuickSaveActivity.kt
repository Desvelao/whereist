package app.locator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.locator.ui.theme.isDark
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.locator.ui.QuickSaveScreen
import app.locator.ui.QuickSaveViewModel
import app.locator.ui.theme.LocatorTheme

/**
 * Short foreground screen opened by the widget and the app shortcut. Taking the fix while this screen is
 * visible is what keeps the app free of background-location permission.
 */
class QuickSaveActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LocatorApp).container
        setContent {
            val mode by (application as LocatorApp).container.theme.mode.collectAsState()
            val dark = mode.isDark()
            DisposableEffect(dark) {
                // The system bars follow the chosen appearance, not only the phone's.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LocatorTheme(dark) {
                val vm = viewModel<QuickSaveViewModel>(
                    factory = viewModelFactory {
                        initializer { QuickSaveViewModel(container.repository, container.location, container.quickItem) }
                    },
                )
                QuickSaveScreen(
                    vm,
                    container.photos,
                    onClose = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}
