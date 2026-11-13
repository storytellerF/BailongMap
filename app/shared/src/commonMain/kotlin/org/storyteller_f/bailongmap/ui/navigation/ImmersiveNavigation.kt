package org.storyteller_f.bailongmap.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import bailongmap.app.shared.generated.resources.*
import org.storyteller_f.bailongmap.data.model.Place

/** Both cards describe the same locked session; details never change its camera mode. */
@Composable
internal fun BoxScope.ImmersiveNavigation(
    destination: Place, session: NavigationSession.Active, progress: RouteProgress?,
    locationAvailable: Boolean, following: Boolean, northUp: Boolean,
    onRecenter: () -> Unit, onNorthUp: () -> Unit, onNextLeg: () -> Unit, onEnd: () -> Unit,
    compact: Boolean = false,
) {
    var details by remember(session.plan.id) { mutableStateOf(false) }
    val leg = session.plan.legs[session.legIndex]
    val next = session.plan.legs.getOrNull(session.legIndex + 1)
    Surface(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
        Column(Modifier.padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(destination.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("沉浸导航 · 模式已锁定", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEnd) { Text("结束导航") }
            }
            Text("当前阶段：${leg.titleText()}", style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (!compact) LinearProgressIndicator(progress = { (session.legIndex + 1f) / session.plan.legs.size }, modifier = Modifier.fillMaxWidth())
            Text("第 ${session.legIndex + 1}/${session.plan.legs.size} 段", style = MaterialTheme.typography.labelSmall)
        }
    }
    Column(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(onClick = onNorthUp, containerColor = MaterialTheme.colorScheme.surface) {
            Text(if (northUp) "N ↑" else "路线 ↑", style = MaterialTheme.typography.labelSmall)
        }
        SmallFloatingActionButton(onClick = onRecenter, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(painterResource(Res.drawable.ic_location_on), "回到跟随")
        }
        if (!following) Surface(shape = MaterialTheme.shapes.medium) {
            TextButton(onClick = onRecenter) { Text("回到跟随") }
        }
    }
    Surface(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
        Column {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (progress != null) "剩余 ${formatRouteDistance(progress.remainingMeters)}" else "前往 ${leg.toName}",
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
                    if (!compact) Text("沿${leg.mode.displayName()}路线前往 ${leg.toName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val warning = when {
                    !locationAvailable -> "定位暂不可用或精度不足；保留路线视角"
                    progress == null -> "当前路段没有可跟随的有效几何"
                    progress.distanceFromRouteMeters > 80 -> "当前位置偏离规划路线，保留已确认的路线视角"
                    else -> null
                }
                warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (!compact) next?.let { Text("下一阶段：${it.titleText()}", style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("约 ${formatRouteDuration(session.plan.legs.drop(session.legIndex).sumOf { it.durationSeconds })}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { details = true }) { Text("行程信息") }
                    if (compact) TextButton(onClick = onNextLeg) { Text(if (next == null) "完成" else "下一段") }
                }
                if (!compact) TextButton(onClick = onNextLeg, modifier = Modifier.fillMaxWidth()) {
                    Text(if (next == null) "完成行程" else "下一阶段")
                }
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text("本次行程") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(session.plan.modeChainText())
            Text(session.plan.summaryText())
            session.plan.notice?.let { Text(it) }
            Text("当前为沉浸导航，本次模式已锁定。关闭后继续导航。")
        } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("继续导航") } },
    )
}
