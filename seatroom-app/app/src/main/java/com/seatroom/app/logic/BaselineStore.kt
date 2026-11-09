package com.seatroom.app.logic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/** 평소의 나 4축. */
/**
 * [minSd]: 표준편차 하한. 자세처럼 횟수가 작은 지표는 평소 편차가 거의 0이라 한두 번 차이로도
 * 알림이 뜬다 → "이 정도는 같은 날로 본다"는 최소 폭을 둔다(가정값).
 */
enum class Axis(val label: String, val question: String, val unit: String, val minSd: Double) {
    PERFORMANCE("수행", "조향 방향 전환(분당)", "회/분", 0.8),
    POSTURE("자세", "주행 중 자세 고침", "회", 1.2),
    VISION("시야 보상", "수동 상향등 시간", "분", 0.5),
    PLANNING("계획", "깜빡이 → 조향 간격", "초", 0.4),
}

data class AxisReading(
    val axis: Axis,
    val today: Double?,
    val baseline: Double?,
    val sd: Double?,
    val n: Int,
    val threshold: Double = 2.0,
) {
    val ready: Boolean get() = n >= MIN_TRIPS && baseline != null
    val z: Double? get() = if (!ready || today == null || sd == null || sd < 1e-6) null else (today - baseline!!) / sd
    val changed: Boolean get() = (z?.let { abs(it) >= threshold }) == true

    /** 경고가 아니라 "평소와 달라졌어요" 알림. 원인은 질문으로만. */
    val message: String
        get() = when {
            today == null -> "이번 주행에서는 측정되지 않았어요"
            !ready -> "평소를 알아 가는 중이에요 ($n/${MIN_TRIPS}회)"
            !changed -> "평소 범위예요"
            else -> when (axis) {
                Axis.PERFORMANCE -> "핸들 보정이 평소와 달라요. 피곤하신가요?"
                Axis.POSTURE -> "운전 중 자세를 평소보다 자주 고쳤어요. 허리가 불편하신가요?"
                Axis.VISION -> "상향등을 평소보다 오래 켰어요. 밤길이 잘 보이시나요?"
                Axis.PLANNING -> "회전할 때 조작 순서가 평소와 달라요."
            }
        }

    companion object { const val MIN_TRIPS = 10 }
}

data class TripSummary(val start: Long, val minutes: Double, val values: Map<Axis, Double?>, val countermeasures: Int, val synthetic: Boolean)

/**
 * 사람(프로필)별로 주행 요약을 쌓고, 지난 기록으로 평소의 나를 만든다.
 * 비교 대상은 항상 그 사람 자신의 지난 주행뿐(타인·나이 비교 없음).
 */
class BaselineStore(context: Context) {
    private val prefs = context.getSharedPreferences("baseline", Context.MODE_PRIVATE)

    fun record(profile: String, m: TripMetrics, synthetic: Boolean = false) {
        val arr = load(profile)
        arr.put(JSONObject().apply {
            put("start", m.start)
            put("synthetic", synthetic)
            put("min", m.minutes)
            m.reversalsPerMin?.let { put(Axis.PERFORMANCE.name, it) }
            put(Axis.POSTURE.name, m.postureChanges)
            put(Axis.VISION.name, m.highBeamManualMs / 60000.0)
            m.medianTurnLead?.let { put(Axis.PLANNING.name, it / 1000.0) }
            put("orderFlips", m.orderFlips)
            put("countermeasures", m.countermeasures)
            m.backrestAtEnd?.let { put("backrest", it) }
            m.seatForeAft?.let { put("foreAft", it) }
            m.seatHeight?.let { put("height", it) }
        })
        val trimmed = JSONArray()
        sorted(arr).takeLast(MAX_TRIPS).forEach { trimmed.put(it) }
        prefs.edit().putString(profile, trimmed.toString()).apply()
    }

    fun clear(profile: String) {
        prefs.edit().remove(profile).apply()
    }

    /** 최근 주행부터, 화면의 "주행 기록" 목록용. */
    fun history(profile: String): List<TripSummary> {
        return sorted(load(profile)).reversed().map { o ->
            TripSummary(
                start = o.optLong("start"),
                minutes = o.optDouble("min"),
                values = Axis.entries.associateWith { o.optDoubleOrNull(it.name) },
                countermeasures = o.optInt("countermeasures"),
                synthetic = o.optBoolean("synthetic"),
            )
        }
    }

    /** 가장 최근 주행을 "오늘", 그 이전 주행들을 "평소"로 본다. */
    fun readings(profile: String, threshold: Double = 2.0, window: Int = WINDOW, realOnly: Boolean = false): List<AxisReading> {
        val trips = sorted(load(profile)).filter { !realOnly || !it.optBoolean("synthetic") }
        val last = trips.lastOrNull()
        val past = trips.dropLast(1).takeLast(window)
        return Axis.entries.map { ax ->
            val vals = past.mapNotNull { it.optDoubleOrNull(ax.name) }
            val mean = vals.takeIf { it.isNotEmpty() }?.average()
            val sd = if (vals.size >= 2 && mean != null) maxOf(ax.minSd, sqrt(vals.sumOf { (it - mean) * (it - mean) } / (vals.size - 1))) else null
            AxisReading(ax, last?.optDoubleOrNull(ax.name), mean, sd, vals.size, threshold)
        }
    }

    /**
     * 느린 변화: 최근 [recent]회 평균을 그 이전 평소와 비교(평균의 표준오차 기준).
     * 하루 단위로는 범위 안이어도 몇 주에 걸쳐 한쪽으로 옮겨 가면 잡는다.
     */
    fun trend(profile: String, recent: Int = 7, threshold: Double = 2.5): List<Pair<Axis, Double>> {
        val trips = sorted(load(profile))
        if (trips.size < recent + MIN_PAST_FOR_TREND) return emptyList()
        val last = trips.takeLast(recent)
        val past = trips.dropLast(recent).takeLast(WINDOW)
        return Axis.entries.mapNotNull { ax ->
            val p = past.mapNotNull { it.optDoubleOrNull(ax.name) }
            val r = last.mapNotNull { it.optDoubleOrNull(ax.name) }
            if (p.size < 5 || r.size < 3) return@mapNotNull null
            val mean = p.average()
            val sd = maxOf(ax.minSd, sqrt(p.sumOf { (it - mean) * (it - mean) } / (p.size - 1)))
            val z = (r.average() - mean) / (sd / sqrt(r.size.toDouble()))
            if (abs(z) >= threshold) ax to (r.average() - mean) else null
        }
    }

    /** 이 사람이 운전할 때의 평균 좌석 위치(앞뒤, 높이). 운전자 자동 구분에 쓴다. */
    fun seatSignature(profile: String): Pair<Double, Double>? {
        val t = load(profile).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { it.has("foreAft") && it.has("height") }
        if (t.isEmpty()) return null
        return t.map { it.getInt("foreAft") }.average() to t.map { it.getInt("height") }.average()
    }

    private fun load(profile: String) = JSONArray(prefs.getString(profile, "[]"))

    /** 넣은 순서가 아니라 주행 시각 순. 가상 주행(과거 날짜)과 실제 주행이 섞여도 최근이 "오늘". */
    private fun sorted(arr: JSONArray) = (0 until arr.length()).map { arr.getJSONObject(it) }.sortedBy { it.optLong("start") }

    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k)) optDouble(k).takeIf { !it.isNaN() } else null

    private companion object {
        const val MAX_TRIPS = 200
        const val WINDOW = 30
        const val MIN_PAST_FOR_TREND = 8
    }
}
