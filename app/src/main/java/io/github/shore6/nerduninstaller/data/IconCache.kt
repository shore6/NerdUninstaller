package io.github.shore6.nerduninstaller.data

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(400)

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(pm: PackageManager, app: AppItem, sizePx: Int): ImageBitmap? {
        cache.get(app.packageName)?.let { return it }
        return try {
            val bitmap = app.appInfo.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap()
            cache.put(app.packageName, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
