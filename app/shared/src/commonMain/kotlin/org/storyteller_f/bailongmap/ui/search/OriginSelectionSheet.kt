package org.storyteller_f.bailongmap.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OriginSelectionSheet(
    destination: String, sheetState: SheetState, onLocate: () -> Unit,
    onManual: () -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("从哪里出发？", style = MaterialTheme.typography.headlineSmall)
            Text("前往 $destination", style = MaterialTheme.typography.titleMedium)
            Text("使用当前位置需要定位权限。你也可以在地图上手动选择起点，不授予权限也能继续规划。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onLocate, modifier = Modifier.fillMaxWidth()) { Text("使用当前位置") }
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手动选择起点") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("先浏览地图") }
        }
    }
}

@Composable
internal fun ManualOriginBar(destination: String, onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择路线起点", style = MaterialTheme.typography.titleLarge)
            Text("移动地图，将中心标记放在起点；目的地：$destination", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("使用这个起点") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}
