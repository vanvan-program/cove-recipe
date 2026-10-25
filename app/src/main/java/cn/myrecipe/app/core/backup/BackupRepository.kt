package cn.myrecipe.app.core.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import cn.myrecipe.app.core.database.*
import cn.myrecipe.app.core.files.ImageStore
import cn.myrecipe.app.core.timer.TimerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.*

interface BackupRepository { suspend fun export(uri: Uri); suspend fun restore(uri: Uri): Int }

class ZipBackupRepository(private val context: Context, private val db: RecipeDatabase, private val images: ImageStore, private val timer: TimerController, private val gate: Mutex) : BackupRepository {
    override suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        gate.withLock {
            val dao = db.dao()
            val data = db.withTransaction { BackupData(dao.recipes(), dao.categories(), dao.tags(), dao.shopping()) }
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入备份文件")
            ZipOutputStream(output.buffered()).use { zip ->
                BackupCodec.encode(data).forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
                data.recipes.mapNotNull { it.recipe.coverPath }.distinct().forEach { path ->
                    val file = images.file(path); require(file.isFile) { "封面图片缺失，请重新选择后备份" }
                    zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
    }
    override suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
        val created = mutableListOf<File>()
        var committed = false
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法打开备份文件")
            val seen = mutableSetOf<String>(); val json = mutableMapOf<String, String>(); var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(seen.add(name) && seen.size <= 50005) { "备份文件重复或过多" }
                    require(!entry.isDirectory && (name in BackupCodec.names || Regex("images/[a-zA-Z0-9_-]+\\.jpg").matches(name))) { "备份含非法路径或未知文件" }
                    val target = File(stage, name); target.parentFile!!.mkdirs()
                    var size = 0L
                    target.outputStream().use { out ->
                        val buffer = ByteArray(8192); var count = zip.read(buffer)
                        while (count != -1) {
                            size += count; total += count
                            require(size <= (if (name in BackupCodec.names) 16L else 32L) * 1024 * 1024 && total <= 256L * 1024 * 1024) { "备份超过大小限制（总计 256MB）" }
                            out.write(buffer, 0, count); count = zip.read(buffer)
                        }
                    }
                    if (name in BackupCodec.names) json[name] = target.readText(Charsets.UTF_8)
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            val data = BackupCodec.decode(json)
            val paths = data.recipes.mapNotNull { it.recipe.coverPath }.distinct()
            require(seen.filter { it.startsWith("images/") }.toSet() == paths.toSet()) { "备份图片缺失或存在未关联图片" }
            val remap = paths.associateWith { path ->
                val source = File(stage, path); require(source.isFile) { "备份图片缺失" }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(source.path, opts)
                require(opts.outWidth in 1..10000 && opts.outHeight in 1..10000 && opts.outMimeType == "image/jpeg") { "备份图片无效" }
                "images/${newId()}.jpg"
            }
            gate.withLock {
                remap.forEach { (old, fresh) -> val destination = images.file(fresh); destination.parentFile!!.mkdirs(); created.add(destination); File(stage, old).copyTo(destination) }
                val dao = db.dao()
                db.withTransaction {
                    dao.clearTimer(); dao.clearRecipes(); dao.clearCategories(); dao.clearTags(); dao.clearShopping()
                    // Insert parents before children to satisfy the category foreign key.
                    data.categories.sortedBy { categoryPath(it.id, data.categories).split(" / ").size }.forEach { dao.putCategories(listOf(it)) }
                    dao.putTags(data.tags)
                    data.recipes.forEach { full ->
                        dao.putRecipe(full.recipe.copy(coverPath = full.recipe.coverPath?.let { remap.getValue(it) }))
                        dao.putIngredients(full.ingredients); dao.putSteps(full.steps); dao.putLinks(full.tags.map { RecipeTag(full.recipe.id, it.id) })
                    }
                    data.shopping.forEach { dao.putShopping(it) }
                }
                committed = true
                timer.cancelPlatformAlarm()
            }
            data.recipes.size
        } catch (e: ZipException) {
            throw IllegalArgumentException("备份压缩文件损坏或含非法路径", e)
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("备份数据格式不完整或已损坏", e)
        } finally {
            if (!committed) created.forEach { it.delete() }
            stage.deleteRecursively()
        }
    }
}
