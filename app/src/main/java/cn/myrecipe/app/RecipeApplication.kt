package cn.myrecipe.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import cn.myrecipe.app.core.database.RecipeDatabase
import cn.myrecipe.app.core.files.ImageStore
import cn.myrecipe.app.core.timer.TimerController
import cn.myrecipe.app.core.backup.ZipBackupRepository
import cn.myrecipe.app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

class AppContainer(context: Context) {
    val gate = Mutex()
    val db = Room.databaseBuilder(context, RecipeDatabase::class.java, "my-recipe.db").addMigrations(RecipeDatabase.MIGRATION_1_2, RecipeDatabase.MIGRATION_2_3, RecipeDatabase.MIGRATION_3_4).build()
    val repository = LocalRepository(db, gate)
    val images = ImageStore(context)
    val preferences = Preferences(context)
    val timer = TimerController(context, db, gate)
    val backup = ZipBackupRepository(context, db, images, timer, gate)
}
class RecipeApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var container: AppContainer
        private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this); scope.launch { container.timer.reconcile() } }
}
