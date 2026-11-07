package org.storyteller_f.bailongmap.data.transit

import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.storyteller_f.bailongmap.data.model.*

internal data class TransitStop(val id: Int, val name: String, val point: RoutePoint, val access: Boolean = true)
internal data class TransitConnection(
    val trip: String, val route: String, val subway: Boolean,
    val origin: Int, val destination: Int, val departure: Long, val arrival: Long,
    val pickup: Boolean = true, val dropoff: Boolean = true,
    val routeId: Int = -1, val sourceTrip: Int = -1, val bikesAllowed: Boolean = false,
)
internal data class TransitConnector(val stop: Int,val legs: List<JourneyLeg>, val notice: String? = null)
internal data class TransitFootpath(val origin: Int, val destination: Int, val seconds: Int, val name: String)
internal data class TransitFrequency(val id: String, val segments: List<TransitConnection>, val start: Long, val end: Long, val headway: Int)
internal data class TransitTransferRule(val origin: Int, val destination: Int, val fromRoute: Int, val fromTrip: Int, val toRoute: Int, val toTrip: Int, val type: Int, val seconds: Int) {
    val rank: Int get() = when {
        fromTrip >= 0 && toTrip >= 0 -> 5
        (fromTrip >= 0 && toRoute >= 0) || (fromRoute >= 0 && toTrip >= 0) -> 4
        fromTrip >= 0 || toTrip >= 0 -> 3
        fromRoute >= 0 && toRoute >= 0 -> 2
        fromRoute >= 0 || toRoute >= 0 -> 1
        else -> 0
    }
}

