package com.seatroom.app

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차의 주행 중 사용 제한(CarUxRestrictions)을 따른다(Pleos 주행 안전 가이드).
 * 제한이 걸리면 화면을 "주행 중 보기"로 줄인다. 차 API가 없으면(차 밖 기기) 제한 없음으로 본다.
 */
class DrivingRestriction(context: Context) {
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted

    private var car: Car? = null

    init {
        try {
            val c = Car.createCar(context.applicationContext)
            car = c
            val mgr = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _restricted.value = mgr.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            mgr.registerListener { r -> _restricted.value = r.isRequiresDistractionOptimization }
        } catch (t: Throwable) {
            car = null
        }
    }

    fun release() {
        runCatching { car?.disconnect() }
    }
}
