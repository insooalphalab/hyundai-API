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

    companion object {
        const val OUTDOOR = 420.0
        const val PER_PERSON = 1.2
        const val RECIRC_ACH = 6.0
        const val ALERT = 1500.0
    }
}
