package org.storyteller_f.bailongmap.ui.transit

import org.storyteller_f.bailongmap.data.transit.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun TransitSettings(state: TransitUiState, onChoose: () -> Unit, onInstall: () -> Unit, onDiscard: () -> Unit, onRemove: () -> Unit) {
    var removeRequested by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("离线公共交通", style = MaterialTheme.typography.titleSmall)
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        val installed = state.installed
        Text(if (installed == null) "尚未导入公交、地铁数据" else "${installed.name} · ${installed.stops} 站 · ${installed.routes} 条线路\n有效期：${installed.start}—${installed.end}\n来源：${installed.source}")
        installed?.let { info ->
            val status = info.serviceDateStatus(currentServiceDate(info.timeZone))
            Text("${status.label} · ${info.timeZone}", style = MaterialTheme.typography.labelMedium,
                color = if (status == ServiceDateStatus.EXPIRED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("日期在范围内不代表当天必有班次，以实际规划结果为准。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("导入 .bltransit 文件后，在本地按服务日期与时刻表规划公交地铁，不含实时到站信息。", style = MaterialTheme.typography.bodySmall)
        when (val importing = state.importing) {
            TransitImportState.Working -> Text("正在处理数据…")
            TransitImportState.Idle -> {
                OutlinedButton(onClick = onChoose) { Text("导入公共交通包") }
                if (installed != null) TextButton(onClick = { removeRequested = true }) { Text("移除公共交通包") }
            }
            is TransitImportState.Review -> {
                Text("待导入：${importing.info.name}\n${importing.info.stops} 站 · ${importing.info.routes} 条线路\n${importing.info.start}—${importing.info.end}\n来源：${importing.info.source}")
                Text("确认后替换当前公共交通数据。线路按包内时刻表规划；接驳需要覆盖行程的道路数据。导入失败时保留原数据。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onInstall) { Text("确认导入") }
                TextButton(onClick = onDiscard) { Text("取消导入") }
            }
        }
    }
    if (removeRequested) AlertDialog(onDismissRequest = { removeRequested = false },
        title = { Text("移除公共交通包？") }, text = { Text("移除后将无法使用这份离线时刻表规划行程。") },
        confirmButton = { TextButton(onClick = { removeRequested = false; onRemove() }) { Text("确认移除") } },
        dismissButton = { TextButton(onClick = { removeRequested = false }) { Text("取消") } })
}
