package org.storyteller_f.bailongmap.data.roads

import btools.expressions.BExpressionContext
import btools.expressions.BExpressionContextNode
import btools.expressions.BExpressionContextWay
import btools.expressions.BExpressionMetaData
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalkingProfileTest {
    private fun contexts(): Pair<BExpressionContextWay, BExpressionContextNode> {
        val context = RuntimeEnvironment.getApplication()
        fun asset(name: String) = File(context.cacheDir, name).also { target ->
            context.assets.open("brouter/$name").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        val meta = BExpressionMetaData()
        val way = BExpressionContextWay(meta)
        val node = BExpressionContextNode(meta)
        node.setForeignContext(way)
        meta.readMetaData(asset("lookups.dat"))
        val profile = asset("shortest.brf")
        way.parseFile(profile, "global")
        node.parseFile(profile, "global")
        return way to node
    }

    // BRouter exposes a package-private Java return type; only the mutation is used.
    private fun BExpressionContext.evaluateTags(vararg tags: Pair<String, String>) {
        val data = createNewLookupData()
        tags.forEach { (key, value) ->
            addLookupValue(key, value, data)
        }
        evaluate(false, encode(data))
    }

    @Test fun bicyclePermissionCannotOverrideForbiddenWalkingOnWays() {
        val (way, _) = contexts()
        for (bicycle in listOf("yes", "designated", "dismount")) {
            way.evaluateTags("highway" to "cycleway", "bicycle" to bicycle, "foot" to "no")
            assertTrue(way.costfactor >= 10000, "foot=no, bicycle=$bicycle must be excluded")
        }
    }

    @Test fun bicyclePermissionCannotOverrideForbiddenWalkingAtNodes() {
        val (_, node) = contexts()
        for (bicycle in listOf("yes", "designated", "dismount")) {
            node.evaluateTags("bicycle" to bicycle, "foot" to "no")
            assertTrue(node.initialcost >= 1000000, "foot=no, bicycle=$bicycle must be excluded")
        }
    }

    @Test fun explicitPedestrianPermissionAllowsOtherwiseRestrictedWays() {
        val (way, _) = contexts()
        way.evaluateTags("highway" to "footway", "access" to "no", "bicycle" to "no", "foot" to "yes")
        assertTrue(way.costfactor < 10000)
    }

    @Test fun explicitPedestrianPermissionAllowsOtherwiseRestrictedNodes() {
        val (_, node) = contexts()
        node.evaluateTags("access" to "no", "bicycle" to "no", "foot" to "yes")
        assertTrue(node.initialcost < 1000000)
    }
}
