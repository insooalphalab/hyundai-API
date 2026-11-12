package com.seatroom.app.logic

import com.seatroom.app.vehicle.CabinState
import com.seatroom.app.vehicle.Signal
import com.seatroom.app.vehicle.after
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InsightsTest {
    private fun run(signals: List<Signal>): List<TripMetrics> {
        val trips = mutableListOf<TripMetrics>()
        val insights = Insights(onFinding = {}, onTripEnd = { trips += it })
        var st = CabinState()
        signals.forEach { s -> st = st.after(s); insights.onSignal(s, st) }
        return trips
    }

    /** 출발 전부터 켜 둔 수동 상향등은 출발 시각부터만 시야 보상 시간으로 센다. */
    @Test
    fun highBeamBeforeDepartureCountsFromTripStart() {
        val min = 60_000L
        val trips = run(listOf(
            Signal(0, "highBeam.state", null, "ON|ON"),
            Signal(30 * min, "gear.selection", null, "GEAR_DRIVE"),
            Signal(32 * min, "highBeam.state", null, "OFF|OFF"),
            Signal(40 * min, "gear.selection", null, "GEAR_PARK"),
        ))
        assertEquals(1, trips.size)
        assertEquals(2 * min, trips[0].highBeamManualMs)
    }

    @Test
    fun pruneDeletesOnlyOldDatedLogs() {
        val root = Files.createTempDirectory("seatroom").toFile()
        val log = SignalLog(root)
        val day = 86_400_000L
        val now = 400 * day
        // 파일 이름은 SignalLog와 같은 규칙(날짜.종류.jsonl). org.json은 단위 테스트에서 못 쓰므로 파일을 직접 만든다.
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        listOf(45, 5, 0).forEach { ago -> File(log.dir, fmt.format(java.util.Date(now - ago * day)) + ".signals.jsonl").writeText("{}") }
        File(log.dir, "notes.txt").writeText("keep")
        assertEquals(1, log.prune(now))
        assertEquals(3, log.dir.listFiles()!!.size)
        root.deleteRecursively()
    }
}

class Co2AlertTest {
    /** 내기 순환·4명으로 1,500 ppm을 넘으면 한 번 알리고, 그대로 높으면 10분 뒤 다시 알린다. 외기로 바꾸면 알리지 않는다. */
    @Test
    fun alertsOnceThenRepeatsEveryTenMinutes() {
        val seats = com.seatroom.app.vehicle.Seat.entries.associateWith { com.seatroom.app.vehicle.SeatState(occupied = true) }
        val st = CabinState(seats = seats, recirculation = true)
        val co2 = Co2Estimator()
        val alerts = mutableListOf<Long>()
        var t = 0L
        while (t <= 40 * 60_000L) {
            co2.step(st, 5.0)
            if (co2.alertDue(t, true)) alerts += t
            t += 5_000
        }
        assertEquals(true, alerts.size >= 3)
        assertEquals(Co2Estimator.REPEAT_MS, alerts[1] - alerts[0])
        assertEquals(false, co2.alertDue(t + Co2Estimator.REPEAT_MS, false))
    }
}
