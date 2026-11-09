package com.seatroom.app.logic

import com.seatroom.app.vehicle.RoomMode
import com.seatroom.app.vehicle.Seat

/**
 * 운전자 한 사람의 "평소" 모습. 가상 주행([SyntheticDrive])이 이 값으로 신호를 만든다.
 * [driftPerDay]: 하루마다 평소가 조금씩 옮겨 가는 정도(고령 운전자의 느린 변화). 마지막 [driftDays]일에만 적용.
 */
data class DriverStyle(
    val reversalsPerMin: Double = 12.0,
    val reversalsSd: Double = 1.2,
    val postureLambda: Double = 1.2,
    val nightProb: Double = 0.35,
    val highBeamMin: Double = 1.0,
    val turnLead: Double = 3.0,
    val turnLeadSd: Double = 0.5,
    val boardingSec: Double = 3.5,
    val passengerElder: Boolean = true,
    /** 더운데도 온도를 안 낮추는 경향(열 감각 둔화) */
    val heatInsensitive: Boolean = false,
    /** 운전자 자동 구분용 좌석 위치(앞뒤, 높이) */
    val seatForeAft: Int = 50,
    val seatHeight: Int = 40,
    val driftDays: Int = 0,
    val driftPerDay: Double = 0.0,
)

/**
 * 알림 규칙. 같은 "다른 날"이라도 시나리오에 따라 알림이 달라진다.
 * [zThreshold]: 평소에서 몇 σ 벗어나면 "달라요"로 볼지. [minAxes]: 축 몇 개가 동시에 달라야 알림을 보낼지.
 */
data class AlertPolicy(
    val zThreshold: Double = 2.0,
    val minAxes: Int = 1,
    val trend: Boolean = false,
    val heatAlert: Boolean = false,
    /** 하루 비교에 쓸 최근 주행 수. 짧게 잡으면 느린 변화는 하루 알림이 아니라 추세로만 잡힌다. */
    val window: Int = 30,
    val note: String,
)

data class Profile(val name: String, val style: DriverStyle, val share: Double)

data class Scenario(
    val id: String,
    val title: String,
    val summary: String,
    val occupied: Set<Seat>,
    val modes: Map<Seat, RoomMode>,
    val profiles: List<Profile>,
    val policy: AlertPolicy,
    val autoIdentify: Boolean = false,
) {
    fun key(profile: String) = "$id:$profile"
}

object Scenarios {
    val SAFE_FAMILY = Scenario(
        id = "S1",
        title = "가족 · 10만 km 넘은 안전운전자",
        summary = "운전이 매우 꾸준한 아빠가 운전하고, 뒷좌석 아이 둘은 자요. 평소 범위가 좁아 작은 흔들림에도 반응하기 쉬우니, 축 2개 이상이 함께 달라질 때만 알려요.",
        occupied = setOf(Seat.DRIVER, Seat.PASSENGER, Seat.REAR_LEFT, Seat.REAR_RIGHT),
        modes = mapOf(Seat.DRIVER to RoomMode.DRIVE, Seat.REAR_LEFT to RoomMode.REST, Seat.REAR_RIGHT to RoomMode.REST),
        profiles = listOf(
            Profile("아빠", DriverStyle(reversalsPerMin = 10.5, reversalsSd = 0.6, postureLambda = 0.6, nightProb = 0.2, highBeamMin = 0.8, turnLead = 3.4, turnLeadSd = 0.3, passengerElder = false, seatForeAft = 55, seatHeight = 35), 1.0),
        ),
        policy = AlertPolicy(zThreshold = 2.0, minAxes = 2, note = "축 2개 이상이 동시에 ±2σ를 벗어날 때만 알림 (꾸준한 운전자의 거짓 알림 줄이기)"),
    )

    val SENIOR = Scenario(
        id = "S2",
        title = "개인 · 고령 운전자",
        summary = "혼자 운전하는 어르신. 시야·계획이 몇 주에 걸쳐 천천히 달라지고, 더위를 덜 느껴요. 축 1개만 달라져도 알리고, 몇 주 추세와 더위 무반응도 알려요.",
        occupied = setOf(Seat.DRIVER),
        modes = emptyMap(),
        profiles = listOf(
            Profile("어르신", DriverStyle(reversalsPerMin = 13.0, reversalsSd = 1.4, postureLambda = 1.5, nightProb = 0.3, highBeamMin = 2.5, turnLead = 3.6, turnLeadSd = 0.6, boardingSec = 8.5, passengerElder = false, heatInsensitive = true, seatForeAft = 40, seatHeight = 55, driftDays = 14, driftPerDay = 0.03), 1.0),
        ),
        policy = AlertPolicy(zThreshold = 2.3, minAxes = 1, trend = true, heatAlert = true, window = 10, note = "축 1개라도 ±2.3σ면 알림(최근 10회와 비교) + 몇 주 추세는 따로 + 더운데 조작 없음 알림"),
    )

    val TWO_DRIVERS = Scenario(
        id = "S3",
        title = "가족 · 2인, 번갈아 운전",
        summary = "두 사람이 번갈아 운전해요. 운전 습관과 좌석 위치가 달라서, 좌석 위치로 누가 운전하는지 자동으로 구분하고 각자의 평소와만 비교해요.",
        occupied = setOf(Seat.DRIVER, Seat.PASSENGER),
        modes = mapOf(Seat.DRIVER to RoomMode.DRIVE),
        profiles = listOf(
            Profile("지수", DriverStyle(reversalsPerMin = 9.5, reversalsSd = 0.9, postureLambda = 0.8, nightProb = 0.4, highBeamMin = 0.7, turnLead = 3.2, passengerElder = false, seatForeAft = 70, seatHeight = 30), 0.55),
            Profile("민호", DriverStyle(reversalsPerMin = 15.0, reversalsSd = 1.1, postureLambda = 2.0, nightProb = 0.3, highBeamMin = 1.4, turnLead = 2.4, passengerElder = false, seatForeAft = 35, seatHeight = 50), 0.45),
        ),
        policy = AlertPolicy(zThreshold = 2.0, minAxes = 1, note = "운전자별 평소와 비교, 축 1개라도 ±2σ면 알림"),
        autoIdentify = true,
    )

    val all = listOf(SAFE_FAMILY, SENIOR, TWO_DRIVERS)
}
