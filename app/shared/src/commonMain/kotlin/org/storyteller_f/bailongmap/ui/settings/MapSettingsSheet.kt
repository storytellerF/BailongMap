package org.storyteller_f.bailongmap.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.storyteller_f.bailongmap.data.settings.MapAppearance
import org.storyteller_f.bailongmap.ui.map.MAP_STYLES
import org.storyteller_f.bailongmap.ui.map.MapUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSettingsSheet(
    uiState: MapUiState,
    onClearCache: () -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onStyleChange: (Int) -> Unit,
    onShowOfflineRegionsChange: (Boolean) -> Unit,
    onShowSearchMarkersChange: (Boolean) -> Unit,
    onShowUserLocationChange: (Boolean) -> Unit,
    transitSettings: @Composable () -> Unit = {},
    onAppearanceChange: (MapAppearance) -> Unit = {},
) {
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
            Text("设置", style = MaterialTheme.typography.titleLarge)
            uiState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            Text("外观与地图", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("界面主题")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapAppearance.entries.forEach { appearance ->
                    androidx.compose.material3.FilterChip(
                        selected = uiState.appearance == appearance,
                        onClick = { onAppearanceChange(appearance) },
                        label = { Text(appearance.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            SectionTitle("默认地图样式")
            MAP_STYLES.forEachIndexed { index, (name, _) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStyleChange(index) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = uiState.styleIndex == index,
                        onClick = { onStyleChange(index) },
                    )
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()

            SectionTitle("图层")
            SwitchRow(
                title = "离线区域覆盖层",
                checked = uiState.showOfflineRegions,
                onCheckedChange = onShowOfflineRegionsChange,
            )
            SwitchRow(
                title = "搜索结果标记",
                checked = uiState.showSearchMarkers,
                onCheckedChange = onShowSearchMarkersChange,
            )
            SwitchRow(
                title = "当前位置",
                checked = uiState.showUserLocation,
                onCheckedChange = onShowUserLocationChange,
            )

            HorizontalDivider()

            SectionTitle("缓存")
            OutlinedButton(
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清理自动缓存")
            }

            HorizontalDivider()

            transitSettings()
            HorizontalDivider()
            SectionTitle("数据源")
            Text(
                text = "地图数据来自 OpenStreetMap，经 OpenFreeMap 样式与瓦片服务渲染；地点搜索使用 Nominatim。请避免短时间大量下载或自动批量请求。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
