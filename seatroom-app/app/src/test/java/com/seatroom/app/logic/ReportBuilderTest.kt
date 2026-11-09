package com.seatroom.app.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/** 시나리오 2(고령 운전자)로 휴대폰 보고서 견본을 만든다: app/build/report-sample.html */
class ReportBuilderTest {

    private fun value(m: TripMetrics, ax: Axis) = when (ax) {
        Axis.PERFORMANCE -> m.reversalsPerMin
        Axis.POSTURE -> m.postureChanges.toDouble()
        Axis.VISION -> m.highBeamManualMs / 60000.0
        Axis.PLANNING -> m.medianTurnLead?.div(1000.0)
    }

    @Test
    fun buildsSampleReport() {
        val s = Scenarios.SENIOR
        val hist = SyntheticDrive.history(s, 30, seed = 11)
        val today = SyntheticDrive.today(s, s.profiles.first(), SyntheticDrive.Day.OFF, seed = 12)
        val all = hist.trips.map { it.second } + today.trips.map { it.second }
        val trips = all.reversed().map { m -> TripSummary(m.start, m.minutes, Axis.entries.associateWith { value(m, it) }, m.countermeasures, true) }
        val past = all.dropLast(1).takeLast(s.policy.window)
        val readings = Axis.entries.map { ax ->
            val v = past.mapNotNull { value(it, ax) }
            val mean = v.average()
            val sd = maxOf(ax.minSd, sqrt(v.sumOf { (it - mean) * (it - mean) } / (v.size - 1)))
            AxisReading(ax, value(all.last(), ax), mean, sd, 30, s.policy.zThreshold)
        }
        val html = ReportBuilder.html(
            ReportData(
                createdAt = System.currentTimeMillis(),
                scenarioTitle = s.title,
                policyNote = s.policy.note,
                alertTitle = "알림 보냄: 오늘 운전이 평소와 조금 달랐어요",
                alertLines = readings.filter { it.changed }.map { "${it.axis.label}: ${it.message}" },
                driverNote = null,
                people = listOf(PersonReport(s.profiles.first().name, readings, listOf(Axis.PLANNING to -0.8), trips)),
                findings = (hist.findings + today.findings).sortedByDescending { it.t },
                roomModes = emptyList(),
                unsupported = listOf("바람 세기 뒷좌석 왼쪽: area not supported"),
            ),
        )
        File("build/report-sample.html").writeText(html)
        assertTrue(html.contains("<svg"))
        assertTrue(html.contains("어르신"))
    }
}
