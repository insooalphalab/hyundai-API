package com.seatroom.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatDepthPositionListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeadrestHeightAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeightPositionListener
import ai.pleos.playground.vehicle.listener.SeatLumbarForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SeatTiltPositionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK의 콜백을 한곳에 모은다.
 * 모든 콜백은 [events]로 흘려 기록·판정 로직이 쓰고, 최신 값은 [state]로 화면이 쓴다.
 * 차종마다 지원하는 신호가 달라서, 등록이 실패한 신호는 [problems]에 남기고 넘어간다.
 */
class CarSignals(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val hvac = vehicle.getHVAC()
    private val seatApi = vehicle.getSeat()
    private val door = vehicle.getDoor()
    private val window = vehicle.getWindow()
    private val light = vehicle.getLight()
    private val steeringWheel = vehicle.getSteeringWheel()
    private val brake = vehicle.getBrake()
    private val turnSignal = vehicle.getTurnSignal()
    private val gear = vehicle.getGear()

    private val _events = MutableSharedFlow<Signal>(extraBufferCapacity = 512)
    val events: SharedFlow<Signal> = _events

    private val _state = MutableStateFlow(CabinState())
    val state: StateFlow<CabinState> = _state

    private val _problems = MutableStateFlow<List<String>>(emptyList())
    val problems: StateFlow<List<String>> = _problems

    private val unregisters = mutableListOf<() -> Unit>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            problem("SDK 초기화", e)
            return
        }
        Seat.entries.forEach { registerSeat(it) }
        registerDriving()
        registerPosture()
    }

    fun stop() {
        if (!started) return
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        runCatching { vehicle.release() }
        started = false
    }

    // ---------- 좌석(방)별 신호 ----------

    private fun registerSeat(s: Seat) {
        val area = s.seatArea
        safe("좌석 점유 ${s.label}") {
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                    seatUpdate(s) { it.copy(occupied = occupancy) }
                    emit("seat.occupancy", s, occupancy)
                }
                override fun onFailed(e: Exception) = problem("좌석 점유 ${s.label}", e)
            }
            seatApi.registerSeatOccupancy(area, l)
            unregisters += { seatApi.unregisterSeatOccupancy(area, l) }
        }
        safe("실내 온도 ${s.label}") {
            val l = object : HvacTemperatureListener {
                override fun onHvacTemperatureUpdated(temperature: Float?) {
                    seatUpdate(s) { it.copy(cabinTemp = temperature) }
                    emit("hvac.cabinTemp", s, temperature)
                }
                override fun onFailed(e: Exception) = problem("실내 온도 ${s.label}", e)
            }
            hvac.registerHvacTemperatureCurrent(area, l)
            unregisters += { hvac.unregisterHvacTemperatureCurrent(area, l) }
        }
        safe("설정 온도 ${s.label}") {
            val l = object : HvacTemperatureListener {
                override fun onHvacTemperatureUpdated(temperature: Float?) {
                    seatUpdate(s) { it.copy(setTemp = temperature) }
                    emit("hvac.setTemp", s, temperature)
                }
                override fun onFailed(e: Exception) = problem("설정 온도 ${s.label}", e)
            }
            hvac.registerHvacTemperature(area, l)
            unregisters += { hvac.unregisterHvacTemperature(area, l) }
        }
        safe("바람 세기 ${s.label}") {
            val l = object : HvacFanSpeedListener {
                override fun onHvacFanSpeedUpdated(speed: Int?) {
                    seatUpdate(s) { it.copy(fan = speed) }
                    emit("hvac.fan", s, speed)
                }
                override fun onFailed(e: Exception) = problem("바람 세기 ${s.label}", e)
            }
            hvac.registerHvacFanSpeed(area, l)
            unregisters += { hvac.unregisterHvacFanSpeed(area, l) }
        }
        safe("열선 ${s.label}") {
            val l = object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
                    seatUpdate(s) { it.copy(seatHeat = temperature) }
                    emit("seat.heat", s, temperature)
                }
                override fun onFailed(e: Exception) = problem("열선 ${s.label}", e)
            }
            seatApi.registerHvacSeatTemperature(area, l)
            unregisters += { seatApi.unregisterHvacSeatTemperature(area, l) }
        }
        safe("통풍 ${s.label}") {
            val l = object : HvacSeatVentilationListener {
                override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
                    seatUpdate(s) { it.copy(seatVent = ventilation) }
                    emit("seat.vent", s, ventilation)
                }
                override fun onFailed(e: Exception) = problem("통풍 ${s.label}", e)
            }
            seatApi.registerHvacSeatVentilation(area, l)
            unregisters += { seatApi.unregisterHvacSeatVentilation(area, l) }
        }
        safe("문 ${s.label}") {
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    seatUpdate(s) { it.copy(doorPos = pos) }
                    emit("door.position", s, pos)
                }
                override fun onFailed(e: Exception) = problem("문 ${s.label}", e)
            }
            door.registerDoorPosition(s.doorArea, l)
            unregisters += { door.unregisterDoorPosition(s.doorArea, l) }
        }
        safe("창문 ${s.label}") {
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(position: Int?) {
                    seatUpdate(s) { it.copy(windowPos = position) }
                    emit("window.position", s, position)
                }
                override fun onFailed(e: Exception) = problem("창문 ${s.label}", e)
            }
            window.registerWindowPosition(s.windowArea, l)
            unregisters += { window.unregisterWindowPosition(s.windowArea, l) }
        }
        // 운전석 위치는 registerPosture가 받는다. 나머지 자리는 손님 판정(탑승 뒤 좌석을 옮겼는지)에 쓴다.
        if (s == Seat.DRIVER) return
        safe("좌석 앞뒤 ${s.label}") {
            val l = object : SeatForeAftPositionListener {
                override fun onSeatForeAftPositionUpdated(position: Int?) = emit("seat.foreAft", s, position)
                override fun onFailed(e: Exception) = problem("좌석 앞뒤 ${s.label}", e)
            }
            seatApi.registerSeatForeAftPosition(area, l)
            unregisters += { seatApi.unregisterSeatForeAftPosition(area, l) }
        }
        safe("좌석 높이 ${s.label}") {
            val l = object : SeatHeightPositionListener {
                override fun onSeatHeightPositionUpdated(position: Int?) = emit("seat.height", s, position)
                override fun onFailed(e: Exception) = problem("좌석 높이 ${s.label}", e)
            }
            seatApi.registerSeatHeightPosition(area, l)
            unregisters += { seatApi.unregisterSeatHeightPosition(area, l) }
        }
    }

    // ---------- 운전 행동 신호 ----------

    private fun registerDriving() {
        safe("내기 순환") {
            val area = Seat.DRIVER.seatArea
            val l = object : HvacRecirculationStateListener {
                override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
                    _state.update { it.copy(recirculation = state) }
                    emit("hvac.recirculation", null, state)
                }
                override fun onFailed(e: Exception) = problem("내기 순환", e)
            }
            hvac.registerHvacRecirculationState(area, l)
            unregisters += { hvac.unregisterHvacRecirculationState(area, l) }
        }
        safe("조향각") {
            val l = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) {
                    _state.update { it.copy(steering = angle) }
                    emit("steering.angle", null, angle)
                }
                override fun onFailed(e: Exception) = problem("조향각", e)
            }
            steeringWheel.registerSteeringWheelAngle(l)
            unregisters += { steeringWheel.unregisterSteeringWheelAngle(l) }
        }
        safe("브레이크") {
            val l = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) {
                    _state.update { it.copy(brake = status) }
                    emit("brake.pressed", null, status)
                }
                override fun onFailed(e: Exception) = problem("브레이크", e)
            }
            brake.registerBrakePedalStatus(l)
            unregisters += { brake.unregisterBrakePedalStatus(l) }
        }
        safe("방향지시등") {
            val l = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                    _state.update { it.copy(turnSignal = state?.name) }
                    emit("turnSignal.state", null, state?.name)
                }
                override fun onFailed(e: Exception) = problem("방향지시등", e)
            }
            turnSignal.registerTurnSignalState(l)
            unregisters += { turnSignal.unregisterTurnSignalState(l) }
        }
        safe("상향등") {
            val l = object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) {
                    val on = state == LightState.ON
                    // 자동 상향등이 켠 것은 시야 보상에서 뺀다: 스위치 상태를 함께 기록
                    light.getHighBeamLightSwitch(
                        onComplete = { sw -> emit("highBeam.state", null, "${state?.name}|${sw?.name}") },
                        onFailed = { emit("highBeam.state", null, "${state?.name}|UNKNOWN") }
                    )
                    _state.update { it.copy(highBeamOn = on) }
                }
                override fun onFailed(e: Exception) = problem("상향등", e)
            }
            light.registerHighBeamLightState(l)
            unregisters += { light.unregisterHighBeamLightState(l) }
        }
        safe("기어") {
            val l = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    _state.update { it.copy(gear = gear?.name) }
                    emit("gear.selection", null, gear?.name)
                }
                override fun onFailed(e: Exception) = problem("기어", e)
            }
            gear.registerGearSelection(l)
            unregisters += { gear.unregisterGearSelection(l) }
        }
    }

    // ---------- 운전석 자세(좌석 위치) ----------

    private fun registerPosture() {
        val area = Seat.DRIVER.seatArea
        fun posture(key: String, v: Int?) {
            _state.update { st -> st.copy(posture = if (v == null) st.posture - key else st.posture + (key to v)) }
            emit("posture.$key", Seat.DRIVER, v)
        }
        safe("좌석 앞뒤") {
            val l = object : SeatForeAftPositionListener {
                override fun onSeatForeAftPositionUpdated(position: Int?) = posture("foreAft", position)
                override fun onFailed(e: Exception) = problem("좌석 앞뒤", e)
            }
            seatApi.registerSeatForeAftPosition(area, l)
            unregisters += { seatApi.unregisterSeatForeAftPosition(area, l) }
        }
        safe("좌석 높이") {
            val l = object : SeatHeightPositionListener {
                override fun onSeatHeightPositionUpdated(position: Int?) = posture("height", position)
                override fun onFailed(e: Exception) = problem("좌석 높이", e)
            }
            seatApi.registerSeatHeightPosition(area, l)
            unregisters += { seatApi.unregisterSeatHeightPosition(area, l) }
        }
        safe("등받이 각도") {
            val l = object : SeatBackrestAnglePositionListener {
                override fun onSeatBackrestAnglePositionUpdated(position: Int?) = posture("backrest", position)
                override fun onFailed(e: Exception) = problem("등받이 각도", e)
            }
            seatApi.registerSeatBackrestAnglePosition(area, l)
            unregisters += { seatApi.unregisterSeatBackrestAnglePosition(area, l) }
        }
        safe("좌석 기울기") {
            val l = object : SeatTiltPositionListener {
                override fun onSeatTiltPositionUpdated(position: Int?) = posture("tilt", position)
                override fun onFailed(e: Exception) = problem("좌석 기울기", e)
            }
            seatApi.registerSeatTiltPosition(area, l)
            unregisters += { seatApi.unregisterSeatTiltPosition(area, l) }
        }
        safe("허리 받침") {
            val l = object : SeatLumbarForeAftPositionListener {
                override fun onSeatLumbarForeAftPositionUpdated(position: Int?) = posture("lumbar", position)
                override fun onFailed(e: Exception) = problem("허리 받침", e)
            }
            seatApi.registerSeatLumbarForeAftPosition(area, l)
            unregisters += { seatApi.unregisterSeatLumbarForeAftPosition(area, l) }
        }
        safe("쿠션 깊이") {
            val l = object : SeatDepthPositionListener {
                override fun onSeatDepthPositionUpdated(position: Int?) = posture("depth", position)
                override fun onFailed(e: Exception) = problem("쿠션 깊이", e)
            }
            seatApi.registerSeatDepthPosition(area, l)
            unregisters += { seatApi.unregisterSeatDepthPosition(area, l) }
        }
        safe("헤드레스트 높이") {
            val l = object : SeatHeadrestHeightAftPositionListener {
                override fun onSeatHeadrestHeightAftPositionUpdated(position: Int?) = posture("headrest", position)
                override fun onFailed(e: Exception) = problem("헤드레스트 높이", e)
            }
            seatApi.registerSeatHeadrestHeightAftPosition(area, l)
            unregisters += { seatApi.unregisterSeatHeadrestHeightAftPosition(area, l) }
        }
    }

    // ---------- 방 꾸미기(제어) ----------

    fun setTemperature(s: Seat, celsius: Float, done: (String) -> Unit) = control(done, "${s.label} 온도 ${celsius}°C") { ok, fail ->
        hvac.setHvacTemperature(s.seatArea, celsius, TemperatureUnit.CELSIUS, { _, _ -> ok() }, fail)
    }

    fun setFan(s: Seat, level: Int, done: (String) -> Unit) = control(done, "${s.label} 바람 $level") { ok, fail ->
        hvac.setHvacFanSpeed(s.seatArea, level, { _, _ -> ok() }, fail)
    }

    fun setSeatHeat(s: Seat, level: Int, done: (String) -> Unit) = control(done, "${s.label} 열선 $level") { ok, fail ->
        seatApi.setHvacSeatTemperature(s.seatArea, level, { _, _ -> ok() }, fail)
    }

    fun setSeatVent(s: Seat, level: Int, done: (String) -> Unit) = control(done, "${s.label} 통풍 $level") { ok, fail ->
        seatApi.setHvacSeatVentilation(s.seatArea, level, { _, _ -> ok() }, fail)
    }

    fun setAmbient(s: Seat, brightness: Int, done: (String) -> Unit) = control(done, "${s.label} 조명 $brightness") { ok, fail ->
        light.setAmbientLightBrightness(s.seatArea, brightness, { _, _ -> ok() }, fail)
    }

    fun setRecirculation(on: Boolean, done: (String) -> Unit) = control(done, if (on) "내기 순환" else "외기 유입") { ok, fail ->
        hvac.setHvacRecirculation(Seat.DRIVER.seatArea, on, { _, _ -> ok() }, fail)
    }

    private fun control(
        done: (String) -> Unit,
        what: String,
        call: (ok: () -> Unit, fail: (Exception) -> Unit) -> Unit,
    ) {
        try {
            call({ done("$what ✓") }, { e -> done("$what 실패: ${e.message}") })
        } catch (e: Exception) {
            done("$what 실패: ${e.message}")
        }
    }

    // ---------- 공통 ----------

    private fun seatUpdate(s: Seat, f: (SeatState) -> SeatState) =
        _state.update { st -> st.copy(seats = st.seats + (s to f(st.seat(s)))) }

    private fun emit(name: String, seat: Seat?, value: Any?) {
        _events.tryEmit(Signal(System.currentTimeMillis(), name, seat?.name, value?.toString()))
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            problem(what, e)
        }
    }

    private fun problem(what: String, e: Exception) {
        Log.w(TAG, "$what: ${e.message}")
        _problems.update { (it + "$what: ${e.message ?: e.javaClass.simpleName}").takeLast(40) }
    }

    private companion object {
        const val TAG = "SeatRoom"
    }
}
