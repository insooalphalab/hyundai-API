package com.seatroom.app.logic

import com.seatroom.app.vehicle.CabinState
import com.seatroom.app.vehicle.Seat
import com.seatroom.app.vehicle.Signal
import com.seatroom.app.vehicle.after
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 가상 주행: SDK 콜백과 같은 모양의 신호 흐름을 만들어, 실제와 똑같이 [Insights] → [BaselineStore]로 통과시킨다.
 * 운전자 모습은 [DriverStyle]이 정한다(시나리오별). 실제 데이터와 구분되게 synthetic 표시.
 *
 * 값은 가정이며, 근거가 있는 것만 문헌 크기에 맞췄다.
 * - 상향등: 젊은 운전자 100 km당 4.6분, 고령 24.2분(UMTRI). 20 km 주행 기준 약 1분 vs 약 5분.
 * - 승하차: 고령자는 근력·균형 때문에 더 오래 걸림(초 단위는 가정).
 * - 조향 방향 전환(2° 기준) 분당 약 10~15회, 깜빡이 → 조향 약 2.5~3.5초: 가정값.
 */
object SyntheticDrive {

    /** 하루의 성격. USUAL = 평소, OFF = 평소와 다른 날(조향·자세·시야·계획이 함께 흔들림). */
    enum class Day { USUAL, OFF }

    /** 한 번의 가상 주행 계획. [driftDay]: 느린 변화가 며칠째 쌓였는지(0이면 없음). */
    data class Plan(val start: Long, val profile: String, val style: DriverStyle, val day: Day, val driftDay: Int = 0)

    data class Result(val trips: List<Pair<Plan, TripMetrics>>, val findings: List<Finding>)

    /** 기본 운전자로 지난 [days]일 + (선택) 오늘. 단위 테스트용. */
    fun generate(days: Int, today: Day?, seed: Long = System.currentTimeMillis()): Result {
        val now = System.currentTimeMillis()
        val rnd = Random(seed)
        val plans = (days downTo 1).map { Plan(now - it * DAY + rnd.nextLong(-40, 40) * 60_000L, "기본", DriverStyle(), Day.USUAL) } +
            listOfNotNull(today?.let { Plan(now - 40 * 60_000L, "기본", DriverStyle(), it) })
        return run(plans, seed)
    }

    /** 시나리오의 운전자 비율대로 지난 [days]일을 채운다. 느린 변화는 마지막 driftDays일에 쌓인다. */
    fun history(s: Scenario, days: Int, seed: Long = System.currentTimeMillis()): Result {
        val now = System.currentTimeMillis()
        val rnd = Random(seed)
        val plans = (days downTo 1).map { d ->
            val p = pick(s.profiles, rnd)
            val drift = if (p.style.driftDays > 0) (p.style.driftDays - d + 1).coerceAtLeast(0) else 0
            Plan(now - d * DAY + rnd.nextLong(-40, 40) * 60_000L, p.name, p.style, Day.USUAL, drift)
        }
        return run(plans, seed + 1)
    }

    /** 오늘 한 번. 고령 시나리오의 느린 변화는 오늘까지 이어진다. */
    fun today(s: Scenario, profile: Profile, day: Day, seed: Long = System.currentTimeMillis()): Result {
        val drift = if (profile.style.driftDays > 0) profile.style.driftDays + 1 else 0
        return run(listOf(Plan(System.currentTimeMillis() - 40 * 60_000L, profile.name, profile.style, day, drift)), seed)
    }

    fun pick(profiles: List<Profile>, rnd: Random): Profile {
        var x = rnd.nextDouble() * profiles.sumOf { it.share }
        profiles.forEach { p -> x -= p.share; if (x <= 0) return p }
        return profiles.last()
    }

    private fun run(plans: List<Plan>, seed: Long): Result {
        val rnd = Random(seed)
        val trips = mutableListOf<Pair<Plan, TripMetrics>>()
        val findings = mutableListOf<Finding>()
        var current: Plan? = null
        val insights = Insights(
            onFinding = { f -> findings += f.copy(text = "[가상] " + f.text) },
            onTripEnd = { m -> current?.let { trips += it to m } },
        )
        var state = CabinState()
        plans.forEach { plan ->
            current = plan
            trip(plan, rnd).forEach { sig ->
                state = state.after(sig)
                insights.onSignal(sig, state)
            }
        }
        return Result(trips, findings)
    }

