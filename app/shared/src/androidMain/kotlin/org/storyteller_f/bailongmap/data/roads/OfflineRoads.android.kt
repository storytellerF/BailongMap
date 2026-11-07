package org.storyteller_f.bailongmap.data.roads

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import btools.router.*
import btools.mapaccess.PhysicalFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.platform.AndroidAppContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

actual fun createOfflineRoads(): OfflineRoads = RoadProvider.instance
private object RoadProvider { val instance by lazy { BRouterRoads(AndroidAppContext.context) } }

/** Embeds the upstream routing-only engine; immutable RD5 generations are atomically published. */
internal class BRouterRoads(private val context: Context) : OfflineRoads {
    private val mutex = Mutex()
    private var preparedWalkingProfile: File? = null
    private val root get() = File(context.filesDir,"roads").apply { mkdirs() }
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    override val files = mutable.asStateFlow()
    private fun active(): File? {
        val pointer=File(root,"active")
        if (!pointer.exists()) return null
        val id=pointer.readText().trim()
        require(UUID.fromString(id).toString()==id)
        return File(root,id)
    }
    override suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        mutable.value=active()?.listFiles()?.filter { it.extension=="rd5" }?.map { it.name }?.sorted().orEmpty()
    } }
    override suspend fun import(documents: List<String>): Unit = withContext(Dispatchers.IO) { mutex.withLock {
        require(documents.isNotEmpty() && documents.size<=64)
        val previous=active()
        val directory=File(root,UUID.randomUUID().toString()).apply { check(mkdir()) }
        val added=mutableListOf<File>()
        try {
            var bytes=0L
            for (document in documents) {
                val uri=Uri.parse(document)
                val name=if(uri.scheme=="file") File(requireNotNull(uri.path)).name else context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                require(Regex("[EW]\\d{1,3}_[NS]\\d{1,2}\\.rd5").matches(name)) { "请选择 BRouter 标准 .rd5 道路分片" }
                val target=File(directory,name)
                require(added.none { it.name==name }) { "重复的道路分片" }
                added+=target
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> target.outputStream().use { output ->
                    val buffer=ByteArray(65536)
                    while (true) {
                        ensureActive()
                        val count=input.read(buffer)
                        if (count<0) break
                        bytes+=count
                        require(bytes<=2L*1024*1024*1024) { "一次导入不能超过 2 GiB" }
                        output.write(buffer,0,count)
                    }
                } }
                require(target.length()>0 && PhysicalFile.checkFileIntegrity(target)==null) { "道路分片损坏或格式不兼容" }
            }
            for (name in listOf("lookups.dat","car-fast.brf","trekking.brf","shortest.brf")) {
                val target=File(directory,name);added+=target
                context.assets.open("brouter/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            ensureActive()
            val pointer=File(root,"active.tmp")
            pointer.writeText(directory.name)
            Files.move(pointer.toPath(),File(root,"active").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            mutable.value=added.filter { it.extension=="rd5" }.map { it.name }.sorted()
            android.util.Log.i("OfflineRoads","operation=road_import outcome=ready")
            previous?.listFiles()?.forEach { it.delete() }
            previous?.delete()
        } finally {
            if (active()!=directory) { added.forEach { it.delete() }; directory.delete() }
        }
    } }
    override fun planner(mode: TravelMode) = JourneyPlanner { origin,destination ->
        require(mode in setOf(TravelMode.CAR,TravelMode.BICYCLE,TravelMode.WALK))
        withContext(Dispatchers.IO) { mutex.withLock {
            val directory=requireNotNull(active()) { "未导入离线道路分片" }
            // Existing imports retain profiles from the app version that imported
            // them. Refresh walking permissions before using those generations.
            if (mode == TravelMode.WALK && preparedWalkingProfile != directory) {
                context.assets.open("brouter/shortest.brf").use { input ->
                    File(directory, "shortest.brf").outputStream().use { input.copyTo(it) }
                }
                preparedWalkingProfile = directory
            }
            val profile=when(mode) { TravelMode.CAR -> "car-fast"; TravelMode.BICYCLE -> "trekking"; else -> "shortest" }
            val routing=RoutingContext().apply { localFunction=File(directory,"$profile.brf").path; memoryclass=64 }
            fun point(p: RoutePoint, label: String)=OsmNodeNamed().apply {
                // Upstream island recovery and dynamic waypoint matching require names.
                name=label
                ilon=((p.longitude+180)*1000000).roundToInt(); ilat=((p.latitude+90)*1000000).roundToInt()
            }
            val start=point(origin,"from")
            val end=point(destination,"to")
            val engine=RoutingEngine(null,null,directory,mutableListOf(start,end),routing)
            // The upstream class extends Thread, but doRun is called synchronously on
            // a coroutine worker. Cancellation requests the engine's own termination.
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { engine.terminate() }
                    launch(Dispatchers.IO) {
                        try {
                            engine.doRun(15000)
                            check(engine.errorMessage==null) { "离线道路未覆盖起终点或无法连通" }
                            val track=requireNotNull(engine.foundTrack)
                            // Recovery may leave a partial track despite a null error message.
                            check(track.nodes.size>=2 &&
                                start.calcDistance(track.nodes.first())<=routing.waypointCatchingRange &&
                                end.calcDistance(track.nodes.last())<=routing.waypointCatchingRange) {
                                "离线道路未能连接请求的起终点"
                            }
                            val points=track.nodes.map { RoutePoint(it.iLat/1000000.0-90,it.iLon/1000000.0-180) }
                            val plan=JourneyPlan("brouter-$profile",listOf(JourneyLeg(mode,points,track.distance.toDouble(),track.totalSeconds.toDouble(),"当前位置","目的地")),notice="离线道路规划 · © OpenStreetMap contributors · BRouter")
                            continuation.resume(listOf(plan))
                        } catch (failure: Exception) { continuation.resumeWithException(failure) }
                    }
                }
            }
        } }
    }
}
