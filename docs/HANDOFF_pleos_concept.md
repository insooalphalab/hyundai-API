# 인계 문서: Pleos 차량 SDK 개념 앱 기획

> 이전 대화(claude.ai, 2026-10-23~24)에서 Claude Code로 넘기는 요약. 이 파일을 먼저 읽고 이어서 진행할 것.

## 1. 프로젝트 성격
- 현대차그룹 **Pleos Connect SDK**(AAOS 기반 인포테인먼트 앱용) 기반의 **개념 앱** 기획.
- 실차 배포·사업화가 아니라 개념 증명. 기술적 가능성은 "가능한 걸로 친다"는 전제(예: FusedLocation 사용 가능 가정).
- 목표(사용자 본인 표현): **스마트폰은 알 수 없는 행위·환경 데이터를, 여러 차량의 군집 데이터와 섞고 통계적 추정을 넣어, 지금까지 측정하지 못한 새로운 데이터를 만들어 내는 것.** 동기는 "기술적 재미(새로운 걸 찾아내기)".

## 2. 사용자 선호 / 작업 방식 (중요)
- **짧고 핵심만.** 길고 읽기 어려운 제안 싫어함.
- **뻔한 것 금지**: "에어컨 틀면 더워진다" 수준의 1신호 추론, 분위기 제어 앱+스토리텔링, 날씨 클리셰, 도로 한정 아이디어.
- **내비/폰 앱으로 이미 되는 것은 탈락.**
- 기관(기상청·지자체·정부)만 쓰는 데이터 말고 **개인 사용자가 궁금해할 것**을 원함.
- B2B(공유차 반납 점검, 운행기록부 등)는 **고려하지 않음**.
- 사용자가 말하지 않은 목적(예: VM/FDC 직무 포트폴리오)을 **임의로 가정하지 말 것.** 이전에 이걸 가정했다가 지적받음.
- 제안 전에 **효과 크기를 문헌으로 먼저 확인**할 것(짐 무게 아이디어를 효과 크기 확인 없이 냈다가 탈락).

## 3. 아이디어 판별 기준 (대화에서 합의된 것)
1. 직접 읽히는 신호가 없을 것 (숨은 변수)
2. 효과 크기가 잡음보다 클 것
3. 폰/내비로 대체 불가
4. 개인 사용자에게 의미가 있을 것
5. 새로울 것

## 3.5 아이디어 생성 규칙 (2026-10-27, 시험 후 채택 — 사용자: "규칙이 있는 게 낫다")
1. 도메인이 다른 신호 2개 이상(차 안 + 차 밖, 사람 + 환경)
2. 그중 하나는 차에만 있는 신호(폰 대체 방지)
3. 군집은 평균이 아니라 보정 장치(차량 효과 분리, 구간 난이도 제거, 차종 보정)
4. 결과가 어느 요소 하나로는 나오지 않는 값(숨은 변수)
5. 각 요소가 다른 요소의 약점·부작용을 막음
6. **선행 검색 필수** — 규칙 통과만으로 새로움 보장 안 됨(시험 5개 중 2개 선행 존재)
- 주의: 생성 결과가 "평소의 나"(운전 행동) 변형으로 쏠림. 의도적으로 다른 대상(사물·공간·환경)도 생성할 것.
- 시험 결과: CONCEPT_v3_integrated.md 10.10절.

## 4. 가장 좋게 평가된 접근 (사용자: "접근방식 자체는 가장 우수")
- **정속 주행 중 배터리 소모 잔차 → 유효 풍속 역산.** 알려진 물리량(구동·경사·차량 상수)을 빼고 남는 잔차를 숨은 변수로 해석, 한 대는 노이즈지만 군집 평균은 신호.
- 선행: Chalmers(커넥티드 차량 데이터로 풍속·노면 추정, "그런대로 잘 됨"), 유효 풍속 특허 US11117590, ABRP(측풍만으로 소비 최대 +8%).
- 탈락 이유: 정속 구간(사실상 고속도로)에서만 측정, 풍속 데이터 자체의 확장성 부족.

