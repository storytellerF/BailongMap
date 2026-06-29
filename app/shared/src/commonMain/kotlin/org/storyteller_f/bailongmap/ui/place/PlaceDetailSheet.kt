package org.storyteller_f.bailongmap.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import org.jetbrains.compose.resources.painterResource
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_favorite_border
import bailongmap.app.shared.generated.resources.ic_favorite_filled
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.storyteller_f.bailongmap.data.model.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    place: Place,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = place.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (place.type.isNotEmpty() || place.category.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (place.category.isNotEmpty()) {
                        AssistChip(onClick = {}, label = { Text(place.category) })
                    }
                    if (place.type.isNotEmpty() && place.type != place.category) {
                        AssistChip(onClick = {}, label = { Text(place.type) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onToggleFavorite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(if (isFavorite) Res.drawable.ic_favorite_filled else Res.drawable.ic_favorite_border),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isFavorite) "已收藏" else "收藏")
            }
        }
    }
}
