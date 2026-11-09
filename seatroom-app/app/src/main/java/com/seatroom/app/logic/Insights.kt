package com.seatroom.app.logic

import com.seatroom.app.vehicle.CabinState
import com.seatroom.app.vehicle.Seat
import com.seatroom.app.vehicle.Signal
import kotlin.math.abs

enum class FindingKind(val label: String) {
    BOARDING("탑승 시간"),
    ALIGHTING("하차 시간"),
    DWELL("귀가 전 머무름"),
    CONFLICT("공조 다툼"),
    HEAT_NO_ACTION("더운데 조작 없음"),
    COMFORT_POINT("온도를 낮춘 시점의 실내 온도"),
    COUNTERMEASURE("졸음 자구책"),
    TRIP("주행 요약"),
}

data class Finding(val t: Long, val kind: FindingKind, val seat: String?, val value: Double?, val text: String)

/** 한 번의 주행(기어 D → P)에서 모은 평소의 나 지표. */
data class TripMetrics(
    val start: Long,
    var end: Long = start,
    /** 수행: 2° 이상 조향 방향 전환 횟수(분당으로 환산) */
    var steeringReversals: Int = 0,
    /** 자세: 출발 2분 뒤부터 주행 중 좌석 위치를 고친 횟수 */
    var postureChanges: Int = 0,
    var backrestAtEnd: Int? = null,
    /** 시야 보상: 수동 상향등이 켜져 있던 시간(ms) */
    var highBeamManualMs: Long = 0,
    /** 계획: 회전마다 깜빡이 → 조향 시작까지(ms). 음수 = 조향이 먼저 */
    val turnLeads: MutableList<Long> = mutableListOf(),
    var countermeasures: Int = 0,
    /** 열 감각: 탄 자리 실내가 27°C 이상 10분 넘게 이어졌는데 아무 조작이 없던 횟수 */
    var heatNoAction: Int = 0,
    /** 출발할 때 운전석 위치(앞뒤, 높이): 누가 운전하는지 가리는 단서 */
    val seatForeAft: Int? = null,
    val seatHeight: Int? = null,
) {
    val minutes: Double get() = (end - start) / 60000.0
    val reversalsPerMin: Double? get() = if (minutes >= 5) steeringReversals / minutes else null
    val orderFlips: Int get() = turnLeads.count { it < 0 }
    val medianTurnLead: Double? get() = turnLeads.filter { it >= 0 }.sorted().let { l -> if (l.size < 3) null else l[l.size / 2].toDouble() }
}

/**
 * 신호 흐름에서 "조작 기록 = 몸의 신호"를 판정한다.
 * 진단하지 않는다: 사실만 기록하고, 평소와의 비교는 [BaselineStore]가 한다.
 */
class Insights(private val onFinding: (Finding) -> Unit, private val onTripEnd: (TripMetrics) -> Unit) {

    var trip: TripMetrics? = null
        private set

    /** 졸음 자구책 이후 휴식을 권하는 마감 시각(효과 약 15분) */
    var restSuggestUntil: Long = 0
        private set

    private val doorOpenedAt = mutableMapOf<Seat, Long>()
    private val lastSetTemp = mutableMapOf<Seat, Float>()
    private val recentSetChanges = ArrayDeque<Triple<Long, Seat, Int>>() // 시각, 좌석, 방향(+1/-1)
    private val heatSince = mutableMapOf<Seat, Long>()
    private val heatReported = mutableSetOf<Seat>()
    private val lastActionAt = mutableMapOf<Seat, Long>()
    private val appInitiated = mutableMapOf<Seat, Long>()

    private var parkedAt: Long? = null
    private var lastAngle: Float? = null
    private var steerDir = 0
    private var steerExtreme: Float? = null
    private var signalOnAt: Long? = null
    private var steerTurnAt: Long? = null
    private var highBeamManualSince: Long? = null
    private var lastFan: Int? = null

    /** 앱이 방 모드로 바꾼 설정은 사람의 조작으로 세지 않는다. */
    fun markAppChange(s: Seat, t: Long) { appInitiated[s] = t }

