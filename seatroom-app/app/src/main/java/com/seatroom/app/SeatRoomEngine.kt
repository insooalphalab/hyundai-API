package com.seatroom.app

import android.content.Context
import com.seatroom.app.logic.Axis
import com.seatroom.app.logic.AxisReading
import com.seatroom.app.logic.BaselineStore
import com.seatroom.app.logic.Co2Estimator
import com.seatroom.app.logic.Finding
import com.seatroom.app.logic.FindingKind
import com.seatroom.app.logic.Insights
import com.seatroom.app.logic.PersonReport
import com.seatroom.app.logic.ReportBuilder
import com.seatroom.app.logic.ReportData
import com.seatroom.app.logic.Scenario
import com.seatroom.app.logic.SignalLog
import com.seatroom.app.logic.SyntheticDrive
import com.seatroom.app.logic.TripSummary
import com.seatroom.app.vehicle.CarSignals
import com.seatroom.app.vehicle.RoomMode
import com.seatroom.app.vehicle.Seat
import com.seatroom.app.vehicle.SeatState
import com.seatroom.app.vehicle.Signal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.hypot
import java.io.File
import kotlin.random.Random

/** 시뮬레이션 결과로 보낼(또는 보내지 않을) 알림과 그 이유. */
data class AlertPreview(
    val sent: Boolean,
    val title: String,
    val lines: List<String>,
    val driverNote: String?,
    /** 차 화면용 한 단어 요약: 달라진 축 이름 등 */
    val tags: List<String> = emptyList(),
    val driver: String? = null,
    val identifiedOk: Boolean? = null,
)

/** 기준선 한 축: 그 사람의 지난 실제 주행 평균·표준편차(하한 적용)·주행 수. */
data class AxisBase(val mean: Double?, val sd: Double?, val n: Int)

/**
 * 끝난 실제 주행 한 번의 평소의 나 4축 값. 키는 화면(HTML)과 같다: perf·pos·vis·plan.
 * [base]는 이번 주행을 뺀 그 사람의 지난 실제 주행으로 만든 기준선, [heat]은 이번 주행 중 더운데 조작 없음이 있었는지.
 */
data class LiveTrip(val t: Long, val minutes: Double, val values: Map<String, Double?>, val base: Map<String, AxisBase>, val heat: Boolean)

/** 손님 판정에 쓰는 실측값: 이번 탑승 뒤 처음 직접 맞춘 온도, 좌석을 옮겼는지, 이 탑승 전 30일 탑승 횟수. */
data class GuestSignal(val firstTemp: Float? = null, val moved: Boolean = false, val seen30: Int = 0)

/** 휴대폰으로 보낸 보고서(시뮬레이션: 차 안에 HTML 파일로 저장). */
data class ReportInfo(val t: Long, val path: String, val pages: Int)

/**
 * 앱 전체에서 하나. 화면이 가려지거나(주행 중 제한) 꺼져도 [CabinService]가 이 엔진을 계속 돌린다.
 * 시나리오를 고르면 그 조건(탑승 구성·운전자 모습·알림 규칙)으로 지난 30일을 시뮬레이션해 기준선을 채운다.
 */
