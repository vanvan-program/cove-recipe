package cn.myrecipe.app.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import cn.myrecipe.app.core.database.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageStore(private val context: Context) {
    fun file(path: String): File {
        require(Regex("images/[a-zA-Z0-9_-]+\\.jpg").matches(path)) { "图片路径无效" }
        return File(context.filesDir, path)
    }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = (maxOf(info.size.width, info.size.height) / 1600f).coerceAtLeast(1f)
                decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0) { "无法读取图片" }
            options.inJustDecodeBounds = false
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1600) options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("无法读取图片")
        }
        val path = "images/${newId()}.jpg"; val target = file(path); target.parentFile!!.mkdirs()
        try { target.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "保存图片失败" } } }
        finally { bitmap.recycle() }
        path
    }
}
