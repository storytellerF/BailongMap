package org.storyteller_f.bailongmap.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_delete
import bailongmap.app.shared.generated.resources.ic_download
import bailongmap.app.shared.generated.resources.ic_pause
import bailongmap.app.shared.generated.resources.ic_play
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflineStorage
import org.maplibre.compose.offline.OfflineStorageState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineCacheSheet(
    offlineStorage: OfflineStorage,
    currentZoom: Double,
    isCreatingPack: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDownloadVisibleRegion: () -> Unit,
    onClearCache: () -> Unit,
    onPause: (OfflinePack) -> Unit,
    onResume: (OfflinePack) -> Unit,
    onDelete: (OfflinePack) -> Unit,
    dataSettings: @Composable () -> Unit = {},
    errorMessage: String? = null,
) {
    val storageState by offlineStorage.state.collectAsStateWithLifecycle()
    val packs = (storageState as? OfflineStorageState.Ready)?.packs.orEmpty()
    val canDownload = currentZoom >= 8.0 && !isCreatingPack && storageState is OfflineStorageState.Ready

    var deleteTarget by remember { mutableStateOf<OfflinePack?>(null) }
    var clearRequested by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("离线中心", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "地图、道路和公共交通数据分别管理。地图用于显示，道路用于算路，交通包用于查询班次与换乘。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            dataSettings()
            HorizontalDivider()
            Text("地图瓦片", style = MaterialTheme.typography.titleMedium)
            Text("下载当前地图可见区域；下载地图不会自动安装道路与交通数据。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDownloadVisibleRegion,
                    enabled = canDownload,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (isCreatingPack) "创建中" else "下载当前区域")
                }
                OutlinedButton(
                    onClick = { clearRequested = true },
                    enabled = storageState is OfflineStorageState.Ready,
                ) {
                    Text("清理缓存")
                }
            }

            if (currentZoom < 8.0) {
                Text(
                    text = "当前缩放层级过低，请放大地图后再下载，避免区域过大。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            if (packs.isEmpty()) {
                Text(
                    text = when (storageState) {
                        OfflineStorageState.Loading -> "正在加载离线区域"
                        is OfflineStorageState.Failed -> "离线存储初始化失败"
                        is OfflineStorageState.Ready -> "暂无离线区域"
                        else -> "离线存储暂不可用"
                    },
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(packs.toList(), key = { it.hashCode() }) { pack ->
                        OfflinePackRow(
                            pack = pack,
                            onPause = { onPause(pack) },
                            onResume = { onResume(pack) },
                            onDelete = { deleteTarget = pack },
                        )
                    }
                }
            }
        }
    }
    if (clearRequested) AlertDialog(onDismissRequest = { clearRequested = false },
        title = { Text("清理自动缓存？") }, text = { Text("只清理自动缓存；已下载的离线区域单独保留。") },
        confirmButton = { TextButton(onClick = { clearRequested = false; onClearCache() }) { Text("确认清理") } },
        dismissButton = { TextButton(onClick = { clearRequested = false }) { Text("取消") } })
    deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null },
        title = { Text("删除离线区域？") }, text = { Text("删除后，此区域需要重新下载才能离线查看。") },
        confirmButton = { TextButton(onClick = { deleteTarget = null; onDelete(target) }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }) }
}

@Composable
private fun OfflinePackRow(
    pack: OfflinePack,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress by pack.downloadProgress.collectAsStateWithLifecycle()
    val metadata by pack.metadata.collectAsStateWithLifecycle()
    val healthy = progress as? DownloadProgress.Healthy
    val fraction = healthy?.progressFraction()
    val isDownloading = healthy?.status == DownloadStatus.Downloading
    val title = metadata?.decodeToString()?.ifBlank { null } ?: "未命名区域"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = progress.summaryText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (healthy?.status != DownloadStatus.Complete) IconButton(onClick = if (isDownloading) onPause else onResume) {
                Icon(
                    painterResource(if (isDownloading) Res.drawable.ic_pause else Res.drawable.ic_play),
                    contentDescription = if (isDownloading) "暂停下载" else "继续下载",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(Res.drawable.ic_delete), contentDescription = "删除离线区域")
            }
        }

        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(2.dp))
    }
}

private fun DownloadProgress.Healthy.progressFraction(): Float? {
    if (!isRequiredResourceCountPrecise || requiredResourceCount <= 0L) return null
    return (completedResourceCount.toFloat() / requiredResourceCount.toFloat()).coerceIn(0f, 1f)
}

private fun DownloadProgress.summaryText(): String =
    when (this) {
        is DownloadProgress.Healthy -> {
            val statusText = when (status) {
                DownloadStatus.Paused -> "已暂停"
                DownloadStatus.Downloading -> "下载中"
                DownloadStatus.Complete -> "已完成"
                else -> "状态未知"
            }
            val countText =
                if (requiredResourceCount > 0L) "$completedResourceCount / $requiredResourceCount"
                else "$completedResourceCount 个资源"
            "$statusText · $countText · ${completedResourceBytes.formatBytes()}"
        }
        is DownloadProgress.Error -> "错误：$message"
        is DownloadProgress.TileLimitExceeded -> "超过离线瓦片上限：$limit"
        DownloadProgress.NotReported -> "准备中"
        else -> "状态未知"
    }

private fun Long.formatBytes(): String =
    when {
        this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
        this >= 1024L -> "${this / 1024L} KB"
        else -> "$this B"
    }
