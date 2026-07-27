package org.storyteller_f.bailongmap

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.platform.AndroidAppContext

class MainActivity : ComponentActivity() {

    private var hasLocationPermission by mutableStateOf(false)
    private var openedPlace by mutableStateOf<Place?>(null)
    private var offlineTestStyleUrl by mutableStateOf<String?>(null)

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            hasLocationPermission = perms.values.any { it }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidAppContext.initialize(this)

        hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        openedPlace = intent.toPlace()
        offlineTestStyleUrl = intent.toOfflineTestStyleUrl()

        setContent {
            App(
                hasLocationPermission = hasLocationPermission,
                openedPlace = openedPlace,
                onOpenedPlaceConsumed = { openedPlace = null },
                offlineTestStyleUrl = offlineTestStyleUrl,
                onRequestLocationPermission = {
                    requestPermission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedPlace = intent.toPlace()
        intent.toOfflineTestStyleUrl()?.let { offlineTestStyleUrl = it }
    }

    private fun Intent?.toPlace(): Place? {
        val uri = this?.data ?: return null
        if (uri.scheme != "bailongmap" || uri.host != "place") return null
        val lat = uri.getQueryParameter("lat")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -90.0..90.0 }
            ?: return null
        val lon = uri.getQueryParameter("lon")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -180.0..180.0 }
            ?: return null
        val name = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "分享地点"
        val address = uri.getQueryParameter("address")?.takeIf { it.isNotBlank() } ?: "$lat, $lon"
        return Place(
            id = "deeplink:$lat,$lon:$name",
            name = name,
            displayName = address,
            lat = lat,
            lon = lon,
            type = "shared",
            category = "deeplink",
        )
    }

    private fun Intent?.toOfflineTestStyleUrl(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val uri = this?.data ?: return null
        if (uri.scheme != "bailongmap" || uri.host != "offline-test") return null
        return uri.getQueryParameter("styleUrl")?.takeIf { it.isNotBlank() }
    }
}