    private fun trip(plan: Plan, rnd: Random): List<Signal> {
        val st = plan.style
        val off = plan.day == Day.OFF
        // 느린 변화: 상향등↑, 깜빡이→조향 간격↓ (며칠째인지에 비례)
        val drift = 1.0 + st.driftPerDay * plan.driftDay
        val t0 = plan.start
        val out = mutableListOf<Signal>()
        fun at(t: Long, name: String, seat: Seat?, v: Any?) { out += Signal(t, name, seat?.name, v?.toString()) }
        val elderPassenger = st.passengerElder && rnd.nextDouble() < 0.6
        val night = rnd.nextDouble() < if (off) 1.0 else st.nightProb

        // 출발 전: 빈 차
        Seat.entries.forEach { at(t0 - 5_000, "seat.occupancy", it, false); at(t0 - 5_000, "door.position", it, 0) }
        at(t0 - 5_000, "gear.selection", null, "GEAR_PARK")

        // 탑승 → 좌석을 자기 위치로 맞춤(운전자 자동 구분의 단서)
        board(out, t0, Seat.DRIVER, rnd.gauss(st.boardingSec * (if (off) 1.3 else 1.0), st.boardingSec * 0.2))
        if (elderPassenger) board(out, t0 + 4_000, Seat.PASSENGER, rnd.gauss(if (off) 13.0 else 9.0, 1.5))
        at(t0 + 10_000, "posture.foreAft", Seat.DRIVER, st.seatForeAft + rnd.nextInt(-3, 4))
        at(t0 + 10_000, "posture.height", Seat.DRIVER, st.seatHeight + rnd.nextInt(-3, 4))
        at(t0 + 10_000, "posture.backrest", Seat.DRIVER, 100)

        // 공조 초기값
        at(t0 + 20_000, "hvac.setTemp", Seat.DRIVER, 22.0f)
        at(t0 + 20_000, "hvac.setTemp", Seat.PASSENGER, 22.0f)
        at(t0 + 20_000, "hvac.cabinTemp", Seat.DRIVER, 24.0f)
        if (elderPassenger) at(t0 + 20_000, "hvac.cabinTemp", Seat.PASSENGER, 24.0f)

        // 주행
        val start = t0 + 30_000
        val minutes = max(10.0, rnd.gauss(25.0, 6.0))
        val end = start + (minutes * 60_000).toLong()
        at(start, "gear.selection", null, "GEAR_DRIVE")

        // 수행: 미세 조향 방향 전환
        val perMin = rnd.gauss(st.reversalsPerMin, st.reversalsSd) * if (off) 1.35 else 1.0
        var t = start + 2_000L
        var sign = 1
        while (t < end) {
            at(t, "steering.angle", null, sign * rnd.uniform(2.5, 6.0).toFloat())
            sign = -sign
            t += (rnd.expo(60.0 / perMin) * 1000).toLong().coerceAtLeast(300)
        }

        // 계획: 교차로 회전 (깜빡이 → 조향)
        val lead = st.turnLead / drift
        repeat(rnd.nextInt(5, 9)) {
            val ts = start + (rnd.nextDouble(0.1, 0.9) * (end - start)).toLong()
            val steerFirst = off && rnd.nextDouble() < 0.2
            val dir = if (rnd.nextBoolean()) "LEFT" else "RIGHT"
            if (steerFirst) {
                at(ts, "steering.angle", null, 95f)
                at(ts + 1_200, "turnSignal.state", null, dir)
            } else {
                val l = max(0.4, if (off) rnd.gauss(lead * 0.55, 0.9) else rnd.gauss(lead, st.turnLeadSd))
                at(ts, "turnSignal.state", null, dir)
                at(ts + (l * 1000).toLong(), "steering.angle", null, 95f)
            }
            at(ts + 6_000, "steering.angle", null, 3f)
            at(ts + 6_500, "turnSignal.state", null, "OFF")
        }

        // 자세: 출발 2분 뒤부터 등받이 고침
        repeat(rnd.poisson(if (off) st.postureLambda + 4.0 else st.postureLambda)) { i ->
            val ts = start + 150_000 + (rnd.nextDouble() * (end - start - 150_000)).toLong()
            at(ts, "posture.backrest", Seat.DRIVER, 100 + (if (off) 2 else 0) + i % 2)
        }

        // 시야 보상: 밤길 수동 상향등
        if (night) {
            val m = max(0.1, if (off) rnd.gauss(st.highBeamMin * 4 + 1, 1.0) else rnd.gauss(st.highBeamMin * drift, st.highBeamMin * 0.35))
            val ts = start + 60_000
            at(ts, "highBeam.state", null, "ON|ON")
            at(ts + (m * 60_000).toLong(), "highBeam.state", null, "OFF|ON")
        }

        // 열 감각: 동승 어르신이 온도를 낮추는 시점
        if (elderPassenger && rnd.nextDouble() < 0.5) {
            val ts = start + 8 * 60_000
            val c = if (off) rnd.gauss(28.5, 0.5) else rnd.gauss(26.5, 0.5)
            at(ts, "hvac.cabinTemp", Seat.PASSENGER, c.toFloat())
            at(ts + 2_000, "hvac.setTemp", Seat.PASSENGER, 21.0f)
        }
        // 열 감각 둔화: 운전자가 더운데도 10분 넘게 그대로
        if (st.heatInsensitive && rnd.nextDouble() < (if (off) 1.0 else 0.25)) {
            val ts = start + 5 * 60_000
            at(ts, "hvac.cabinTemp", Seat.DRIVER, 28.2f)
            at(ts + 6 * 60_000, "hvac.cabinTemp", Seat.DRIVER, 28.6f)
            at(ts + 11 * 60_000, "hvac.cabinTemp", Seat.DRIVER, 28.9f)
        }

        // 주차 → 귀가 전 머무름 → 하차
        at(end, "gear.selection", null, "GEAR_PARK")
        val dwell = (exp(ln(1.5) + rnd.gauss(0.0, 0.6)) * 60_000).toLong()
        alight(out, end + dwell, Seat.DRIVER, rnd.gauss(st.boardingSec * 0.85, st.boardingSec * 0.2))
        if (elderPassenger) alight(out, end + dwell + 4_000, Seat.PASSENGER, rnd.gauss(if (off) 14.0 else 10.0, 1.5))
        return out.sortedBy { it.t }
    }

