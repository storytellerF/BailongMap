package org.storyteller_f.bailongmap.data.roads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberRoadImportPicker(onSelected: (List<String>) -> Unit): () -> Unit {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onSelected(uris.map { it.toString() })
    }
    return { picker.launch(arrayOf("*/*")) }
}
