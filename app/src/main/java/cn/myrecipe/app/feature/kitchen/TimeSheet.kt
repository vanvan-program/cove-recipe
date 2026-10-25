package cn.myrecipe.app.feature

import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimeSheet(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val duration = initial.coerceIn(1, 604800)
    var hours by rememberSaveable(initial) { mutableIntStateOf((duration / 3600).toInt()) }
    var minutes by rememberSaveable(initial) { mutableIntStateOf((duration / 60 % 60).toInt()) }
    var seconds by rememberSaveable(initial) { mutableIntStateOf((duration % 60).toInt()) }
    val total = hours * 3600L + minutes * 60 + seconds
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("设置计时")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeWheel("小时", hours, 168, Modifier.weight(1f)) { hours = it; if (it == 168) { minutes = 0; seconds = 0 } }
                TimeWheel("分钟", minutes, if (hours == 168) 0 else 59, Modifier.weight(1f)) { minutes = it }
                TimeWheel("秒", seconds, if (hours == 168) 0 else 59, Modifier.weight(1f)) { seconds = it }
            }
            Text(if (total == 0L) "请选择至少 1 秒" else "上下滑动选择时长", color = if (total == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onConfirm(total) }, enabled = total in 1..604800, modifier = Modifier.fillMaxWidth()) { Text("确定") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun TimeWheel(label: String, selected: Int, maximum: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val currentChange by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        AndroidView(modifier = Modifier.fillMaxWidth().height(180.dp).testTag("timer-$label").semantics {
            contentDescription = label
            stateDescription = selected.toString()
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..maximum.toFloat(), (maximum - 1).coerceAtLeast(0))
            setProgress { currentChange(it.toInt().coerceIn(0, maximum)); true }
        }, factory = { context ->
            NumberPicker(context).apply {
                minValue = 0; maxValue = maximum; value = selected
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setFormatter { "%02d".format(it) }
                setOnValueChangedListener { _, _, value -> currentChange(value) }
            }
        }, update = { picker ->
            picker.maxValue = maximum
            picker.value = selected.coerceIn(0, maximum)
            if (Build.VERSION.SDK_INT >= 29) { picker.textColor = color; picker.textSize = 24 * picker.resources.displayMetrics.scaledDensity }
        })
    }
}
