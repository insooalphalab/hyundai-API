package com.seatroom.app.logic

import com.seatroom.app.vehicle.CabinState

/**
 * 센서 없이 실내 CO₂를 추정한다: 인원 × 내기 순환 × 바람 × 시간의 질량 수지.
 *   dC/dt = n·g − λ·(C − C_out)
 * g ≈ 1.2 ppm/s/인 (문헌: 내기 순환 시 1명 1.43, 3명 3.52 ppm/s 상승)
 * λ: 내기 순환 시간당 약 6회(차종별 3~23회로 편차 큼 → 실측 보정 전 가정값), 외기 유입 시 바람 세기에 비례.
 */
class Co2Estimator {
    var ppm = OUTDOOR
        private set

    fun step(state: CabinState, dtSec: Double): Double {
        val n = state.occupants.coerceAtLeast(if (state.driving) 1 else 0)
        val fan = state.seat(com.seatroom.app.vehicle.Seat.DRIVER).fan ?: 2
        val airChangesPerHour = if (state.recirculation == true) RECIRC_ACH else 20.0 + 8.0 * fan
        val lambda = airChangesPerHour / 3600.0
        ppm += dtSec * (n * PER_PERSON - lambda * (ppm - OUTDOOR))
        ppm = ppm.coerceIn(OUTDOOR, 6000.0)
        return ppm
    }

    private var alertedAt: Long? = null

    /**
     * 지금 알릴 차례인지. 내기 순환인 채로 [ALERT]를 넘으면 한 번, 그대로 높으면 [REPEAT_MS]마다 다시.
     * [ALERT] 아래로 내려가면 처음부터 다시 센다.
     */
    fun alertDue(now: Long, recirculation: Boolean): Boolean {
        if (ppm < ALERT) { alertedAt = null; return false }
        if (!recirculation) return false
        val last = alertedAt
        if (last != null && now - last < REPEAT_MS) return false
        alertedAt = now
        return true
    }

    companion object {
        const val REPEAT_MS = 10 * 60_000L
        const val OUTDOOR = 420.0
        const val PER_PERSON = 1.2
        const val RECIRC_ACH = 6.0
        const val ALERT = 1500.0
    }
}
