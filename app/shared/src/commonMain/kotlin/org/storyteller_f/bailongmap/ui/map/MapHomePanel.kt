package org.storyteller_f.bailongmap.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import bailongmap.app.shared.generated.resources.*
import org.storyteller_f.bailongmap.data.model.Place

@Composable
internal fun MapHomePanel(
    places: List<Place>, onPlace: (Place) -> Unit, onFavorites: () -> Unit,
    onOffline: () -> Unit, modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("从这里，去任何地方。", style = MaterialTheme.typography.titleLarge)
                Text("搜索地点，或从熟悉的地方出发", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (places.isNotEmpty()) {
                    Text("最近查看", style = MaterialTheme.typography.labelMedium)
                    val place = places.first()
                    TextButton(onClick = { onPlace(place) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(painterResource(Res.drawable.ic_location_on), null)
                        Spacer(Modifier.width(8.dp))
                        Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                NavigationBarItem(selected = true, onClick = {},
                    icon = { Icon(painterResource(Res.drawable.ic_map), null) }, label = { Text("地图") })
                NavigationBarItem(selected = false, onClick = onFavorites,
                    icon = { Icon(painterResource(Res.drawable.ic_favorite_border), null) }, label = { Text("收藏") })
                NavigationBarItem(selected = false, onClick = onOffline,
                    icon = { Icon(painterResource(Res.drawable.ic_download), null) }, label = { Text("离线") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesSheet(places: List<Place>, onPlace: (Place) -> Unit, onDismiss: () -> Unit, sheetState: SheetState) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("我的收藏", style = MaterialTheme.typography.headlineSmall)
            Text("想去的地方，下次出发更快一步。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (places.isEmpty()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("还没有收藏地点\n在地点详情中点击收藏，就能从这里再次出发。", Modifier.fillMaxWidth().padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(places, key = { it.id }) { place ->
                    Surface(onClick = { onPlace(place) }, color = MaterialTheme.colorScheme.surface) {
                        ListItem(
                            leadingContent = { Icon(painterResource(Res.drawable.ic_favorite_filled), null,
                                tint = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text(place.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        ) { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Text("收藏保存地点信息；离线地图和路线计算仍需对应数据。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
