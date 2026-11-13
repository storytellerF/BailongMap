package org.storyteller_f.bailongmap.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationModeSheet(
    destination: Place,
    plan: JourneyPlan,
    sheetState: SheetState,
    onStart: (NavigationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember(plan.id) { mutableStateOf(NavigationMode.IMMERSIVE) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("这次，怎样导航？", style = MaterialTheme.typography.headlineSmall)
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(destination.name, style = MaterialTheme.typography.titleMedium)
                        Text(plan.summaryText(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                NavigationMode.entries.forEach { option ->
                    OutlinedCard(
                        onClick = { mode = option },
                        border = BorderStroke(1.dp, if (mode == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (mode == option)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                Text(option.description, style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected = mode == option, onClick = { mode = option })
                        }
                    }
                }
                Text("本次导航开始后不能更改模式。结束后，下次导航可重新选择。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onStart(mode) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("以${mode.label.removeSuffix("导航")}模式开始导航")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
