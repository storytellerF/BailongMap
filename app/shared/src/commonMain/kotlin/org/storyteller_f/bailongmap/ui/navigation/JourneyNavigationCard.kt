package org.storyteller_f.bailongmap.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_close
import bailongmap.app.shared.generated.resources.ic_navigation
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.storyteller_f.bailongmap.data.model.JourneyLeg
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.model.TravelMode

@Composable
fun JourneyNavigationCard(
    destination: Place,
    plan: JourneyPlan,
    activeLegIndex: Int,
    onNextLeg: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = activeLegIndex.coerceIn(plan.legs.indices)
    val activeLeg = plan.legs[activeIndex]
    val nextLeg = plan.legs.getOrNull(activeIndex + 1)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_navigation),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = plan.summaryText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "结束导航",
                    )
                }
            }

            Text(
                text = "当前阶段：${activeLeg.titleText()}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "第 ${activeIndex + 1}/${plan.legs.size} 段 · " +
                    "${formatRouteDistance(activeLeg.distanceMeters)} · " +
                    "约 ${formatRouteDuration(activeLeg.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            nextLeg?.let {
                Text(
                    text = "下一阶段：${it.titleText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = onNextLeg,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (nextLeg == null) "完成行程" else "下一阶段")
            }
        }
    }
}

internal fun JourneyPlan.summaryText(): String =
    "${formatRouteDistance(distanceMeters)} · 约 ${formatRouteDuration(durationSeconds)}"

internal fun JourneyPlan.modeChainText(): String =
    legs.joinToString(" → ") { it.mode.displayName() }

internal fun JourneyLeg.titleText(): String =
    buildString {
        append(mode.displayName())
        routeName?.let {
            append(" ")
            append(it)
        }
        append(" · ")
        append(fromName)
        append(" → ")
        append(toName)
    }

internal fun TravelMode.displayName(): String = when (this) {
    TravelMode.WALK -> "步行"
    TravelMode.SUBWAY -> "地铁"
    TravelMode.BICYCLE -> "自行车"
    TravelMode.TRANSIT -> "公共交通"
    TravelMode.CAR -> "驾车"
}

internal fun formatRouteDistance(distanceMeters: Double): String =
    if (distanceMeters < 1_000.0) {
        "${distanceMeters.roundToInt()} 米"
    } else {
        val tenths = (distanceMeters / 100.0).roundToInt()
        val kilometers = if (tenths % 10 == 0) {
            (tenths / 10).toString()
        } else {
            "${tenths / 10}.${tenths % 10}"
        }
        "$kilometers 公里"
    }

internal fun formatRouteDuration(durationSeconds: Double): String {
    val totalMinutes = ceil(durationSeconds / 60.0).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        minutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $minutes 分钟"
    }
}
