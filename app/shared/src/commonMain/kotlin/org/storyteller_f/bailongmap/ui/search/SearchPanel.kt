package org.storyteller_f.bailongmap.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bailongmap.app.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.ui.map.PlaceSearch

@Composable
fun SearchBarUi(
    query: String, onQueryChange: (String) -> Unit, results: List<Place>,
    isSearching: Boolean, isExpanded: Boolean, onResultClick: (Place) -> Unit,
    modifier: Modifier = Modifier, searchState: PlaceSearch = PlaceSearch.Matches(results),
    onRetry: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.large, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            OutlinedTextField(
                value = query, onValueChange = onQueryChange, placeholder = { Text("想去哪里？") },
                leadingIcon = {
                    if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(Res.drawable.ic_search), null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(Res.drawable.ic_close), "清除")
                    }
                }, singleLine = true, shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isExpanded) Surface(shape = MaterialTheme.shapes.large, shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface) {
            when {
                searchState is PlaceSearch.Failed -> Column(Modifier.padding(20.dp)) {
                    Text("暂时无法搜索", style = MaterialTheme.typography.titleMedium)
                    Text(searchState.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text("重试搜索") }
                }
                results.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { place ->
                        ListItem(
                            modifier = Modifier.clickable { onResultClick(place) },
                            leadingContent = { Icon(painterResource(Res.drawable.ic_location_on), null,
                                tint = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text(place.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        ) { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                else -> Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        isSearching -> "正在搜索地点…"
                        query.trim().length < 2 -> "输入至少两个字开始搜索"
                        else -> "没有找到相关地点"
                    }, style = MaterialTheme.typography.titleSmall)
                    Text(if (isSearching) "地点搜索需要网络连接" else "试试更短的名称，或在地图上长按选择地点",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
