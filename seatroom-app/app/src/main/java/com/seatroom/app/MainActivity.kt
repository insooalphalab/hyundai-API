package com.seatroom.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.seatroom.app.vehicle.RoomMode
import com.seatroom.app.vehicle.Seat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 화면은 design/seatroom-screens.html 하나(빌드 때 assets/seatroom.html로 복사).
 * 차 신호는 0.5초마다 window.onCar(json)으로 넘기고, 화면의 조작은 window.Android.* 로 받는다.
 */
class MainActivity : ComponentActivity() {
    private lateinit var restriction: DrivingRestriction
    private lateinit var web: WebView
    private lateinit var engine: SeatRoomEngine

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        CabinService.start(this)
        engine = SeatRoomEngine.get(this)
        restriction = DrivingRestriction(this)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/seatroom.html")
        }
        setContentView(web)

        lifecycleScope.launch {
            var sent = ""
            while (true) {
                delay(500)
                val json = stateJson().toString()
                if (json != sent) {
                    sent = json
                    web.evaluateJavascript("window.onCar && window.onCar($json)", null)
                }
            }
        }
    }

    private fun stateJson(): JSONObject {
        val st = engine.car.state.value
        fun perSeat(f: (Seat) -> Any?) = JSONObject().apply { Seat.entries.forEach { s -> put(s.name, f(s) ?: JSONObject.NULL) } }
        return JSONObject().apply {
            put("gear", st.gear ?: JSONObject.NULL)
            put("driving", st.driving)
            put("restricted", restriction.restricted.value)
            put("occ", perSeat { st.seat(it).occupied })
            put("setTemp", perSeat { st.seat(it).setTemp?.takeIf { t -> t > 5f } })
            put("fan", perSeat { st.seat(it).fan })
            put("fixes", perSeat { engine.fixes.value[it] })
            put("recirc", st.recirculation ?: JSONObject.NULL)
            put("co2", engine.co2Ppm.value)
            put("restLeftMs", (engine.restUntil.value - System.currentTimeMillis()).coerceAtLeast(0))
            put("problems", JSONArray(engine.car.problems.value))
            put("guest", perSeat { s ->
                engine.guestSignals.value[s]?.let { g ->
                    JSONObject().apply { put("firstTemp", g.firstTemp ?: JSONObject.NULL); put("moved", g.moved); put("seen30", g.seen30) }
                }
            })
            engine.lastTrip.value?.let { t ->
                put("trip", JSONObject().apply {
                    put("t", t.t)
                    put("minutes", t.minutes)
                    put("v", JSONObject().apply { t.values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } })
                    put("base", JSONObject().apply {
                        t.base.forEach { (k, b) ->
                            put(k, JSONObject().apply { put("m", b.mean ?: JSONObject.NULL); put("sd", b.sd ?: JSONObject.NULL); put("n", b.n) })
                        }
                    })
                    put("heat", t.heat)
                })
            }
        }
    }

    /** 화면(HTML)에서 부르는 함수. WebView 스레드에서 오므로 메인으로 넘긴다. */
    inner class Bridge {
        @JavascriptInterface
        fun setMode(seat: String, mode: String, temp: Double, fan: Int) = main {
            Seat.entries.firstOrNull { it.name == seat }?.let { engine.setMode(it, RoomMode.of(mode), temp.toFloat(), fan) }
        }

        @JavascriptInterface
        fun setRecirc(on: String) = main { engine.setRecirculation(on == "true") }

        /** 화면이 탑승자별로 정리한 리포트 요약(제목, 사람마다 한 줄). */
        @JavascriptInterface
        fun sendReport(title: String, body: String, warn: String) = main { engine.sendReport(title, body.lines().filter { it.isNotBlank() }, warn == "true") }

        /** 지금 운전하는 사람(시나리오·이름). 실제 주행은 이 사람의 기록에 쌓이고 기준선도 이 사람 것만 쓴다. */
        @JavascriptInterface
        fun setDriver(key: String) = main { engine.selectProfile(key) }

        @JavascriptInterface
        fun notify(kind: String, title: String, body: String) = main {
            engine.notifier.message(if (kind == "trend") Notifier.ID_TREND else Notifier.ID_TRIP, title, body)
        }

        private fun main(block: () -> Unit) = runOnUiThread(block)
    }

    override fun onDestroy() {
        restriction.release()
        web.destroy()
        super.onDestroy()
    }
}
