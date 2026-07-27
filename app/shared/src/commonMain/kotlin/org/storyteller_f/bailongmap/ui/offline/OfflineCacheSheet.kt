package org.storyteller_f.bailongmap.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_delete
import bailongmap.app.shared.generated.resources.ic_download
import bailongmap.app.shared.generated.resources.ic_pause
import bailongmap.app.shared.generated.resources.ic_play
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflinePack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineCacheSheet(
    offlineManager: OfflineManager,
    currentZoom: Double,
    isCreatingPack: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDownloadVisibleRegion: () -> Unit,
    onError: (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val canDownload = currentZoom >= 8.0 && !isCreatingPack

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("离线地图", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "下载当前屏幕可见区域，后续无网络时可直接打开已缓存区域。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    onClick = {
                        coroutineScope.launch {
                            runCatching { offlineManager.clearAmbientCache() }
                                .onFailure { onError("清理缓存失败") }
                        }
                    },
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

            if (offlineManager.packs.isEmpty()) {
                Text(
                    text = "暂无离线区域",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(offlineManager.packs.toList(), key = { it.hashCode() }) { pack ->
                        OfflinePackRow(
                            pack = pack,
                            onPause = { offlineManager.pause(pack) },
                            onResume = { offlineManager.resume(pack) },
                            onDelete = {
                                coroutineScope.launch {
                                    runCatching { offlineManager.delete(pack) }
                                        .onFailure { onError("删除离线区域失败") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePackRow(
    pack: OfflinePack,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = pack.downloadProgress
    val healthy = progress as? DownloadProgress.Healthy
    val fraction = healthy?.progressFraction()
    val isDownloading = healthy?.status == DownloadStatus.Downloading
    val title = pack.metadata?.decodeToString()?.ifBlank { null } ?: "未命名区域"

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
            IconButton(onClick = if (isDownloading) onPause else onResume) {
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
            }
            val countText =
                if (requiredResourceCount > 0L) "$completedResourceCount / $requiredResourceCount"
                else "$completedResourceCount 个资源"
            "$statusText · $countText · ${completedResourceBytes.formatBytes()}"
        }
        is DownloadProgress.Error -> "错误：$message"
        is DownloadProgress.TileLimitExceeded -> "超过离线瓦片上限：$limit"
        DownloadProgress.Unknown -> "准备中"
    }

private fun Long.formatBytes(): String =
    when {
        this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
        this >= 1024L -> "${this / 1024L} KB"
        else -> "$this B"
    }
