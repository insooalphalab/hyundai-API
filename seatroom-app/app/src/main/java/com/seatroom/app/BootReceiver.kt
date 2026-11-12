package com.seatroom.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 시동(부팅)과 함께 [CabinService]를 켠다. 앱을 한 번도 열지 않은 날의 주행도 평소의 나에 쌓이게.
 * 시스템이 백그라운드 시작을 막는 차·이미지에서는 조용히 넘어가고, 앱을 열 때 시작된다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            CabinService.start(context)
        } catch (e: Exception) {
            Log.w("SeatRoom", "부팅 시 서비스 시작 실패: ${e.message}")
        }
    }
}