class SeatRoomEngine private constructor(private val app: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val notifier = Notifier(app)

    val car = CarSignals(app)
    private val log = SignalLog(app.filesDir)
    private val baseline = BaselineStore(app)
    private val co2 = Co2Estimator()

    private val _scenario = MutableStateFlow<Scenario?>(null)
    val scenario: StateFlow<Scenario?> = _scenario

    /** 시나리오를 고르지 않고 실제 차 신호만 볼지 */
    private val _liveOnly = MutableStateFlow(false)
    val liveOnly: StateFlow<Boolean> = _liveOnly

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _modes = MutableStateFlow(Seat.entries.associateWith { RoomMode.NORMAL })
    val modes: StateFlow<Map<Seat, RoomMode>> = _modes

    private val _co2 = MutableStateFlow(Co2Estimator.OUTDOOR)
    val co2Ppm: StateFlow<Double> = _co2

    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings

    private val _recent = MutableStateFlow<List<Signal>>(emptyList())
    val recent: StateFlow<List<Signal>> = _recent

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    private val liveProfiles = listOf("운전자 A", "운전자 B", "운전자 C")
    private val _profiles = MutableStateFlow(liveProfiles)
    val profiles: StateFlow<List<String>> = _profiles

    private val _profile = MutableStateFlow(liveProfiles.first())
    val profile: StateFlow<String> = _profile

    private val _readings = MutableStateFlow(baseline.readings(key(liveProfiles.first())))
    val readings: StateFlow<List<AxisReading>> = _readings

    private val _trips = MutableStateFlow(baseline.history(key(liveProfiles.first())))
    val trips: StateFlow<List<TripSummary>> = _trips

    private val _trend = MutableStateFlow<List<Pair<Axis, Double>>>(emptyList())
    val trend: StateFlow<List<Pair<Axis, Double>>> = _trend

    private val _alert = MutableStateFlow<AlertPreview?>(null)
    val alert: StateFlow<AlertPreview?> = _alert

    private val _report = MutableStateFlow<ReportInfo?>(null)
    val report: StateFlow<ReportInfo?> = _report

    private val _lastTrip = MutableStateFlow<LiveTrip?>(null)
    val lastTrip: StateFlow<LiveTrip?> = _lastTrip

    /** 이번 탑승에서 사람이 직접 고친 공조·시트 조작 횟수(앱이 바꾼 것은 빼고). 자리에 새로 앉으면 0부터. */
    private val _fixes = MutableStateFlow<Map<Seat, Int>>(emptyMap())
    val fixes: StateFlow<Map<Seat, Int>> = _fixes
    private val appChangedAt = mutableMapOf<Seat, Long>()
    private val startedAt = System.currentTimeMillis()

    private val _guestSignals = MutableStateFlow<Map<Seat, GuestSignal>>(emptyMap())
    val guestSignals: StateFlow<Map<Seat, GuestSignal>> = _guestSignals
    private val boardedAt = mutableMapOf<Seat, Long>()
    private val lastPos = mutableMapOf<String, String?>()
    private val boardings = app.getSharedPreferences("boardings", Context.MODE_PRIVATE)

    private val _restUntil = MutableStateFlow(0L)
    val restUntil: StateFlow<Long> = _restUntil

    val logDir: String get() = log.dir.absolutePath

    private val insights = Insights(
        onFinding = { f ->
            _findings.update { (listOf(f) + it).take(200) }
            scope.launch(Dispatchers.IO) { log.finding(f) }
        },
        onTripEnd = { m ->
            val k = key(_profile.value)
            baseline.record(k, m)
            // 기록한 뒤 읽으면 마지막 = 이번 주행, 그 앞 = 평소. 가상 주행은 빼고 실제 주행만 기준선으로 쓴다.
            val past = baseline.readings(k, threshold(), realOnly = true)
            _lastTrip.value = LiveTrip(
                m.end, m.minutes,
                mapOf(
                    "perf" to m.reversalsPerMin,
                    "pos" to m.postureChanges.toDouble(),
                    "vis" to m.highBeamManualMs / 60000.0,
                    "plan" to m.medianTurnLead?.div(1000.0),
                ),
                past.associate { AXIS_KEY.getValue(it.axis) to AxisBase(it.baseline, it.sd, it.n) },
                m.heatNoAction > 0,
            )
            refreshBaseline()
            // 알림은 화면(HTML)이 시나리오의 알림 규칙으로 판정해 Android.notify로 보낸다(같은 주행을 두 번 알리지 않게)
        },
    )

    /** 방 모드를 바꾸기 전 설정. 기본으로 돌아갈 때 되돌린다. */
    private val saved = mutableMapOf<Seat, SeatState>()

    init {
        car.start()
        scope.launch {
            car.events.collect { s ->
                val before = insights.restSuggestUntil
                insights.onSignal(s, car.state.value)
                _restUntil.value = insights.restSuggestUntil
                if (insights.restSuggestUntil != before) notifier.drowsy()
                countFix(s)
                _recent.update { (listOf(s) + it).take(80) }
                launch(Dispatchers.IO) { log.signal(s) }
            }
        }
        scope.launch {
            while (true) {
                delay(5_000)
                val prev = _co2.value
                _co2.value = co2.step(car.state.value, 5.0)
                if (prev < Co2Estimator.ALERT && _co2.value >= Co2Estimator.ALERT && car.state.value.recirculation == true) notifier.co2(_co2.value)
            }
        }
    }

    private fun countFix(s: Signal) {
        val seat = s.area?.let { a -> Seat.entries.firstOrNull { it.name == a } } ?: return
        val initial = s.t - startedAt < 3_000 // 등록 직후 현재값 콜백
        if (s.name == "seat.occupancy") {
            if (s.value == "true") {
                _fixes.update { it + (seat to 0) }
                boardedAt[seat] = s.t
                _guestSignals.update { it + (seat to GuestSignal(seen30 = boardingsIn30Days(seat, s.t))) }
                if (!initial) logBoarding(seat, s.t)
            } else boardedAt.remove(seat)
            return
        }
        if (s.name in POSITION_SIGNALS) {
            val prev = lastPos.put("${seat.name}.${s.name.substringAfter('.')}", s.value)
            val since = boardedAt[seat] ?: return
            // 앉은 직후 3초(시트 메모리 이동 등)와 앱 시작 직후는 빼고, 값이 바뀌면 "좌석을 옮김"
            if (!initial && s.t - since > 3_000 && prev != null && prev != s.value) {
                _guestSignals.update { it + (seat to (it[seat] ?: GuestSignal()).copy(moved = true)) }
            }
            return
        }
        if (s.name !in FIX_SIGNALS) return
        // 앱이 방 모드로 바꾼 직후 3초는 사람 조작으로 세지 않는다
        if (initial || s.t - (appChangedAt[seat] ?: 0L) < 3_000) return
        _fixes.update { it + (seat to ((it[seat] ?: 0) + 1)) }
        if (s.name == "hvac.setTemp" && boardedAt[seat] != null) {
            val v = s.value?.toFloatOrNull()
            _guestSignals.update { m ->
                val g = m[seat] ?: GuestSignal()
                if (g.firstTemp != null || v == null) m else m + (seat to g.copy(firstTemp = v))
            }
        }
    }

    private fun boardingTimes(seat: Seat): List<Long> =
        (boardings.getString(seat.name, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }

    private fun boardingsIn30Days(seat: Seat, now: Long) = boardingTimes(seat).count { now - it in 0..DAYS_30 }

    private fun logBoarding(seat: Seat, t: Long) {
        val keep = boardingTimes(seat).filter { t - it < DAYS_30 } + t
        boardings.edit().putString(seat.name, keep.joinToString(",")).apply()
    }

    private fun key(p: String) = _scenario.value?.key(p) ?: p
    private fun threshold() = _scenario.value?.policy?.zThreshold ?: 2.0

    // ---------- 시나리오 ----------

    /**
     * 시나리오 적용: 그 집의 지난 30일을 운전자 비율대로 시뮬레이션해 기준선을 채우고,
     * 방 모드(운전·휴식)를 시나리오대로 차에 적용한다.
     */
    fun applyScenario(s: Scenario) {
        _liveOnly.value = false
        _scenario.value = s
        _profiles.value = s.profiles.map { it.name }
        _profile.value = s.profiles.first().name
        _alert.value = null
        _busy.value = true
        scope.launch(Dispatchers.Default) {
            s.profiles.forEach { baseline.clear(s.key(it.name)) }
            val r = SyntheticDrive.history(s, days = 30)
            r.trips.forEach { (plan, m) -> baseline.record(s.key(plan.profile), m, synthetic = true) }
            _findings.value = r.findings.sortedByDescending { it.t }.take(120)
            refreshBaseline()
            // 몇 주에 걸친 변화는 시나리오를 열 때(주 1회 요약에 해당) 한 번만 알린다
            if (s.policy.trend) _trend.value.takeIf { it.isNotEmpty() }?.let { t -> notifier.trend(t.map { it.first.label }) }
            _busy.value = false
            _messages.update { (listOf("${s.title}: 지난 30일 가상 주행 ${r.trips.size}회") + it).take(30) }
        }
        Seat.entries.forEach { seat -> setMode(seat, s.modes[seat] ?: RoomMode.NORMAL) }
    }

    fun useLiveOnly() {
        _scenario.value = null
        _liveOnly.value = true
        _profiles.value = liveProfiles
        _profile.value = liveProfiles.first()
        _alert.value = null
        refreshBaseline()
    }

    fun leaveScenario() {
        _scenario.value = null
        _liveOnly.value = false
    }

    /**
     * 오늘 주행 한 번을 시뮬레이션한다. 운전자는 비율대로 뽑고(2인 시나리오), 좌석 위치로 자동 구분해
     * 그 사람의 기록에 넣은 뒤, 시나리오의 알림 규칙으로 알림을 보낼지 정한다.
     */
    fun simulateToday(day: SyntheticDrive.Day) {
        val s = _scenario.value ?: return
        _busy.value = true
        scope.launch(Dispatchers.Default) {
            val truth = SyntheticDrive.pick(s.profiles, Random(System.nanoTime()))
            val r = SyntheticDrive.today(s, truth, day)
            val (_, m) = r.trips.firstOrNull() ?: run { _busy.value = false; return@launch }
            var driverNote: String? = null
            var idOk: Boolean? = null
            val who = if (s.autoIdentify) {
                val id = identify(s, m.seatForeAft, m.seatHeight) ?: truth.name
                idOk = id == truth.name
                driverNote = "좌석 위치(앞뒤 ${m.seatForeAft}, 높이 ${m.seatHeight})로 '$id'(으)로 구분" +
                    if (id == truth.name) " — 맞음" else " — 실제는 '${truth.name}'"
                id
            } else truth.name
            baseline.record(s.key(who), m, synthetic = true)
            _profile.value = who
            refreshBaseline()
            _findings.update { (r.findings.sortedByDescending { it.t } + it).take(200) }

            val p = s.policy
            val changed = _readings.value.filter { it.changed }
            val trend = if (p.trend) baseline.trend(s.key(who)) else emptyList()
            val heat = if (p.heatAlert) r.findings.filter { it.kind == FindingKind.HEAT_NO_ACTION } else emptyList()
            val lines = mutableListOf<String>()
            changed.forEach { lines += "${it.axis.label}: ${it.message} (평소 ${"%.2f".format(it.baseline)} → 이번 ${"%.2f".format(it.today)}, ${"%.1f".format(it.z)}σ)" }
            heat.forEach { lines += "열 감각 · ${it.text.removePrefix("[가상] ")}" }
            // 몇 주 추세는 오늘 알림과 따로: 주행마다 반복하지 않고 화면의 "몇 주에 걸친 변화"와 주간 알림으로만
            val sent = changed.size >= p.minAxes || heat.isNotEmpty()
            val title = when {
                sent -> "알림 보냄: 오늘 운전이 평소와 조금 달랐어요"
                changed.isNotEmpty() -> "알림 안 보냄: ${changed.size}개 축만 달라짐 (규칙: ${p.minAxes}개 이상)"
                else -> "알림 없음: 평소 범위"
            }
            val tags = (changed.map { it.axis.label } + heat.map { "열 감각" }).distinct()
            _alert.value = AlertPreview(sent, title, lines, driverNote, tags, who, idOk)
            if (sent) notifier.tripSummary((changed.map { it.axis.label } + heat.map { "열 감각" }).distinct())
            _trend.value = trend
            _busy.value = false
        }
    }

    /**
     * 세부 내용(주행 목록, 4축 그래프, 승하차·열 감각·머무름, 안 되는 신호)을 HTML 보고서로 만든다.
     * 시뮬레이션에서는 휴대폰 전송 대신 차 안에 저장한다: files/reports/
     */
    fun sendReport(summaryTitle: String? = null, summary: List<String> = emptyList(), warn: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val s = _scenario.value
            val names = _profiles.value
            val people = names.map { n ->
                val k = key(n)
                PersonReport(
                    name = n,
                    readings = baseline.readings(k, threshold(), s?.policy?.window ?: 30),
                    trend = if (s?.policy?.trend == true) baseline.trend(k) else emptyList(),
                    trips = baseline.history(k),
                )
            }.filter { it.trips.isNotEmpty() || names.size == 1 }
            val a = _alert.value
            val data = ReportData(
                createdAt = System.currentTimeMillis(),
                scenarioTitle = s?.title ?: "실제 차 신호",
                policyNote = s?.policy?.note,
                alertTitle = summaryTitle ?: a?.title,
                alertLines = if (summaryTitle != null) summary else a?.lines ?: emptyList(),
                alertWarn = warn,
                driverNote = a?.driverNote,
                people = people,
                findings = _findings.value,
                roomModes = _modes.value.filterValues { it != RoomMode.NORMAL }.map { (seat, m) -> "${seat.label} ${m.label}" },
                unsupported = car.problems.value,
            )
            val dir = File(app.filesDir, "reports").apply { mkdirs() }
            val file = File(dir, "report-${data.createdAt}.html")
            file.writeText(ReportBuilder.html(data))
            File(dir, "latest.html").writeText(file.readText())
            _report.value = ReportInfo(data.createdAt, file.absolutePath, 2 + people.size)
            if (summaryTitle != null) notifier.message(Notifier.ID_REPORT, summaryTitle, summary.joinToString("\n")) else notifier.reportSent()
        }
    }

    /** 좌석 위치가 가장 가까운 사람. */
    private fun identify(s: Scenario, foreAft: Int?, height: Int?): String? {
        if (foreAft == null || height == null) return null
        return s.profiles.mapNotNull { p ->
            baseline.seatSignature(s.key(p.name))?.let { (f, h) -> p.name to hypot(f - foreAft, h - height) }
        }.minByOrNull { it.second }?.first
    }

    fun selectProfile(p: String) {
        _profile.value = p
        refreshBaseline()
    }

    /** 실제 차 모드에서 기준선을 빨리 채우기 위한 가상 주행(시나리오 없이). */
    fun addSynthetic(pastDays: Int, today: SyntheticDrive.Day?) {
        val p = key(_profile.value)
        scope.launch(Dispatchers.Default) {
            val r = SyntheticDrive.generate(pastDays, today)
            r.trips.forEach { (_, m) -> baseline.record(p, m, synthetic = true) }
            _findings.update { (r.findings.sortedByDescending { it.t }.take(60) + it).take(200) }
            refreshBaseline()
            _messages.update { (listOf("${_profile.value}: 가상 주행 ${r.trips.size}회 넣음") + it).take(30) }
        }
    }

    fun clearProfile() {
        baseline.clear(key(_profile.value))
        _findings.update { list -> list.filterNot { it.text.startsWith("[가상]") } }
        _alert.value = null
        refreshBaseline()
    }

    private fun refreshBaseline() {
        val k = key(_profile.value)
        _readings.value = baseline.readings(k, threshold(), _scenario.value?.policy?.window ?: 30)
        _trips.value = baseline.history(k)
        _trend.value = if (_scenario.value?.policy?.trend == true) baseline.trend(k) else emptyList()
    }

    // ---------- 방 꾸미기 ----------

    /**
     * 운전(운전석): 시원하게, 통풍 1, 조명 보통. 휴식: 따뜻하게, 바람 1, 열선 1, 조명 어둡게. 집중: 바람 1, 조명 밝게.
     * 온도·바람은 화면이 그 자리 주인에게서 배운 값을 넘겨주면 그 값을 쓴다.
     * 등받이는 건드리지 않는다(눕힌 자세는 사고 때 벨트 아래로 미끄러질 위험).
     */
    fun setMode(s: Seat, mode: RoomMode, temp: Float? = null, fan: Int? = null) {
        if (_modes.value[s] == mode && temp == null) return
        val now = System.currentTimeMillis()
        insights.markAppChange(s, now)
        appChangedAt[s] = now
        if (_modes.value[s] == RoomMode.NORMAL && mode != RoomMode.NORMAL) saved[s] = car.state.value.seat(s)
        _modes.update { it + (s to mode) }
        val done: (String) -> Unit = { msg -> _messages.update { (listOf(msg) + it).take(30) } }
        when (mode) {
            RoomMode.REST -> {
                car.setTemperature(s, temp ?: 24f, done); car.setFan(s, fan ?: 1, done)
                car.setSeatHeat(s, 1, done); car.setSeatVent(s, 0, done); car.setAmbient(s, 10, done)
            }
            RoomMode.DRIVE -> {
                car.setTemperature(s, temp ?: 21f, done); car.setFan(s, fan ?: 3, done)
                car.setSeatHeat(s, 0, done); car.setSeatVent(s, 1, done); car.setAmbient(s, 60, done)
            }
            RoomMode.FOCUS -> {
                car.setTemperature(s, temp ?: 22f, done); car.setFan(s, fan ?: 1, done)
                car.setAmbient(s, 80, done)
            }
            RoomMode.NORMAL -> saved.remove(s)?.let { p ->
                p.setTemp?.let { car.setTemperature(s, it, done) }
                p.fan?.let { car.setFan(s, it, done) }
                p.seatHeat?.let { car.setSeatHeat(s, it, done) }
                p.seatVent?.let { car.setSeatVent(s, it, done) }
            }
        }
    }

    /** CO₂ 추정이 높을 때 잠깐 외기로. */
    fun freshAir() = setRecirculation(false)

    fun setRecirculation(on: Boolean) {
        car.setRecirculation(on) { msg -> _messages.update { (listOf(msg) + it).take(30) } }
    }

    companion object {
        private val FIX_SIGNALS = setOf("hvac.setTemp", "hvac.fan", "seat.heat", "seat.vent")
        private val POSITION_SIGNALS = setOf("seat.foreAft", "seat.height", "posture.foreAft", "posture.height")
        private val AXIS_KEY = mapOf(Axis.PERFORMANCE to "perf", Axis.POSTURE to "pos", Axis.VISION to "vis", Axis.PLANNING to "plan")
        private const val DAYS_30 = 30L * 24 * 3600 * 1000

        @Volatile private var instance: SeatRoomEngine? = null
        fun get(context: Context): SeatRoomEngine =
            instance ?: synchronized(this) { instance ?: SeatRoomEngine(context.applicationContext).also { instance = it } }
    }
}
