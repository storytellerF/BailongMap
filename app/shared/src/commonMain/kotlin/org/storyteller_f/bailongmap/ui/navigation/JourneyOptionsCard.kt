package org.storyteller_f.bailongmap.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place

@Composable
fun JourneyOptionsCard(
    destination: Place,
    plans: List<JourneyPlan>,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoading) "正在规划多模式行程…" else "选择行程方案",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "取消行程规划",
                    )
                }
            }

            if (!isLoading) {
                val fastestDuration = plans.minOfOrNull(JourneyPlan::durationSeconds)
                plans.forEachIndexed { index, plan ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(plan.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buildString {
                                        append("方案 ${index + 1}")
                                        if (plan.durationSeconds == fastestDuration) append(" · 最快")
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = plan.modeChainText(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = plan.summaryText() +
                                        if (plan.isMultiStage) " · ${plan.transferCount} 次阶段切换" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "选择",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
