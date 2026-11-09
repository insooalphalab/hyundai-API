package com.seatroom.app.vehicle

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue

/** 시트룸이 다루는 네 개의 방. 운전석은 왼쪽 앞(국내 좌핸들) 기준. */
enum class Seat(val label: String, seat: SeatValue, door: DoorValue, window: WindowValue) {
    DRIVER("운전석", SeatValue.ROW_1_LEFT, DoorValue.ROW_1_LEFT, WindowValue.ROW_1_LEFT),
    PASSENGER("동승석", SeatValue.ROW_1_RIGHT, DoorValue.ROW_1_RIGHT, WindowValue.ROW_1_RIGHT),
    REAR_LEFT("뒷좌석 왼쪽", SeatValue.ROW_2_LEFT, DoorValue.ROW_2_LEFT, WindowValue.ROW_2_LEFT),
    REAR_RIGHT("뒷좌석 오른쪽", SeatValue.ROW_2_RIGHT, DoorValue.ROW_2_RIGHT, WindowValue.ROW_2_RIGHT);

    val seatArea = SeatArea(seat)
    val doorArea = DoorArea(door)
    val windowArea = WindowArea(window)
}

/** 방마다 지금 정해진 성격. 운전석은 운전/휴식, 나머지 자리는 기본/휴식/집중. 화면(HTML)의 키와 이름을 맞춘다. */
enum class RoomMode(val label: String, val key: String) {
    NORMAL("기본", ""), DRIVE("운전", "drive"), REST("휴식", "rest"), FOCUS("집중", "focus");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

data class SeatState(
    val occupied: Boolean? = null,
    val cabinTemp: Float? = null,
    val setTemp: Float? = null,
    val fan: Int? = null,
    val seatHeat: Int? = null,
    val seatVent: Int? = null,
    val doorPos: Int? = null,
    val windowPos: Int? = null,
)

data class CabinState(
    val seats: Map<Seat, SeatState> = Seat.entries.associateWith { SeatState() },
    val recirculation: Boolean? = null,
    val steering: Float? = null,
    val brake: Boolean? = null,
    val turnSignal: String? = null,
    val highBeamOn: Boolean? = null,
    val gear: String? = null,
    /** 운전석 자세 항목 이름 → 위치 값 */
    val posture: Map<String, Int> = emptyMap(),
) {
    fun seat(s: Seat) = seats.getValue(s)
    val occupants: Int get() = seats.values.count { it.occupied == true }
    val driving: Boolean get() = gear == "GEAR_DRIVE" || gear == "GEAR_REVERSE"
    val parked: Boolean get() = gear == "GEAR_PARK"
}

/** 차에서 들어온 신호 한 건. 기록 파일의 한 줄이 된다. */
data class Signal(val t: Long, val name: String, val area: String?, val value: String?)

/**
 * 신호 한 건을 상태에 반영한다. 가상 주행을 실제와 같은 판정 경로로 돌릴 때 쓴다
 * ([CarSignals]가 콜백마다 하는 갱신과 같은 규칙).
 */
fun CabinState.after(sig: Signal): CabinState {
    val target = sig.area?.let { a -> Seat.entries.firstOrNull { it.name == a } }
    fun seatSet(f: (SeatState) -> SeatState) = if (target == null) this else copy(seats = seats + (target to f(seat(target))))
    val v = sig.value
    return when {
        sig.name == "seat.occupancy" -> seatSet { it.copy(occupied = v?.toBooleanStrictOrNull()) }
        sig.name == "hvac.cabinTemp" -> seatSet { it.copy(cabinTemp = v?.toFloatOrNull()) }
        sig.name == "hvac.setTemp" -> seatSet { it.copy(setTemp = v?.toFloatOrNull()) }
        sig.name == "hvac.fan" -> seatSet { it.copy(fan = v?.toIntOrNull()) }
        sig.name == "door.position" -> seatSet { it.copy(doorPos = v?.toIntOrNull()) }
        sig.name == "window.position" -> seatSet { it.copy(windowPos = v?.toIntOrNull()) }
        sig.name == "hvac.recirculation" -> copy(recirculation = v?.toBooleanStrictOrNull())
        sig.name == "steering.angle" -> copy(steering = v?.toFloatOrNull())
        sig.name == "brake.pressed" -> copy(brake = v?.toBooleanStrictOrNull())
        sig.name == "turnSignal.state" -> copy(turnSignal = v)
        sig.name == "highBeam.state" -> copy(highBeamOn = v?.startsWith("ON") == true)
        sig.name == "gear.selection" -> copy(gear = v)
        sig.name.startsWith("posture.") -> {
            val key = sig.name.removePrefix("posture.")
            copy(posture = v?.toIntOrNull()?.let { posture + (key to it) } ?: (posture - key))
        }
        else -> this
    }
}
