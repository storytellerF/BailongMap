package org.storyteller_f.bailongmap.ui.transit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.storyteller_f.bailongmap.data.roads.OfflineRoads

sealed interface RoadImportStatus {
    data object Idle : RoadImportStatus
    data class Review(val documents: List<String>) : RoadImportStatus
    data object Working : RoadImportStatus
    data class Failed(val message: String) : RoadImportStatus
}
data class RoadUiState(val files: List<String> = emptyList(),val status: RoadImportStatus = RoadImportStatus.Working)
class RoadHost(private val roads: OfflineRoads,coordination: CoroutineDispatcher) {
    private val scope=CoroutineScope(SupervisorJob()+coordination)
    private val mutable=MutableStateFlow(RoadUiState())
    val state=mutable.asStateFlow()
    init {
        scope.launch { roads.files.collect { mutable.value=mutable.value.copy(files=it) } }
        scope.launch { perform { roads.load() } }
    }
    fun review(documents: List<String>) = scope.launch {
        if (documents.isNotEmpty() && mutable.value.status != RoadImportStatus.Working) {
            mutable.value = mutable.value.copy(status = RoadImportStatus.Review(documents.toList()))
        }
    }
    fun install() = scope.launch {
        val review = mutable.value.status as? RoadImportStatus.Review ?: return@launch
        perform { roads.import(review.documents) }
    }
    fun discard() = scope.launch {
        if (mutable.value.status is RoadImportStatus.Review) mutable.value = mutable.value.copy(status = RoadImportStatus.Idle)
    }
    private suspend fun perform(action: suspend () -> Unit) {
        mutable.value=mutable.value.copy(status=RoadImportStatus.Working)
        try { action();mutable.value=mutable.value.copy(status=RoadImportStatus.Idle) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value=mutable.value.copy(status=RoadImportStatus.Failed("道路数据操作失败，请选择有效的 BRouter .rd5 分片，并检查存储空间")) }
    }
    fun close() { scope.cancel() }
}
@Composable
fun RoadSettings(state: RoadUiState,onChoose: () -> Unit, onInstall: () -> Unit = {}, onDiscard: () -> Unit = {}) {
    val uri=LocalUriHandler.current
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text("离线道路",style=MaterialTheme.typography.titleSmall)
        Text(if(state.files.isEmpty()) "尚未导入道路数据" else "已安装 ${state.files.size} 个道路分片")
        Text("支持驾车、骑行、步行。一次选齐所需地区的 BRouter .rd5 文件，导入后替换当前道路数据。地图瓦片不包含可算路的道路。",style=MaterialTheme.typography.bodySmall)
        if(state.status==RoadImportStatus.Working) Text("正在处理道路数据…")
        else if (state.status is RoadImportStatus.Review) {
            Text("待导入 ${state.status.documents.size} 个道路分片", style = MaterialTheme.typography.titleMedium)
            Text("确认后替换当前道路区域。格式和完整性将在导入时检查；失败保留已有数据。", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onInstall) { Text("确认导入道路") }
            TextButton(onClick = onDiscard) { Text("取消导入道路") }
        } else OutlinedButton(onClick=onChoose) { Text("导入离线道路") }
        (state.status as? RoadImportStatus.Failed)?.let { Text(it.message) }
        TextButton(onClick={uri.openUri("https://brouter.de/brouter/segments4/")}) { Text("获取 BRouter 道路分片") }
        TextButton(onClick={uri.openUri("https://routing.openstreetmap.de/about.html")}) { Text("在线步行与骑行服务说明") }
    }
}