    private fun board(out: MutableList<Signal>, t: Long, s: Seat, sec: Double) {
        out += Signal(t, "door.position", s.name, "60")
        out += Signal(t + (max(1.0, sec) * 1000).toLong(), "seat.occupancy", s.name, "true")
        out += Signal(t + (max(1.0, sec) * 1000).toLong() + 1_500, "door.position", s.name, "0")
    }

    private fun alight(out: MutableList<Signal>, t: Long, s: Seat, sec: Double) {
        out += Signal(t, "door.position", s.name, "60")
        out += Signal(t + (max(1.0, sec) * 1000).toLong(), "seat.occupancy", s.name, "false")
        out += Signal(t + (max(1.0, sec) * 1000).toLong() + 2_000, "door.position", s.name, "0")
    }

    private const val DAY = 24 * 3600_000L

    private fun Random.gauss(mean: Double, sd: Double): Double {
        val u = nextDouble().coerceAtLeast(1e-12)
        val v = nextDouble()
        return mean + sd * sqrt(-2 * ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    private fun Random.uniform(a: Double, b: Double) = a + (b - a) * nextDouble()
    private fun Random.expo(mean: Double) = -mean * ln(nextDouble().coerceAtLeast(1e-12))
    private fun Random.poisson(lambda: Double): Int {
        val l = exp(-lambda); var k = 0; var p = 1.0
        do { k++; p *= nextDouble() } while (p > l)
        return k - 1
    }
}
