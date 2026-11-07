package org.storyteller_f.bailongmap.data.roads

import androidx.compose.runtime.Composable

@Composable
expect fun rememberRoadImportPicker(onSelected: (List<String>) -> Unit): () -> Unit
