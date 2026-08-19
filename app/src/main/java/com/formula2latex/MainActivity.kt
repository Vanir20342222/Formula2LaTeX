package com.formula2latex

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.formula2latex.data.image.ImagePipeline
import com.formula2latex.data.provider.ProviderRegistry
import com.formula2latex.data.security.AndroidKeystoreSecretCipher
import com.formula2latex.data.settings.SettingsRepository
import com.formula2latex.ui.main.MainScreen
import com.formula2latex.ui.main.MainViewModel
import com.formula2latex.ui.theme.FormulaTheme
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val imagePipeline = ImagePipeline(applicationContext)
        val settings = SettingsRepository(applicationContext, AndroidKeystoreSecretCipher())
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
        val providers = ProviderRegistry(client)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(settings, providers, imagePipeline) as T
        }
        setContent {
            val main: MainViewModel = viewModel(factory = factory)
            val state by main.state.collectAsStateWithLifecycle()
            FormulaTheme(state.settings.theme) {
                MainScreen(main, imagePipeline)
            }
        }
    }
}
