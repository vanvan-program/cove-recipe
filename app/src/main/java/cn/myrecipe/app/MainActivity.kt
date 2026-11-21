package cn.myrecipe.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.myrecipe.app.core.designsystem.RecipeTheme
import cn.myrecipe.app.feature.AppViewModel
import cn.myrecipe.app.feature.RecipeApp

class MainActivity : ComponentActivity() {
    private var timerTarget by mutableStateOf<Pair<String, String>?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        readTarget(intent)
        val container = (application as RecipeApplication).container
        setContent {
            RecipeTheme {
                val vm: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
                })
                RecipeApp(vm, timerTarget) { timerTarget = null }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readTarget(intent) }
    private fun readTarget(intent: Intent) { intent.getStringExtra("timerRecipe")?.let { timerTarget = it to intent.getStringExtra("timerStep").orEmpty() } }
}
