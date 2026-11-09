package com.seatroom.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * 주행 중에는 화면 대신 알림으로만 말한다(짧은 문장, 버튼 하나).
 * - 상태: 계속 떠 있는 조용한 알림(서비스 유지용)
 * - 경고: CO₂ 추정 높음, 졸음 자구책 → 차 화면 위에 잠깐 뜨는 알림
 * - 평소의 나: 주행이 끝난 뒤(주차)에만
 */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "시트룸 상태", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "시트룸 알림", NotificationManager.IMPORTANCE_HIGH))
    }

    fun status(): Notification = Notification.Builder(context, CH_STATUS)
        .setSmallIcon(R.drawable.ic_stat_seatroom)
        .setContentTitle("시트룸")
        .setContentText("좌석마다 차 안 상태를 보고 있어요")
        .setContentIntent(openApp())
        .setOngoing(true)
        .build()

    fun co2(ppm: Double) = alert(
        ID_CO2, "실내 공기가 답답해질 수 있어요",
        "CO₂ 추정 %,.0f ppm. 잠깐 외기로 바꿀까요?".format(ppm),
        Notification.Action.Builder(null, "외기로", freshAirIntent()).build(),
    )

    fun drowsy() = alert(ID_DROWSY, "잠깐 쉬어 가면 어떨까요", "창문·찬바람의 효과는 보통 15분 안팎이에요.")

    fun tripSummary(axes: List<String>) = alert(
        ID_TRIP, "오늘 운전이 평소와 조금 달랐어요", "${axes.joinToString("·")} — 앱에서 확인해 보세요.",
    )

    fun reportSent() = alert(ID_REPORT, "휴대폰으로 보고서를 보냈어요", "주행 기록·평소의 나 그래프·방에서 읽은 신호를 휴대폰에서 자세히 볼 수 있어요.")

    fun trend(axes: List<String>) = alert(
        ID_TREND, "몇 주에 걸쳐 운전이 조금씩 달라지고 있어요", "${axes.joinToString("·")} — 하루가 아니라 추세예요. 앱에서 확인해 보세요.",
    )

    /** 화면이 정한 제목·본문 그대로. 본문 여러 줄은 펼쳐서 보인다. */
    fun message(id: Int, title: String, body: String) = alert(id, title, body)

    private fun alert(id: Int, title: String, text: String, action: Notification.Action? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val b = Notification.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_seatroom)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp())
            .setAutoCancel(true)
        action?.let { b.addAction(it) }
        nm.notify(id, b.build())
    }

    private fun openApp() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun freshAirIntent() = PendingIntent.getBroadcast(
        context, 1, Intent(context, FreshAirReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CH_STATUS = "status"
        const val CH_ALERT = "alert"
        const val ID_STATUS = 1
        const val ID_CO2 = 2
        const val ID_DROWSY = 3
        const val ID_TRIP = 4
        const val ID_TREND = 5
        const val ID_REPORT = 6
    }
}

/** 알림의 "외기로" 버튼. */
class FreshAirReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SeatRoomEngine.get(context).freshAir()
        context.getSystemService(NotificationManager::class.java).cancel(Notifier.ID_CO2)
    }
}
