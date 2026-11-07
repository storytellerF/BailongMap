package org.storyteller_f.bailongmap.ui.transit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun TransitSettings(state: TransitUiState, onChoose: () -> Unit, onInstall: () -> Unit, onDiscard: () -> Unit, onRemove: () -> Unit) {
    Column {
        Text("离线公共交通", style = MaterialTheme.typography.titleSmall)
        val installed = state.installed
        Text(if (installed == null) "尚未导入公交、地铁数据" else "${installed.name} · ${installed.stops} 站 · ${installed.routes} 条线路\n有效期：${installed.start}—${installed.end}\n来源：${installed.source}")
        Text("使用数据整理工具生成 .bltransit 文件后导入。路线在手机本地计算，按离线时刻表查询，不含实时到站信息。", style = MaterialTheme.typography.bodySmall)
        when (val importing = state.importing) {
            TransitImportState.Working -> Text("正在处理数据…")
            TransitImportState.Idle -> {
                OutlinedButton(onClick = onChoose) { Text("导入公共交通包") }
                if (installed != null) TextButton(onClick = onRemove) { Text("移除公共交通包") }
            }
            is TransitImportState.Review -> {
                Text("待导入：${importing.info.name}\n${importing.info.stops} 站 · ${importing.info.routes} 条线路\n${importing.info.start}—${importing.info.end}\n来源：${importing.info.source}")
                Text("确认后替换当前公共交通数据。站点间连线及步行接驳为直线示意。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onInstall) { Text("确认导入") }
                TextButton(onClick = onDiscard) { Text("取消导入") }
            }
        }
    }
}
