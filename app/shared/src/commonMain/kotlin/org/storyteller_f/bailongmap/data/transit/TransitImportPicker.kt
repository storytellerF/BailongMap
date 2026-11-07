package org.storyteller_f.bailongmap.data.transit

import androidx.compose.runtime.Composable

@Composable
expect fun rememberTransitImportPicker(onSelected: (String) -> Unit): () -> Unit