## 5. 검토 후 상태
| 아이디어 | 상태 | 사유 |
|---|---|---|
| 와이퍼+조명 기반 국지 강수 보정 | 보류(가능성만 열어둠) | 근거 강함(미시간대 ~70대 연구, 도요타·부산 시범). 단 날씨·기관용 |
| 풍속 역산 | 접근은 최우수, 대상은 탈락 | 정속 구간 한정, 확장성 부족 |
| 짐 무게(전비 변화) | 탈락 | 100kg≈전비 1~5%. 100kg급 변화는 대부분 사람이고 좌석 점유로 이미 앎 |
| 배터리 SOH | 탈락 | 충전 사업자 데이터로 대체 가능 |
| 충전기 실사용 신뢰도/대기열 | 탈락 | 내비·충전 앱으로 구현 가능 |
| 트렁크/승하차/체류 행위 지도 | "새롭다" 평가, 보류 | 개인 가치 약함(상권·지자체용) |
| 주차 미기후(여름 열 노출, 겨울 서리) | 후보 | 시동 직후 공조 에너지 잔차 + 군집 평균. 개인 결정(주차 위치·예약 공조). 단 결국 날씨 계열 |
| 도시 열·충전 정책·상권 | 탈락 | 기관용 |

## 6. 열린 결정 (2026-10-26 결정됨: 4번 → 이후 경과는 11번)
날씨 외에는 기준을 모두 통과한 게 없음. 어떤 기준을 완화할지:
1. 개인 가치 완화 → 트렁크 활동 지도
2. 내비 대체 불가 완화 → 충전기 실사용 신뢰도
3. 새로움 완화 → 환경 보정 전비 비교
4. 날씨 영역 유지 → "주차 미기후"(개인 생활 데이터) + 트렁크 지도를 2차 출력

