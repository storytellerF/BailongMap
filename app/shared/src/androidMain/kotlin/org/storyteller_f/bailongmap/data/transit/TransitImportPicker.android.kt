package org.storyteller_f.bailongmap.data.transit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberTransitImportPicker(onSelected: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSelected(uri.toString())
    }
    return { launcher.launch(arrayOf("*/*")) }
}
