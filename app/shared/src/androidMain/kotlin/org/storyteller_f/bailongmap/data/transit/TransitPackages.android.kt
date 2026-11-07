package org.storyteller_f.bailongmap.data.transit

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.platform.AndroidAppContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

actual fun createTransitPackages(): TransitPackages = TransitPackageProvider.instance
private object TransitPackageProvider {
    val instance by lazy { AndroidTransitPackages(AndroidAppContext.context) }
}

internal class AndroidTransitPackages(private val context: android.content.Context, private val clock: Clock = Clock.systemUTC()) : TransitPackages {
    private val mutex = Mutex()
    private val current = MutableStateFlow<TransitPackageInfo?>(null)
    override val installed = current.asStateFlow()
    private val directory get() = File(context.filesDir, "transit").apply { mkdirs() }
    private val active get() = File(directory, "active.sqlite")
    private data class Staged(val file: File, val info: TransitPackageInfo)
    private var staged: Staged? = null
    private var loaded = false
    private var revision = 0L

    override suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!loaded) {
            directory.listFiles()?.filter { it.name.startsWith("pending-") || it.name.startsWith("import-") }?.forEach { it.delete() }
            current.value = if (active.exists()) validate(active) else null
            loaded = true
        }
    } }

    override suspend fun preview(document: String): TransitPackageInfo = withContext(Dispatchers.IO) { mutex.withLock {
        staged?.file?.delete()
        staged = null
        val archive = File.createTempFile("import-", ".zip", directory)
        val database = File.createTempFile("pending-", ".sqlite", directory)
        try {
            requireNotNull(context.contentResolver.openInputStream(Uri.parse(document))).use { input ->
                archive.outputStream().use { copyBounded(input, it, 32L * 1024 * 1024) }
            }
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.map { it.name }.toSet() == setOf("manifest.json", "network.sqlite") && entries.size == 2) { "不是有效的离线公共交通包" }
                val manifestEntry = zip.getEntry("manifest.json")
                val manifest = zip.getInputStream(manifestEntry).use { input ->
                    val bytes = input.readNBytes(16 * 1024 + 1)
                    require(bytes.size <= 16 * 1024)
                    Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                }
                require(manifest["format"]?.jsonPrimitive?.content == "bailongmap-transit" && manifest["version"]?.jsonPrimitive?.int in 1..3) { "离线包版本不兼容" }
                zip.getInputStream(zip.getEntry("network.sqlite")).use { input ->
                    database.outputStream().use { copyBounded(input, it, 128L * 1024 * 1024) }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                database.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(hash == manifest["sha256"]?.jsonPrimitive?.content) { "文件校验失败" }
                val info = validate(database)
                require(info.name == manifest["name"]?.jsonPrimitive?.content && info.start == manifest["start"]?.jsonPrimitive?.content &&
                    info.end == manifest["end"]?.jsonPrimitive?.content && info.source == manifest["source"]?.jsonPrimitive?.content &&
                    info.stops == manifest["stops"]?.jsonPrimitive?.int && info.routes == manifest["routes"]?.jsonPrimitive?.int) { "清单与数据库不一致" }
                ensureActive()
                staged = Staged(database, info)
                android.util.Log.i("TransitPackages", "operation=transit_import outcome=validated")
                info
            }
        } finally {
            archive.delete()
            if (staged?.file != database) database.delete()
        }
    } }

    override suspend fun install() = withContext(Dispatchers.IO) { mutex.withLock {
        val pending = requireNotNull(staged) { "请先选择离线包" }
        ensureActive()
        Files.move(pending.file.toPath(), active.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        revision++
        current.value = pending.info
        loaded = true
        staged = null
        android.util.Log.i("TransitPackages", "operation=transit_install outcome=ready")
        Unit
    } }

    override suspend fun discard() = withContext(Dispatchers.IO) { mutex.withLock {
        staged?.file?.delete(); staged = null
    } }

    override suspend fun remove() = withContext(Dispatchers.IO) { mutex.withLock {
        check(!active.exists() || active.delete()) { "删除离线数据失败" }
        revision++
        current.value = null; loaded = true
    } }

    override suspend fun plans(origin: RoutePoint, destination: RoutePoint): List<JourneyPlan> =
        calculate(origin,destination,null,null,false)

    override suspend fun allowsBicycles(): Boolean {
        load()
        return withContext(Dispatchers.IO) { mutex.withLock {
            if (current.value == null) false else open(active).use { db ->
                version(db)>=3 && db.rawQuery("SELECT 1 FROM trips WHERE bikes_allowed=1 LIMIT 1",null).use { it.moveToFirst() }
            }
        } }
    }

    override suspend fun plansWithAccess(origin: RoutePoint,destination: RoutePoint,streets: JourneyPlanner,mode: TravelMode): List<JourneyPlan> {
        require(mode in setOf(TravelMode.WALK,TravelMode.BICYCLE))
        load()
        if (installed.value == null) return emptyList()
        val (points,packageRevision)=withContext(Dispatchers.IO) { mutex.withLock { open(active).use { readStops(it).values.filter { stop -> stop.access } to revision } } }
        val radius=if(mode==TravelMode.WALK) 2000 else 5000
        suspend fun connectors(point: RoutePoint,approach: Boolean): List<TransitConnector> = buildList {
            for (stop in points.filter { distance(point,it.point)<=radius }.sortedBy { distance(point,it.point) }.take(2)) {
                try {
                    val options=if(distance(point,stop.point)<=5) listOf(JourneyPlan("at-stop",listOf(
                        JourneyLeg(mode,listOf(point,stop.point),0.0,0.0,"出发点","站点"))))
                        else withTimeoutOrNull(12000) {
                            if(approach) streets.plans(point,stop.point) else streets.plans(stop.point,point)
                        }.orEmpty()
                    val selected=options.minByOrNull { it.durationSeconds } ?: continue
                    val legs=selected.legs
                    if (legs.all { it.mode==mode }) add(TransitConnector(stop.id,legs.mapIndexed { i,leg ->
                        leg.copy(fromName=if(approach && i==0) "出发点" else if(!approach && i==0) stop.name else leg.fromName,
                            toName=if(approach && i==legs.lastIndex) stop.name else if(!approach && i==legs.lastIndex) "目的地" else leg.toName)
                    }, selected.notice))
                } catch(cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
            }
        }
        val access=connectors(origin,true)
        val egress=connectors(destination,false)
        if(access.isEmpty() || egress.isEmpty()) return emptyList()
        return calculate(origin,destination,access,egress,mode==TravelMode.BICYCLE,packageRevision)
    }

    private suspend fun calculate(origin: RoutePoint,destination: RoutePoint,approaches: List<TransitConnector>?,exits: List<TransitConnector>?,bicycle: Boolean,expectedRevision: Long? = null): List<JourneyPlan> {
        load()
        return withContext(Dispatchers.IO) { mutex.withLock {
            check(expectedRevision==null || expectedRevision==revision) { "交通包已更换，请重新规划" }
            val info = requireNotNull(current.value) { "尚未导入公共交通包" }
            open(active).use { db ->
                val meta = metadata(db)
                val zone = ZoneId.of(meta.getValue("timezone"))
                val now = clock.instant()
                val day = now.atZone(zone).toLocalDate()
                val earliest = LocalDate.parse(info.start, DATE).atTime(12, 0).atZone(zone).toEpochSecond() - 43200
                val latest = LocalDate.parse(info.end, DATE).atTime(12, 0).atZone(zone).toEpochSecond() - 43200 + 172800
                require(now.epochSecond in earliest until latest) { "公共交通包不在有效期内，请更新数据" }
                val stops = readStops(db)
                val version = version(db)
                val connections = mutableListOf<TransitConnection>()
                val frequencies = mutableListOf<TransitFrequency>()
                // GTFS service time is measured from local noon minus 12 hours, including DST days.
                for (offset in -2L..1L) {
                    val serviceDay = day.plusDays(offset)
                    val base = serviceDay.atTime(12, 0).atZone(zone).toEpochSecond() - 43200
                    val stamp = serviceDay.format(DATE)
                    // Bound boarding opportunities, not continuation on an already boarded trip.
                    db.rawQuery("""SELECT c.trip,r.name,r.mode,c.origin,c.destination,c.departure,c.arrival,c.pickup,c.dropoff,r.id,${if (version >= 2) "t.source_trip" else "t.id"},${if(version>=3) "t.bikes_allowed" else "0"}
                        FROM connections c JOIN trips t ON t.id=c.trip JOIN routes r ON r.id=t.route
                        JOIN service_dates s ON s.service=t.service AND s.date=?
                        WHERE c.departure>=? AND c.trip IN
                            (SELECT trip FROM connections WHERE departure>=? AND departure<=? AND pickup=0)
                        ${if (version >= 2) "AND NOT EXISTS (SELECT 1 FROM frequencies f WHERE f.trip=t.id)" else ""} ORDER BY c.departure,c.trip,c.sequence""",
                        arrayOf(stamp, (now.epochSecond-base).toString(), (now.epochSecond-base).toString(), (now.epochSecond+86400-base).toString())).use { cursor ->
                        while (cursor.moveToNext()) {
                            ensureActive()
                            connections += TransitConnection("$stamp:${cursor.getInt(0)}", cursor.getString(1), cursor.getInt(2) == 1,
                                cursor.getInt(3), cursor.getInt(4), base+cursor.getLong(5), base+cursor.getLong(6), cursor.getInt(7) == 0, cursor.getInt(8) == 0, cursor.getInt(9), cursor.getInt(10),cursor.getInt(11)==1)
                        }
                    }
                    if (version >= 2) {
                        db.rawQuery("""SELECT f.id,f.trip,f.start,f.end,f.headway FROM frequencies f
                            JOIN trips t ON t.id=f.trip JOIN service_dates s ON s.service=t.service AND s.date=?""", arrayOf(stamp)).use { windows ->
                            while (windows.moveToNext()) {
                                val segments = mutableListOf<TransitConnection>()
                                db.rawQuery("""SELECT r.name,r.mode,c.origin,c.destination,c.departure,c.arrival,c.pickup,c.dropoff,r.id,t.source_trip,${if(version>=3) "t.bikes_allowed" else "0"}
                                    FROM connections c JOIN trips t ON t.id=c.trip JOIN routes r ON r.id=t.route WHERE c.trip=? ORDER BY c.sequence""",
                                    arrayOf(windows.getInt(1).toString())).use { cursor ->
                                    while (cursor.moveToNext()) {
                                        ensureActive()
                                        segments += TransitConnection("$stamp:f${windows.getInt(0)}",cursor.getString(0),cursor.getInt(1)==1,
                                            cursor.getInt(2),cursor.getInt(3),cursor.getLong(4),cursor.getLong(5),cursor.getInt(6)==0,cursor.getInt(7)==0,cursor.getInt(8),cursor.getInt(9),cursor.getInt(10)==1)
                                    }
                                }
                                frequencies += TransitFrequency("$stamp:f${windows.getInt(0)}",segments,base+windows.getLong(2),base+windows.getLong(3),windows.getInt(4))
                            }
                        }
                    }
                }
                val sorted = withContext(Dispatchers.Default) { connections.sortedBy { it.departure } }
                val router = LocalTransitRouter(stops, readFootpaths(db,version), readRules(db,version))
                withContext(Dispatchers.Default) { listOf(router.plan(origin, destination, now.epochSecond, sorted, frequencies,approaches,exits,bicycle)) }
            }
        } }
    }

    private fun validate(file: File): TransitPackageInfo = open(file).use { db ->
        fun scalar(sql: String): String = db.rawQuery(sql, null).use { check(it.moveToFirst()); it.getString(0) }
        require(scalar("PRAGMA application_id") == "1112298578" && scalar("PRAGMA user_version").toInt() in 1..3) { "数据库版本不兼容" }
        require(scalar("PRAGMA quick_check") == "ok") { "数据库已损坏" }
        db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'", null).use { cursor ->
            val expected = setOf("metadata", "stops", "routes", "services", "service_dates", "trips", "connections") +
                if (version(db) >= 2) setOf("frequencies", "footpaths", "transfer_rules") else emptySet()
            val seen = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                require(cursor.getString(0) in setOf("table", "index")) { "数据库含不支持的对象" }
                if (cursor.getString(0) == "table") {
                    require(cursor.getString(1) in expected && !cursor.getString(2).contains("VIRTUAL", true))
                    seen += cursor.getString(1)
                }
            }
            require(seen == expected)
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "数据库存在无效引用" } }
        require(scalar("SELECT COUNT(*) FROM metadata").toInt() == 5)
        require(scalar("SELECT COUNT(*) FROM service_dates").toInt() in 1..1000000)
        require(scalar("SELECT COUNT(*) FROM stops WHERE length(name)=0 OR length(name)>500") == "0")
        val meta = metadata(db)
        require(meta.getValue("name").length <= 200 && meta.getValue("source").length <= 2000)
        val zone = ZoneId.of(meta.getValue("timezone"))
        require(zone.id.isNotBlank())
        val start = LocalDate.parse(meta.getValue("start"), DATE)
        val end = LocalDate.parse(meta.getValue("end"), DATE)
        require(!end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 365)
        if (version(db) >= 3) require(scalar("SELECT COUNT(*) FROM trips WHERE bikes_allowed NOT IN (0,1,2)")=="0")
        if (version(db) >= 2) {
            require(scalar("SELECT COUNT(*) FROM frequencies").toInt() <= 100000)
            require(scalar("SELECT COUNT(*) FROM footpaths").toInt() <= 100000)
            require(scalar("SELECT COUNT(*) FROM transfer_rules").toInt() <= 100000)
            require(scalar("SELECT COUNT(*) FROM frequencies WHERE start<0 OR end<=start OR end>=172800 OR headway<=0 OR headway>172800") == "0")
            require(scalar("SELECT COUNT(*) FROM footpaths WHERE seconds<=0 OR seconds>172800 OR origin=destination") == "0")
            require(scalar("SELECT COUNT(*) FROM transfer_rules WHERE type NOT IN (0,1,2,3) OR seconds<0 OR seconds>172800") == "0")
            require(scalar("SELECT COUNT(*) FROM stops WHERE kind NOT IN (0,1,2,3,4) OR access NOT IN (0,1)") == "0")
        }
        val count = scalar("SELECT COUNT(*) FROM connections").toInt()
        require(count in 1..350000)
        require(scalar("SELECT COUNT(*) FROM connections WHERE departure<0 OR arrival<=departure OR arrival>=172800 OR pickup NOT IN (0,1) OR dropoff NOT IN (0,1)") == "0")
        require(scalar("SELECT COUNT(*) FROM routes WHERE mode NOT IN (1,3) OR length(name)=0 OR length(name)>200") == "0")
        db.rawQuery("SELECT date FROM service_dates", null).use { cursor ->
            while (cursor.moveToNext()) { val date = LocalDate.parse(cursor.getString(0), DATE); require(date in start..end) }
        }
        require(scalar("SELECT COUNT(*) FROM stops").toInt() in 1..50000)
        val stops = readStops(db)
        val routes = scalar("SELECT COUNT(*) FROM routes").toInt()
        require(stops.isNotEmpty() && routes > 0 && meta.getValue("name").isNotBlank() && meta.getValue("source").isNotBlank())
        TransitPackageInfo(meta.getValue("name"), meta.getValue("source"), meta.getValue("start"), meta.getValue("end"), stops.size, routes)
    }

    private fun readStops(db: SQLiteDatabase): Map<Int, TransitStop> = buildMap {
        db.rawQuery("SELECT id,name,lat,lon,${if (version(db) >= 2) "access" else "1"} FROM stops", null).use { cursor ->
            while (cursor.moveToNext()) {
                require(cursor.getString(1).isNotBlank())
                put(cursor.getInt(0), TransitStop(cursor.getInt(0), cursor.getString(1), RoutePoint(cursor.getDouble(2), cursor.getDouble(3)),cursor.getInt(4)==1))
            }
        }
    }
    private fun version(db: SQLiteDatabase) = db.rawQuery("PRAGMA user_version",null).use { it.moveToFirst(); it.getInt(0) }
    private fun readFootpaths(db: SQLiteDatabase, version: Int): List<TransitFootpath> = buildList {
        if (version >= 2) db.rawQuery("SELECT origin,destination,seconds,name FROM footpaths",null).use {
            while (it.moveToNext()) add(TransitFootpath(it.getInt(0),it.getInt(1),it.getInt(2),it.getString(3)))
        }
    }
    private fun readRules(db: SQLiteDatabase, version: Int): List<TransitTransferRule> = buildList {
        if (version >= 2) db.rawQuery("SELECT origin,destination,from_route,from_trip,to_route,to_trip,type,seconds FROM transfer_rules",null).use {
            while (it.moveToNext()) add(TransitTransferRule(it.getInt(0),it.getInt(1),it.getInt(2),it.getInt(3),it.getInt(4),it.getInt(5),it.getInt(6),it.getInt(7)))
        }
    }
    private fun metadata(db: SQLiteDatabase): Map<String, String> = buildMap {
        db.rawQuery("SELECT key,value FROM metadata", null).use { while (it.moveToNext()) put(it.getString(0), it.getString(1)) }
    }
    private fun open(file: File) = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    private suspend fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return
            total += count
            require(total <= limit) { "文件超过离线包大小限制" }
            output.write(buffer, 0, count)
        }
    }
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
}