    fun onSignal(sig: Signal, st: CabinState) {
        val seat = sig.area?.let { a -> Seat.entries.firstOrNull { it.name == a } }
        when (sig.name) {
            "gear.selection" -> onGear(sig, st)
            "door.position" -> if (seat != null) onDoor(sig, seat, st)
            "seat.occupancy" -> if (seat != null) onOccupancy(sig, seat)
            "hvac.setTemp" -> if (seat != null) onSetTemp(sig, seat, st)
            "hvac.fan" -> if (seat == Seat.DRIVER) onDriverFan(sig, st)
            "hvac.cabinTemp" -> if (seat != null) onCabinTemp(sig, seat, st)
            "window.position" -> if (seat == Seat.DRIVER) onDriverWindow(sig, st)
            "steering.angle" -> onSteering(sig, st)
            "turnSignal.state" -> onTurnSignal(sig)
            "highBeam.state" -> onHighBeam(sig)
            else -> if (sig.name.startsWith("posture.")) onPosture(sig, st)
        }
    }

    // ---------- 주행 시작·끝, 귀가 전 머무름 ----------

    private fun onGear(sig: Signal, st: CabinState) {
        when (sig.value) {
            "GEAR_DRIVE", "GEAR_REVERSE" -> {
                parkedAt = null
                if (trip == null) trip = TripMetrics(start = sig.t, seatForeAft = st.posture["foreAft"], seatHeight = st.posture["height"])
            }
            "GEAR_PARK" -> {
                trip?.let { m ->
                    m.end = sig.t
                    m.backrestAtEnd = st.posture["backrest"]
                    highBeamManualSince?.let { m.highBeamManualMs += sig.t - it }
                    highBeamManualSince = null
                    if (m.minutes >= MIN_TRIP_MIN) {
                        onTripEnd(m)
                        emit(sig.t, FindingKind.TRIP, Seat.DRIVER, m.minutes, "주행 %.1f분 → 평소의 나에 기록".format(m.minutes))
                    } else {
                        emit(sig.t, FindingKind.TRIP, Seat.DRIVER, m.minutes, "주행 %.0f초 → 1분 미만이라 기록하지 않음".format(m.minutes * 60))
                    }
                }
                trip = null
                if (st.seat(Seat.DRIVER).occupied != false) parkedAt = sig.t
            }
        }
    }

    // ---------- 승하차 시간 (어르신 방의 하체 거동) ----------

    private fun onDoor(sig: Signal, s: Seat, st: CabinState) {
        val pos = sig.value?.toIntOrNull() ?: return
        if (pos > 0 && doorOpenedAt[s] == null) {
            doorOpenedAt[s] = sig.t
            if (s == Seat.DRIVER) parkedAt?.let { p ->
                val min = (sig.t - p) / 60000.0
                if (min in 0.05..120.0) emit(sig.t, FindingKind.DWELL, s, min, "주차 후 %.1f분 머무름".format(min))
                parkedAt = null
            }
        }
        if (pos == 0) doorOpenedAt.remove(s)
    }

    private fun onOccupancy(sig: Signal, s: Seat) {
        val opened = doorOpenedAt[s] ?: return
        val sec = (sig.t - opened) / 1000.0
        if (sec !in 0.5..120.0) return
        when (sig.value) {
            "true" -> emit(sig.t, FindingKind.BOARDING, s, sec, "${s.label} 탑승 %.1f초".format(sec))
            "false" -> emit(sig.t, FindingKind.ALIGHTING, s, sec, "${s.label} 하차 %.1f초".format(sec))
        }
    }

    // ---------- 공조 다툼, 열 감각, 졸음 자구책 ----------

    private fun onSetTemp(sig: Signal, s: Seat, st: CabinState) {
        val v = sig.value?.toFloatOrNull() ?: return
        val prev = lastSetTemp.put(s, v) ?: return
        if (abs(v - prev) < 0.4f) return
        if (appInitiated[s]?.let { sig.t - it < 10_000 } == true) return
        val dir = if (v > prev) 1 else -1
        lastActionAt[s] = sig.t

        while (recentSetChanges.isNotEmpty() && sig.t - recentSetChanges.first().first > 10 * 60_000) recentSetChanges.removeFirst()
        if (recentSetChanges.any { it.second != s && it.third == -dir }) {
            emit(sig.t, FindingKind.CONFLICT, s, null, "${s.label}이(가) 다른 자리와 반대로 온도를 바꿈")
        }
        recentSetChanges.addLast(Triple(sig.t, s, dir))

        if (dir < 0) {
            st.seat(s).cabinTemp?.let { emit(sig.t, FindingKind.COMFORT_POINT, s, it.toDouble(), "${s.label} 실내 %.1f°C에서 온도를 낮춤".format(it)) }
            if (s == Seat.DRIVER && st.driving) countermeasure(sig.t, "운전석 온도를 낮춤")
        }
    }