## 7. SDK / 환경 핵심 사실
- 구성: Vehicle, NaviHelper, TextToSpeech, SpeechToText, ADAS, FusedLocation (+ Gleo AI의 LLM, Sync SDK).
- Vehicle SDK 2.3.2: 19개 도메인, 공개 메서드 442개. 패턴 = capability / get / set / callback, **area(예: ROW_1_LEFT) 단위**.
- SDK 상태(공식 문서): **Vehicle State·NaviHelper는 즉시 사용 가능**, FusedLocation·TTS·STT·Vehicle Control은 "준비 중"(문서끼리 불일치 있음: FusedLocation 샘플 1.1.3 공개됨).
- 호환성: Vehicle은 차종별(`checkXXXCapabilities`), NaviHelper는 내비 탑재 차량만, FusedLocation은 `isSupported`.
- 에뮬레이터: Pleos Connect Emulator v2.1.0, API 34 이미지, **minSdk 28 이상**, Cold boot 필수(VHAL), GPU 필수(최소 RTX 3060 노트북 6GB급). 1대=차 1대, 여러 대 동시 실행은 문서에 없음.
- **Virtual Console**: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`. 수동 값 설정 도구. 주요 카드: 기어, 브레이크 페달 0~100%, 주행거리, 핸들 각 -540~540°, 크루즈 상태·목표속도, 도어/트렁크/창문 0~100%/좌석 점유, 조명(앰비언트 포함), 와이퍼, 경고등(TPMS 등), 배터리 에너지 0~200kWh(0.1 단위)·SOC·용량·충전 전류/목표/스위치/포트, 공조(A/C, 내외기, 팬 0~8, 구역 온도, 열선/통풍, 공기청정). **속도·위치 카드는 없음**(속도 권한은 존재).
- 에뮬레이터 주의: set 성공해도 get 값이 안 바뀔 수 있음, 일부 area 미지원.
- 위치 테스트: `adb emu geo fix <lon> <lat>`.
- 보안 가이드: HTTPS/TLS1.2+, 최소 권한, 필요한 API만. 위치·동승 등은 동의·집계·최소 인원 기준 필요.

## 8. 활용 가능 공공 데이터 (확인됨)
- 기상청 단기예보, 생활기상지수, 서리 발생 가능성 예측
- 에어코리아 대기오염, 서울 S-DoT(1,170대, 온습도 1시간 단위, 일부 풍향·풍속)
- 환경공단 전기차 충전소(충전기 상태, 마지막 충전 시각)
- 무인단속카메라, 어린이보호구역, 과속방지턱(지자체별), 돌발상황정보, 서울 포트홀 보수 위치
- 소상공인 상가업소, KOPIS 공연, TourAPI, 천문연 특일정보
- 목록: https://github.com/yybmion/public-apis-4Kr

## 9. 주요 링크
- Pleos Playground: https://pleos.ai/playground
- SDK 사용 신청/상태: https://document.pleos.ai/docs/pleos-only/create-my-project
- API 호환성: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console 사용: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- Vehicle 샘플 가이드: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- 샘플 저장소: https://github.com/Pleos-Playground/pleos-sample-vehicle-app , https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app , https://github.com/Pleos-Playground/pleos-sample-navi-app
- 보안 가이드: https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide

## 10. Claude Code에서 시작할 때
"HANDOFF_pleos_concept.md 읽고, 11번부터 이어서 하자"라고 지시하면 됨.

## 11. 진행 상황 (2026-10-26~27, Claude Code 세션)
- 6번 결정: **4번(주차 미기후 주력 + 트렁크 지도 2차)** 선택 → 검토 후 방향 확장.
- 경과: 주차 미기후 → "평소의 나"(운전 기반 개인 내 변화) → 수면 드라이브 → **통합 개념 v3(가족 수면)**.
- **사용자 평가: v3(가족 수면 + 좌석별 개별 환경 + PEX 확장)를 "괜찮은 아이디어"로 기록.** 사용자가 원하는 진행 방식 = 기존 모듈과 서로 보정되는 새 모듈을 붙여 확장.
- **핵심 원칙(사용자, 2026-10-28): 진짜 맞춤은 본인의 판단이 아니라 몸이 실제로 반응한 지점(운전 행동·생리 신호)을 근거로 한다.** 새 기능은 이 원칙으로 평가할 것.
- 2026-10-28 추가 모듈: 경로 성격 선택(편안한/즐기는/빠른 길, 몸의 반응으로 학습) → CONCEPT_v3 3.7절.
- 2026-10-28 근거 자료: `EVIDENCE_driver_state_sensing.md` — 몸의 반응으로 운전자 상태 파악하는 연구·기술 분석. 졸음은 양산(조향·카메라), 음주·저혈당·인지 저하는 실차 연구, 스트레스·인지 부하는 실험실. "평소의 나"는 졸음·피로처럼 검증된 신호로 시작.
- **현재 기준 문서: `CONCEPT_v3_integrated.md`** (통합 개념, 근거, 안전 규칙, 선행 사례, 필요 신호, 기술지원 질문, 탈락 목록).
- 보조 문서: `CONCEPT_baseline_me.md`(평소의 나 상세), `NOTES_parking_microclimate.md`(미기후 조사·SDK 확인·로거 구조·시뮬레이션), `sim_cluster_recovery.py`, `sim_crossed_car.py`(군집 복원 시뮬레이션).
- 사용자 상태: 에뮬레이터·SDK **미설치**. 앱 구현보다 개념 정리 우선.
- 추가로 확인된 SDK 사실: Vehicle SDK에 외기온·배터리 온도·운전자 상태 API 없음. 실내 온도(`getHvacTemperatureCurrent`, 좌석 구역별 °C), SOC(정수 %), 전비(km/kWh), 조향각, 브레이크 페달, 와이퍼는 있음. ADAS SDK는 차 바깥 정보(객체·차선·주차 공간), 상세 문서 미확인. STT는 텍스트만.
- 사용자 관심 확장: PEX(Pleos Extended, 외부 디바이스 장착)를 전제로 한 센서 추가.

## 12. 다음 할 일 후보
1. 1가정 기록 실험 설계: 좌석별 온도·빛 조절이 동승자 수면 진입·유지에 주는 효과(수동 "잠들었어요" 입력으로 시작)
2. Pleos 기술지원 질문 정리·발송 (CONCEPT_v3 9절)
3. PEX 공식 자료(장착 위치, 전원, 개발 조건) 찾기
4. 기준선 형성 기간·군집 규모 시뮬레이션
- 2026-11-04: 앱 이름 **시트룸 — 좌석마다 나만의 방** 확정. 시각화 2차본(`시트룸_2차본.html`).
- 2026-11-04: **첫 APK 빌드** `seatroom-app/dist/seatroom-0.1.0-debug.apk` (1단계 SDK 기능: 좌석별 침실·서재 모드, CO₂ 추정, 졸음 자구책, 평소의 나 4축, 승하차·열 감각·귀가 전 머무름·공조 다툼, JSONL 기록). 빌드 도구는 이 PC `%USERPROFILE%\dev`(JDK 17, Android SDK 35, Gradle 8.10.2), 에뮬레이터는 다른 PC. 사용법·제약: `seatroom-app/README.md`.
- SDK 원본 확인으로 바뀐 사실: 브레이크 페달은 Boolean(눌림 여부), 가속 페달 없음, `Gear` API 있음(deprecated 표시), 좌석 위치 콜백 있음.
