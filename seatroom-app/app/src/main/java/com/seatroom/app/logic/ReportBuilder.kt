package com.seatroom.app.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 휴대폰으로 보낼 보고서에 들어갈 것. 차 화면에서 뺀 세부 내용은 모두 여기로 간다. */
data class ReportData(
    val createdAt: Long,
    val scenarioTitle: String,
    val policyNote: String?,
    val alertTitle: String?,
    val alertLines: List<String>,
    /** 화면이 정리한 요약에 주의할 사람이 있는지 */
    val alertWarn: Boolean = false,
    val driverNote: String?,
    val people: List<PersonReport>,
    val findings: List<Finding>,
    val roomModes: List<String>,
    val unsupported: List<String>,
)

data class PersonReport(
    val name: String,
    val readings: List<AxisReading>,
    val trend: List<Pair<Axis, Double>>,
    val trips: List<TripSummary>,
)

/**
 * 휴대폰에서 읽는 HTML 보고서(밝은 배경, 한 열). 차 안 화면과 달리 숫자·목록·그래프를 다 보여 준다.
 * 외부 리소스 없이 파일 하나로 열린다.
 */
object ReportBuilder {
    private val dayFmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
    private val fullFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

    fun html(d: ReportData): String = buildString {
        append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>시트룸 보고서</title><style>").append(CSS).append("</style></head><body><main>")
        append("<header><div class=\"brand\">시트룸</div><div class=\"sub\">좌석마다 나만의 방 · 보고서</div>")
        append("<div class=\"meta\">${esc(d.scenarioTitle)} · ${fullFmt.format(Date(d.createdAt))}</div></header>")

        // 1. 오늘 알림
        append("<section><h2>오늘 알림</h2>")
        if (d.alertTitle == null) append("<p class=\"muted\">오늘 주행 기록이 아직 없어요.</p>")
        else {
            val sent = d.alertTitle.startsWith("알림 보냄") || d.alertWarn
            append("<div class=\"card ${if (sent) "warn" else "ok"}\"><b>${esc(d.alertTitle)}</b>")
            d.driverNote?.let { append("<p>${esc(it)}</p>") }
            if (d.alertLines.isNotEmpty()) append("<ul>").append(d.alertLines.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")
            append("</div>")
        }
        d.policyNote?.let { append("<p class=\"muted\">알림 규칙: ${esc(it)}</p>") }
        append("</section>")

        // 2. 사람별 평소의 나
        d.people.forEach { p ->
            append("<section><h2>${esc(p.name)} · 평소의 나</h2>")
            append("<p class=\"muted\">기록된 주행 ${p.trips.size}회 (가상 ${p.trips.count { it.synthetic }}회). 점선 띠 = 평소 범위, 마지막 점 = 가장 최근 주행.</p>")
            if (p.trend.isNotEmpty()) {
                append("<div class=\"card warn\"><b>몇 주에 걸친 변화</b><p>")
                append(p.trend.joinToString(" · ") { (ax, v) -> "${ax.label} ${sign(v)}${fmt(v)}${ax.unit}" })
                append(" (최근 7회 평균 vs 그 이전)</p></div>")
            }
            append("<div class=\"grid\">")
            p.readings.forEach { r ->
                val series = p.trips.reversed().mapNotNull { it.values[r.axis] }
                append("<div class=\"axis\"><div class=\"ah\"><span class=\"dot ${if (!r.ready) "na" else if (r.changed) "warn" else "ok"}\"></span>${r.axis.label}</div>")
                append("<div class=\"q\">${esc(r.axis.question)}</div>")
                append(spark(series, r.baseline, r.sd, r.threshold, r.changed))
                append("<div class=\"v\">이번 ${r.today?.let { fmt(it) } ?: "—"} · 평소 ${r.baseline?.let { fmt(it) } ?: "—"} ${r.axis.unit}</div>")
                append("<div class=\"m\">${esc(r.message)}</div></div>")
            }
            append("</div>")
            append("<details><summary>주행 기록 ${p.trips.size}회</summary><table><tr><th>시각</th><th>분</th>")
            Axis.entries.forEach { append("<th>${it.label}</th>") }
            append("</tr>")
            p.trips.take(40).forEach { t ->
                append("<tr><td>${dayFmt.format(Date(t.start))}${if (t.synthetic) " <i>가상</i>" else ""}</td><td>${fmt(t.minutes, 0)}</td>")
                Axis.entries.forEach { append("<td>${t.values[it]?.let { v -> fmt(v) } ?: "—"}</td>") }
                append("</tr>")
            }
            append("</table></details></section>")
        }

        // 3. 방에서 읽은 신호
        append("<section><h2>방에서 읽은 신호</h2>")
        listOf(
            "어르신 방 · 승하차" to setOf(FindingKind.BOARDING, FindingKind.ALIGHTING),
            "열 감각" to setOf(FindingKind.COMFORT_POINT, FindingKind.HEAT_NO_ACTION),
            "귀가 전 머무름" to setOf(FindingKind.DWELL),
            "졸음 자구책 · 공조 다툼" to setOf(FindingKind.COUNTERMEASURE, FindingKind.CONFLICT),
        ).forEach { (title, kinds) ->
            val list = d.findings.filter { it.kind in kinds }
            append("<details${if (list.isNotEmpty()) " open" else ""}><summary>${esc(title)} · ${list.size}건</summary>")
            if (list.isEmpty()) append("<p class=\"muted\">없음</p>")
            else append("<ul class=\"log\">").append(list.take(20).joinToString("") { "<li><span>${dayFmt.format(Date(it.t))}</span>${esc(it.text.removePrefix("[가상] "))}</li>" }).append("</ul>")
            append("</details>")
        }
        append("</section>")

        // 4. 부록
        append("<section><h2>부록</h2>")
        append("<p><b>방 설정</b> ${esc(d.roomModes.joinToString(", ").ifEmpty { "모두 기본" })}</p>")
        append("<details><summary>이 차에서 안 되는 신호 ${d.unsupported.size}개</summary><ul class=\"log\">")
        d.unsupported.forEach { append("<li>${esc(it)}</li>") }
        append("</ul></details>")
        append("<p class=\"muted\">진단이 아니에요. 비교 대상은 그 운전자의 지난 주행뿐이고, 원인은 단정하지 않아요. 기록은 차 안에만 저장돼요.</p>")
        append("</section></main></body></html>")
    }

    /** 최근 주행들의 꺾은선 + 평소 범위 띠(평균 ± 기준 σ). */
    private fun spark(v: List<Double>, mean: Double?, sd: Double?, k: Double, changed: Boolean): String {
        if (v.size < 2) return "<div class=\"muted\">기록 부족</div>"
        val w = 260.0; val h = 64.0; val pad = 6.0
        val lo0 = listOfNotNull(v.minOrNull(), mean?.let { m -> sd?.let { m - k * it } }).min()
        val hi0 = listOfNotNull(v.maxOrNull(), mean?.let { m -> sd?.let { m + k * it } }).max()
        val span = (hi0 - lo0).takeIf { it > 1e-9 } ?: 1.0
        fun x(i: Int) = pad + i * (w - 2 * pad) / (v.size - 1)
        fun y(a: Double) = h - pad - (a - lo0) / span * (h - 2 * pad)
        return buildString {
            append("<svg viewBox=\"0 0 $w $h\" class=\"spark\">")
            if (mean != null && sd != null) {
                val top = y(mean + k * sd); val bot = y(mean - k * sd)
                append("<rect x=\"$pad\" y=\"${f(top)}\" width=\"${f(w - 2 * pad)}\" height=\"${f(bot - top)}\" class=\"band\"/>")
            }
            append("<polyline class=\"line\" points=\"").append(v.indices.joinToString(" ") { "${f(x(it))},${f(y(v[it]))}" }).append("\"/>")
            append("<circle cx=\"${f(x(v.size - 1))}\" cy=\"${f(y(v.last()))}\" r=\"4\" class=\"${if (changed) "end warn" else "end"}\"/>")
            append("</svg>")
        }
    }

    private fun f(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun fmt(d: Double, n: Int = 2) = String.format(Locale.US, "%.${n}f", d)
    private fun sign(d: Double) = if (d > 0) "+" else ""
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val CSS = """
:root{--bg:#F4F6F8;--card:#fff;--ink:#15202B;--muted:#5A6774;--line:#DDE3E8;--cool:#1D8CB0;--warm:#CF8233;--ok:#2C8A57;--warn:#B98410}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.6 -apple-system,"Apple SD Gothic Neo","Noto Sans KR",sans-serif}
main{max-width:720px;margin:0 auto;padding:20px 16px 48px}
header{padding:8px 0 16px}.brand{font-size:30px;font-weight:700}.sub{color:var(--muted)}.meta{margin-top:6px;font-size:13px;color:var(--muted)}
section{margin-top:22px}h2{font-size:18px;margin:0 0 10px}
.card{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--line);border-radius:12px;padding:12px 14px}
.card.warn{border-left-color:var(--warn)}.card.ok{border-left-color:var(--ok)}.card p{margin:6px 0 0}.card ul{margin:6px 0 0;padding-left:18px}
.muted{color:var(--muted);font-size:13px}
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.axis{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px}
.ah{font-weight:600;display:flex;align-items:center;gap:6px}.q,.m{font-size:12.5px;color:var(--muted)}.v{font-size:13.5px}
.dot{width:9px;height:9px;border-radius:50%;display:inline-block}.dot.ok{background:var(--ok)}.dot.warn{background:var(--warn)}.dot.na{background:var(--line)}
.spark{width:100%;height:auto;display:block;margin:6px 0}.band{fill:var(--cool);opacity:.13}.line{fill:none;stroke:var(--ink);stroke-width:1.5}
.end{fill:var(--ok)}.end.warn{fill:var(--warn)}
details{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:10px 14px;margin-top:8px}summary{cursor:pointer;font-weight:600}
table{width:100%;border-collapse:collapse;font-size:12.5px;margin-top:8px;font-variant-numeric:tabular-nums}th,td{text-align:right;padding:4px 6px;border-bottom:1px solid var(--line)}th:first-child,td:first-child{text-align:left}
.log{list-style:none;padding:0;margin:8px 0 0;font-size:13.5px}.log li{padding:3px 0;border-bottom:1px solid var(--line)}.log span{color:var(--muted);display:inline-block;width:86px}
@media (max-width:480px){.grid{grid-template-columns:1fr}}
"""
}
