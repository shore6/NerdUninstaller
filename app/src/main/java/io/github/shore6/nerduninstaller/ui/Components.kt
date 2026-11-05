package io.github.shore6.nerduninstaller.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shore6.nerduninstaller.data.AppItem
import io.github.shore6.nerduninstaller.data.IconCache
import io.github.shore6.nerduninstaller.data.Safety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppIcon(app: AppItem, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(IconCache.peek(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconCache.load(context.packageManager, app, sizePx) }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun Tag(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun SafetyTag(safety: Safety) {
    val (container, content) = safetyColors(safety)
    Tag(safety.label, container, content)
}

@Composable
fun safetyColors(safety: Safety): Pair<Color, Color> = when (safety) {
    Safety.RECOMMENDED -> Color(0xFF2E7D32) to Color.White
    Safety.CAUTION -> Color(0xFFF9A825) to Color.Black
    Safety.DANGER -> Color(0xFFC62828) to Color.White
    Safety.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    Safety.USER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

fun formatSize(context: Context, bytes: Long?): String =
    if (bytes == null) "—" else Formatter.formatShortFileSize(context, bytes)

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

fun formatDate(time: Long): String = if (time <= 0) "—" else dateFormat.format(Date(time))

fun formatDateTime(time: Long): String = if (time <= 0) "—" else dateTimeFormat.format(Date(time))
