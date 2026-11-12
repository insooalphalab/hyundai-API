package com.seatroom.app.logic

import com.seatroom.app.vehicle.Signal
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 모든 신호와 판정 결과를 날짜별 JSONL로 남긴다(차 안 저장, 본인만 열람).
 * 위치: /data/data/com.seatroom.app/files/logs/
 * 꺼내기: adb shell run-as com.seatroom.app cat files/logs/2026-11-04.signals.jsonl
 */
class SignalLog(filesDir: File) {
    val dir = File(filesDir, "logs").apply { mkdirs() }
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun signal(s: Signal) = append("signals", JSONObject().apply {
        put("t", s.t); put("name", s.name); put("area", s.area); put("value", s.value)
    })

    @Synchronized
    fun finding(f: Finding) = append("findings", JSONObject().apply {
        put("t", f.t); put("kind", f.kind.name); put("seat", f.seat); put("value", f.value); put("text", f.text)
    })

    /** [keepDays]일보다 오래된 날짜 파일을 지운다(차 안 저장 공간이 끝없이 늘지 않게). 지운 파일 수를 돌려준다. */
    @Synchronized
    fun prune(now: Long, keepDays: Int = KEEP_DAYS): Int {
        val cutoff = day.format(Date(now - keepDays * 86_400_000L))
        return (dir.listFiles() ?: return 0).count { f ->
            val d = f.name.substringBefore('.')
            f.name.endsWith(".jsonl") && d.length == 10 && d < cutoff && f.delete()
        }
    }

    private fun append(kind: String, o: JSONObject) {
        val file = File(dir, "${day.format(Date(o.getLong("t")))}.$kind.jsonl")
        file.appendText(o.toString() + "\n")
    }

    companion object { const val KEEP_DAYS = 30 }
}
