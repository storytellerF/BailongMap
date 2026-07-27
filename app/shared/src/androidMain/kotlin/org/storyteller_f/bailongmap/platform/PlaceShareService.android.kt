package org.storyteller_f.bailongmap.platform

import android.content.Intent
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.model.toDeepLink

actual fun createPlaceShareService(): PlaceShareService =
    AndroidPlaceShareService()

private class AndroidPlaceShareService : PlaceShareService {
    override fun share(place: Place) {
        val text = buildString {
            appendLine(place.name)
            appendLine(place.displayName)
            appendLine("坐标：${place.lat}, ${place.lon}")
            append(place.toDeepLink())
        }
        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(sendIntent, "分享地点")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidAppContext.context.startActivity(chooser)
    }
}