internal fun distance(a: RoutePoint, b: RoutePoint): Double {
    val radians = PI / 180
    val dLat = (b.latitude - a.latitude) * radians
    val dLon = (b.longitude - a.longitude) * radians
    val h = sin(dLat / 2).pow(2) + cos(a.latitude * radians) * cos(b.latitude * radians) * sin(dLon / 2).pow(2)
    return 12742000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Earliest-arrival search retaining arriving-trip context for scoped transfer rules. */
internal class LocalTransitRouter(
    private val stops: Map<Int, TransitStop>,
    footpaths: List<TransitFootpath> = emptyList(),
    rules: List<TransitTransferRule> = emptyList(),
) {
    private val walks = footpaths.groupBy { it.origin }
    private val transfers = rules.groupBy { it.origin to it.destination }
    private data class Trace(val previous: Trace?, val leg: JourneyLeg, val notice: String? = null)
    private data class Context(val connection: TransitConnection, val stop: Int, val arrival: Long)
    private data class State(val stop: Int, val time: Long, val context: Context?, val trace: Trace)
    private data class Key(val stop: Int, val alight: Int, val source: String, val route: Int)
    private fun key(state: State): Key {
        val context = state.context
        val connection = context?.connection
        return Key(state.stop, context?.stop ?: -1,
            connection?.let { if (it.sourceTrip >= 0) it.sourceTrip.toString() else it.trip }.orEmpty(), connection?.routeId ?: -1)
    }

    suspend fun plan(origin: RoutePoint, destination: RoutePoint, now: Long, connections: List<TransitConnection>, frequencies: List<TransitFrequency> = emptyList(),
        approaches: List<TransitConnector>? = null, exits: List<TransitConnector>? = null,
        bicycleOnTransit: Boolean = false): JourneyPlan {
        val tripSegments = connections.groupBy { it.trip }.mapValues { (_, rows) -> rows.sortedBy { it.departure } }
        val boarding = tripSegments.values.flatMap { segments -> segments.indices.filter { segments[it].pickup }.map { segments to it } }
            .groupBy { (segments, index) -> segments[index].origin }
        val frequencyBoarding = frequencies.flatMap { frequency -> frequency.segments.indices.filter { frequency.segments[it].pickup }.map { frequency to it } }
            .groupBy { (frequency, index) -> frequency.segments[index].origin }
        val labels = mutableMapOf<Key, MutableList<State>>()
        val coroutine = currentCoroutineContext()
        var work = 0
        fun tick() {
            if (++work % 1024 == 0) coroutine.ensureActive()
            check(work <= 5000000) { "查询规模过大，请缩小区域数据" }
        }
        val queue = StateHeap()
        fun offer(state: State) {
            val key = key(state)
            tick()
            val previous = labels.getOrPut(key) { mutableListOf() }
            fun alight(s: State) = s.context?.arrival ?: 0
            if (previous.any { it.time <= state.time && alight(it) <= alight(state) }) return
            previous.removeAll { state.time <= it.time && alight(state) <= alight(it) }
            previous += state
            queue.add(state)
        }
        fun trace(legs: List<JourneyLeg>,previous: Trace? = null, notice: String? = null): Trace {
            var result=previous
            legs.forEachIndexed { index, leg -> result=Trace(result,leg,notice.takeIf { index == 0 }) }
            return requireNotNull(result)
        }
        if (approaches != null) approaches.forEach { connector ->
            offer(State(connector.stop,now+ceil(connector.legs.sumOf { it.durationSeconds }).toLong(),null,trace(connector.legs,notice=connector.notice)))
        }
        else stops.values.filter { it.access }.forEach { stop ->
            val meters = distance(origin, stop.point)
            if (meters <= 800) {
                val leg = walk(origin, stop.point, "出发点", stop.name)
                offer(State(stop.id, now + leg.durationSeconds.toLong(), null, Trace(null, leg)))
            }
        }
        fun ready(state: State, next: TransitConnection): Long? {
            if (bicycleOnTransit && !next.bikesAllowed) return null
            val context = state.context ?: return state.time
            val previous = context.connection
            val rule = transfers[context.stop to state.stop].orEmpty().filter {
                (it.fromRoute < 0 || it.fromRoute == previous.routeId) && (it.fromTrip < 0 || it.fromTrip == previous.sourceTrip) &&
                (it.toRoute < 0 || it.toRoute == next.routeId) && (it.toTrip < 0 || it.toTrip == next.sourceTrip)
            }.maxByOrNull { it.rank }
            if (rule?.type == 3) return null
            return maxOf(state.time, context.arrival + (rule?.seconds ?: 120))
        }
        fun ride(state: State, segments: List<TransitConnection>, index: Int, shift: Long, estimated: Boolean) {
            val first = segments[index]
            val points = mutableListOf(stops.getValue(first.origin).point)
            for (position in index..segments.lastIndex) {
                tick()
                val row = segments[position]
                if (bicycleOnTransit && !row.bikesAllowed) break
                points += stops.getValue(row.destination).point
                if (!row.dropoff) continue
                val arrival = row.arrival + shift
                val leg = JourneyLeg(if (row.subway) TravelMode.SUBWAY else TravelMode.TRANSIT,
                    points.toList(), points.zipWithNext().sumOf { (a,b) -> distance(a,b) },
                    (arrival-state.time).toDouble(), stops.getValue(first.origin).name, stops.getValue(row.destination).name,
                    first.route + if (estimated) "（间隔估算）" else "")
                offer(State(row.destination, arrival, Context(row, row.destination, arrival), Trace(state.trace, leg)))
            }
        }
        val exitByStop=exits?.groupBy { it.stop }?.mapValues { (_,items) -> items.minBy { c -> c.legs.sumOf { it.durationSeconds } } }
        var bestExit: List<JourneyLeg> = emptyList()
        var bestExitNotice: String? = null
        var best: State? = null
        var bestTime = Long.MAX_VALUE
        var operations = 0
        while (queue.isNotEmpty()) {
            val state = queue.remove()
            if (labels[key(state)].orEmpty().none { it === state }) continue
            if (state.time >= bestTime) break
            currentCoroutineContext().ensureActive()
            check(++operations <= 200000) { "查询规模过大，请缩小区域数据" }
            val stop = stops.getValue(state.stop)
            val exitDistance = distance(stop.point, destination)
            val exitLegs=if (exitByStop != null) exitByStop[state.stop]?.legs
                else if (stop.access && exitDistance<=800) listOf(walk(stop.point,destination,stop.name,"目的地")) else null
            if (state.context != null && exitLegs != null) {
                val end = state.time + ceil(exitLegs.sumOf { it.durationSeconds }).toLong()
                if (end < bestTime) { best = state; bestTime = end; bestExit=exitLegs; bestExitNotice=exitByStop?.get(state.stop)?.notice }
            }
            walks[state.stop].orEmpty().forEach { path ->
                val to = stops.getValue(path.destination)
                val leg = JourneyLeg(TravelMode.WALK, listOf(stop.point, to.point), distance(stop.point, to.point), path.seconds.toDouble(), stop.name, to.name, path.name)
                offer(State(to.id, state.time+path.seconds, state.context, Trace(state.trace, leg)))
            }
            boarding[state.stop].orEmpty().forEach { (segments,index) ->
                tick()
                val next = segments[index]
                val readyAt = ready(state, next)
                if (readyAt != null && next.departure >= readyAt && next.departure <= now+86400) ride(state,segments,index,0,false)
            }
            frequencyBoarding[state.stop].orEmpty().forEach { (frequency,index) ->
                tick()
                val next = frequency.segments[index]
                val readyAt = ready(state,next) ?: return@forEach
                val offset = next.departure-frequency.segments.first().departure
                // Unscheduled service has no fixed phase. Budget one full headway,
                // never present a fabricated aligned departure as an exact timetable.
                val departure = maxOf(readyAt, frequency.start+offset)+frequency.headway
                if (departure-offset < frequency.end && departure <= now+86400) ride(state,frequency.segments,index,departure-next.departure,true)
            }
        }
        val end = best ?: error("未来 24 小时内未找到可用班次；请检查日期、站点和换乘规则")
        val chosenTrace = generateSequence(end.trace) { it.previous }.toList().asReversed()
        val legs = chosenTrace.map { it.leg } + bestExit
        val connectorNotices = (chosenTrace.mapNotNull { it.notice } + listOfNotNull(bestExitNotice)).distinct()
        return JourneyPlan("local-transit",legs,notice=connectorNotices.joinToString("；", postfix=if (connectorNotices.isEmpty()) "" else "；") + (if (approaches!=null) "道路接驳已按路网计算；" else "接驳为直线估算；") + (if (bicycleOnTransit) "自带自行车，仅使用明确允许携车的班次；站内请推行。" else "") + "离线时刻表；含候车时间及换乘规则。未指定换乘最短时间时预留 2 分钟。发车间隔线路按完整间隔估算候车。公共交通线路连线为示意，请核对实际路径。")
    }

    private fun walk(a: RoutePoint, b: RoutePoint, from: String, to: String): JourneyLeg {
        val meters = distance(a,b)
        return JourneyLeg(TravelMode.WALK,listOf(a,b),meters,ceil(meters/1.2),from,to,"接驳（直线估算）")
    }
    private class StateHeap {
        private val values = mutableListOf<State>()
        fun isNotEmpty() = values.isNotEmpty()
        fun add(state: State) {
            values.add(state)
            var index = values.lastIndex
            while (index > 0) {
                val parent = (index-1)/2
                if (values[parent].time <= state.time) break
                values[index] = values[parent]; index = parent
            }
            values[index] = state
        }
        fun remove(): State {
            val first = values.first()
            val last = values.removeAt(values.lastIndex)
            if (values.isEmpty()) return first
            var index = 0
            while (index*2+1 < values.size) {
                val left = index*2+1
                val right = left+1
                val child = if (right < values.size && values[right].time < values[left].time) right else left
                if (values[child].time >= last.time) break
                values[index] = values[child]; index = child
            }
            values[index] = last
            return first
        }
    }
}