    private fun onDriverFan(sig: Signal, st: CabinState) {
        val v = sig.value?.toIntOrNull() ?: return
        val prev = lastFan
        lastFan = v
        lastActionAt[Seat.DRIVER] = sig.t
        if (prev != null && v - prev >= 2 && st.driving && appInitiated[Seat.DRIVER]?.let { sig.t - it < 10_000 } != true) {
            countermeasure(sig.t, "운전석 바람을 세게 함")
        }
    }

    private fun onDriverWindow(sig: Signal, st: CabinState) {
        val v = sig.value?.toIntOrNull() ?: return
        if (v > 5 && st.driving) countermeasure(sig.t, "운전석 창문을 엶")
    }

    private fun countermeasure(t: Long, what: String) {
        if (t < restSuggestUntil - 10 * 60_000) return // 같은 졸음 구간은 한 번만
        restSuggestUntil = t + 15 * 60_000
        trip?.let { it.countermeasures++ }
        emit(t, FindingKind.COUNTERMEASURE, Seat.DRIVER, null, "$what → 효과는 보통 15분 안팎")
    }

    private fun onCabinTemp(sig: Signal, s: Seat, st: CabinState) {
        val v = sig.value?.toFloatOrNull() ?: return
        if (st.seat(s).occupied != true || v < HOT) { heatSince.remove(s); heatReported.remove(s); return }
        val since = heatSince.getOrPut(s) { sig.t }
        val acted = (lastActionAt[s] ?: 0) > since
        if (!acted && sig.t - since > 10 * 60_000 && heatReported.add(s)) {
            trip?.let { it.heatNoAction++ }
            emit(sig.t, FindingKind.HEAT_NO_ACTION, s, v.toDouble(), "${s.label} 실내 %.1f°C가 10분 넘게 이어졌지만 조작 없음".format(v))
        }
    }

    // ---------- 평소의 나: 수행 · 자세 · 시야 보상 · 계획 ----------

    private fun onSteering(sig: Signal, st: CabinState) {
        val a = sig.value?.toFloatOrNull() ?: return
        val m = trip
        if (m != null && st.driving) {
            // 방향 전환: 극값에서 2° 넘게 되돌아오면 1회
            val ext = steerExtreme
            if (ext == null) steerExtreme = a
            else {
                val d = a - ext
                when {
                    steerDir >= 0 && d < -REVERSAL_DEG -> { if (steerDir > 0) m.steeringReversals++; steerDir = -1; steerExtreme = a }
                    steerDir <= 0 && d > REVERSAL_DEG -> { if (steerDir < 0) m.steeringReversals++; steerDir = 1; steerExtreme = a }
                    steerDir > 0 && a > ext -> steerExtreme = a
                    steerDir < 0 && a < ext -> steerExtreme = a
                }
            }
            // 회전 시작: 조향 60° 넘음
            val prev = lastAngle
            if (prev != null && abs(prev) < TURN_DEG && abs(a) >= TURN_DEG) {
                val on = signalOnAt
                if (on != null && sig.t - on in 0..15_000) m.turnLeads += sig.t - on
                else steerTurnAt = sig.t
            }
        }
        lastAngle = a
    }

    private fun onTurnSignal(sig: Signal) {
        if (sig.value == "LEFT" || sig.value == "RIGHT") {
            signalOnAt = sig.t
            // 조향을 먼저 하고 3초 안에 깜빡이: 순서 뒤바뀜
            steerTurnAt?.let { s -> if (sig.t - s in 0..3_000) trip?.turnLeads?.add(-(sig.t - s)) }
            steerTurnAt = null
        } else signalOnAt = null
    }

    private fun onHighBeam(sig: Signal) {
        val (state, sw) = (sig.value ?: return).split("|").let { it[0] to it.getOrNull(1) }
        val manual = state == "ON" && sw != "AUTOMATIC"
        if (manual && highBeamManualSince == null) highBeamManualSince = sig.t
        if (!manual) {
            highBeamManualSince?.let { s -> trip?.let { it.highBeamManualMs += sig.t - s } }
            highBeamManualSince = null
        }
    }

    private fun onPosture(sig: Signal, st: CabinState) {
        val m = trip ?: return
        if (st.driving && sig.t - m.start > 2 * 60_000) m.postureChanges++
    }

    private fun emit(t: Long, k: FindingKind, s: Seat?, v: Double?, text: String) = onFinding(Finding(t, k, s?.name, v, text))

    private companion object {
        const val MIN_TRIP_MIN = 1.0
        const val HOT = 27f
        const val REVERSAL_DEG = 2f
        const val TURN_DEG = 60f
    }
}
