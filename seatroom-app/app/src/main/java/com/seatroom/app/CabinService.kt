package com.seatroom.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 화면이 주행 중 제한으로 가려지거나 다른 앱으로 넘어가도 신호 수집·판정·알림을 계속한다.
 */
class CabinService : Service() {

    override fun onCreate() {
        super.onCreate()
        val engine = SeatRoomEngine.get(this)
        val n = engine.notifier.status()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_STATUS, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, CabinService::class.java))
        }
    }
}
