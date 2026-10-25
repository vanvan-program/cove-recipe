package cn.myrecipe.app.data.repository

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.preferences by preferencesDataStore("recipe_preferences")
class Preferences(private val context: Context) {
    private val modeKey = stringPreferencesKey("home_mode")
    private val categoryKey = stringPreferencesKey("home_category")
    val home = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { Pair(it[modeKey] ?: "全部", it[categoryKey]) }
    suspend fun saveHome(mode: String, category: String?) { context.preferences.edit { it[modeKey] = mode; if (category == null) it.remove(categoryKey) else it[categoryKey] = category } }
}
