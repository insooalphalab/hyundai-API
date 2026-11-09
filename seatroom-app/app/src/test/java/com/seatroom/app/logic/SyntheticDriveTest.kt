package com.seatroom.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** 가상 주행이 실제 판정 경로를 지나 기대한 지표를 만들고, 시나리오별 알림 규칙이 다르게 동작하는지 확인한다. */
class SyntheticDriveTest {

    private fun axisValues(m: TripMetrics) = mapOf(
        Axis.PERFORMANCE to m.reversalsPerMin,
        Axis.POSTURE to m.postureChanges.toDouble(),
        Axis.VISION to m.highBeamManualMs / 60000.0,
        Axis.PLANNING to m.medianTurnLead?.div(1000.0),
    )

    /** BaselineStore.readings와 같은 계산(표준편차 하한 포함). */
    private fun z(past: List<TripMetrics>, today: TripMetrics, window: Int = 30): Map<Axis, Double?> = Axis.entries.associateWith { ax ->
        val vals = past.takeLast(window).mapNotNull { axisValues(it)[ax] }
        val mean = vals.average()
        val sd = maxOf(ax.minSd, sqrt(vals.sumOf { (it - mean) * (it - mean) } / (vals.size - 1)))
        axisValues(today)[ax]?.let { (it - mean) / sd }
    }

    /** BaselineStore.trend와 같은 계산: 최근 7회 평균 vs 그 이전. */
    private fun trendAxes(trips: List<TripMetrics>): List<Axis> = Axis.entries.filter { ax ->
        val last = trips.takeLast(7).mapNotNull { axisValues(it)[ax] }
        val past = trips.dropLast(7).takeLast(30).mapNotNull { axisValues(it)[ax] }
        if (past.size < 5 || last.size < 3) return@filter false
        val mean = past.average()
        val sd = maxOf(ax.minSd, sqrt(past.sumOf { (it - mean) * (it - mean) } / (past.size - 1)))
        abs((last.average() - mean) / (sd / sqrt(last.size.toDouble()))) >= 2.5
    }

    /** 엔진의 simulateToday와 같은 결정: 축 수 규칙 + 더위 무반응(추세는 따로 알림). */
    private fun alertSent(s: Scenario, history: List<TripMetrics>, today: SyntheticDrive.Result): Boolean {
        val m = today.trips.single().second
        val changed = z(history, m, s.policy.window).values.count { it != null && abs(it) >= s.policy.zThreshold }
        val heat = s.policy.heatAlert && today.findings.any { it.kind == FindingKind.HEAT_NO_ACTION }
        return changed >= s.policy.minAxes || heat
    }

    private fun rates(s: Scenario, runs: Int = 150): Pair<Int, Int> {
        var usual = 0
        var off = 0
        repeat(runs) { i ->
            val h = SyntheticDrive.history(s, 30, seed = 500L + i).trips.map { it.second }
            val p = s.profiles.first()
            val hp = if (s.profiles.size > 1) SyntheticDrive.history(s, 30, seed = 500L + i).trips.filter { it.first.profile == p.name }.map { it.second } else h
            if (alertSent(s, hp, SyntheticDrive.today(s, p, SyntheticDrive.Day.USUAL, seed = 9000L + i))) usual++
            if (alertSent(s, hp, SyntheticDrive.today(s, p, SyntheticDrive.Day.OFF, seed = 9000L + i))) off++
        }
        return usual * 100 / runs to off * 100 / runs
    }

    @Test
    fun scenarioAlertRates() {
        Scenarios.all.forEach { s ->
            val (u, o) = rates(s)
            println("${s.id} ${s.title}: 평소 같은 날 알림 $u% / 다른 날 알림 $o%")
            assertTrue("${s.id} 다른 날은 대부분 알림", o >= 85)
        }
        val (u1, _) = rates(Scenarios.SAFE_FAMILY)
        assertTrue("안전운전자(축 2개 규칙)는 평소 날 알림이 드물어야 함: $u1%", u1 <= 10)
    }

    @Test
    fun seniorUsualDayBreakdown() {
        val s = Scenarios.SENIOR
        var axis = 0
        var heat = 0
        val runs = 150
        repeat(runs) { i ->
            val h = SyntheticDrive.history(s, 30, seed = 500L + i).trips.map { it.second }
            val t = SyntheticDrive.today(s, s.profiles.first(), SyntheticDrive.Day.USUAL, seed = 9000L + i)
            if (z(h, t.trips.single().second, s.policy.window).values.any { it != null && abs(it) >= s.policy.zThreshold }) axis++
            if (t.findings.any { it.kind == FindingKind.HEAT_NO_ACTION }) heat++
        }
        println("고령 평소 날: 축 판정 알림 ${axis * 100 / runs}% / 더위 무반응 사건 ${heat * 100 / runs}%")
    }

    @Test
    fun seniorDriftShowsAsTrend() {
        var hits = 0
        repeat(100) { i ->
            val h = SyntheticDrive.history(Scenarios.SENIOR, 30, seed = 300L + i).trips.map { it.second }
            if (trendAxes(h).isNotEmpty()) hits++
        }
        println("고령 운전자: 30일 기록 중 최근 7회 추세가 잡힌 비율 $hits%")
        assertTrue(hits >= 60)
    }

    @Test
    fun twoDriversIdentifiedBySeatPosition() {
        val s = Scenarios.TWO_DRIVERS
        val hist = SyntheticDrive.history(s, 30, seed = 77).trips
        val centroid = s.profiles.associate { p ->
            val mine = hist.filter { it.first.profile == p.name }.map { it.second }
            p.name to (mine.mapNotNull { it.seatForeAft }.average() to mine.mapNotNull { it.seatHeight }.average())
        }
        var correct = 0
        val n = 100
        repeat(n) { i ->
            val truth = SyntheticDrive.pick(s.profiles, kotlin.random.Random(i))
            val m = SyntheticDrive.today(s, truth, SyntheticDrive.Day.USUAL, seed = 4000L + i).trips.single().second
            val guess = centroid.minBy { (_, c) -> hypot(c.first - m.seatForeAft!!, c.second - m.seatHeight!!) }.key
            if (guess == truth.name) correct++
        }
        // 두 사람 기록을 섞으면 평소 범위가 얼마나 넓어지는지(수행 축)
        fun sd(v: List<Double>) = v.average().let { mu -> sqrt(v.sumOf { (it - mu) * (it - mu) } / (v.size - 1)) }
        val mixed = sd(hist.mapNotNull { it.second.reversalsPerMin })
        val each = s.profiles.map { p -> sd(hist.filter { it.first.profile == p.name }.mapNotNull { it.second.reversalsPerMin }) }
        println("2인: 좌석 위치로 운전자 구분 $correct/$n, 수행 축 평소 폭 섞으면 %.2f vs 사람별 %s".format(mixed, each.map { "%.2f".format(it) }))
        assertEquals(n, correct)
        assertTrue(mixed > each.max() * 1.5)
    }

    @Test
    fun defaultGeneratorStillWorks() {
        val r = SyntheticDrive.generate(20, SyntheticDrive.Day.OFF, seed = 42)
        assertEquals(21, r.trips.size)
        assertTrue(r.findings.any { it.kind == FindingKind.BOARDING })
        assertTrue(r.findings.any { it.kind == FindingKind.DWELL })
    }
}
