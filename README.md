# 시트룸 — 좌석마다 나만의 방

현대차그룹 **Pleos Connect SDK**(AAOS 인포테인먼트) 기반 **개념 앱** 기획과 시뮬레이션 APK.

> **가족은 편히 자고, 운전자는 깨어 있게.**
> 이미 하는 이동에서, 좌석별로 자는 사람은 편히 자게 하고 운전석은 깨어 있게 한다.
> 운전자 상태 감시("평소의 나")가 안전을 닫고, 차량 실내 센싱(CO₂ 추정·미기후)이 입력이 된다.

- 실차 배포·사업화가 아닌 **개념 증명**.
- 목표: 스마트폰은 알 수 없는 차 안의 행위·환경 신호를 통계적으로 해석해, 지금까지 측정하지 못한 값을 만들어 낸다.
- 핵심 원칙: 진짜 맞춤은 본인의 판단이 아니라 **몸이 실제로 반응한 지점**(운전 행동·생리 신호)을 근거로 한다.

## 폴더 구성

```
.
├── docs/            기획·근거 문서
├── visuals/         개념 시각화 (HTML, 스토리 이미지)
├── simulations/     군집 복원 시뮬레이션 (Python)
└── seatroom-app/    Android 앱 (Kotlin + WebView 화면)
    ├── app/         소스 (SDK 연결, 판정 로직, 단위 테스트)
    ├── design/      차 화면 HTML (빌드 시 앱 assets로 복사)
    └── dist/        최신 APK·브라우저용 화면·보고서 견본
```

## 문서 (`docs/`)

| 파일 | 내용 |
|---|---|
| [CONCEPT_v3_integrated.md](docs/CONCEPT_v3_integrated.md) | **기준 문서.** 통합 개념 v3 — 모듈 구성, 근거, 안전 규칙, 선행 사례, 필요 신호, 기술지원 질문, 탈락 목록 |
| [CONCEPT_baseline_me.md](docs/CONCEPT_baseline_me.md) | "평소의 나" — 운전 기반 개인 내 변화 감지 상세 |
| [EVIDENCE_driver_state_sensing.md](docs/EVIDENCE_driver_state_sensing.md) | 몸의 반응으로 운전자 상태를 파악하는 연구·기술 정리 (졸음·음주·저혈당·인지 저하·스트레스) |
| [DATA_inventory_unused.md](docs/DATA_inventory_unused.md) | Vehicle SDK 19개 도메인 데이터: 쓴 것 / 안 쓴 것 |
| [NOTES_parking_microclimate.md](docs/NOTES_parking_microclimate.md) | 주차 미기후 효과 크기·신호 설계·SDK 확인·로거 구조 |
| [HANDOFF_pleos_concept.md](docs/HANDOFF_pleos_concept.md) | 인계 문서 — 판별 기준, 아이디어 생성 규칙, 검토 경과, SDK·에뮬레이터 사실, 링크, 진행 상황 |
| [SUMMARY_session_2026-10-26_28.md](docs/SUMMARY_session_2026-10-26_28.md) | 2026-10-26~28 세션 흐름·결정·결론 요약 |

## 시각화 (`visuals/`)

| 파일 | 내용 |
|---|---|
| `시트룸_2차본.html` | 시트룸 개념 시각화 2차본 |
| `VISUAL_implementation_map.html` | 시트룸 구현 지도 (스토리 이미지 `story/1~6.webp` 사용) |
| `Pleos_앱_구현지도.html` | Pleos SDK 기준 앱 구현 지도 |

브라우저로 바로 열면 된다.

## 시뮬레이션 (`simulations/`)

여러 차의 기록을 모아 한 대에서는 잡음인 값을 복원할 수 있는지 확인한 실험 (주차 미기후 검토 단계).

| 파일 | 내용 |
|---|---|
| `sim_cluster_recovery.py` | 사건 수에 따른 군집 평균 복원 정확도 (→ `sim_cluster_recovery_results.csv`, `sim_out.txt`) |
| `sim_crossed_car.py` | 차량 효과와 장소 효과가 교차할 때 분리 가능성 (→ `sim_crossed_car_results.csv`) |

```
cd simulations
python sim_cluster_recovery.py
python sim_crossed_car.py
```

## 앱 (`seatroom-app/`) — 0.9.0

1단계(Vehicle SDK만으로 가능한 기능)를 담은 시뮬레이션 앱.

| 화면 | 기능 |
|---|---|
| 방 | 좌석별 모드(운전·휴식·기본·집중) → 그 자리 주인에게서 배운 온도·바람으로 공조·시트·조명 제어 |
| 방 | CO₂ 추정(센서 없이, 좌석 점유·내기 순환·바람·시간) + "외기로 환기" |
| 방 | 졸음 자구책(창문 열기·온도 내림·바람 세게) 감지 → 휴식 권고 |
| 평소의 나 | 4축(수행·자세·시야 보상·계획)을 본인 평소와 비교(±2σ), 운전자 자동 구분 |
| 평소의 나 | 승하차 시간, 열 감각, 귀가 전 머무름, 공조 다툼 |
| 주행 중 | 큰 타일 화면 + 백그라운드 서비스로 수집·판정·알림 계속 |

- 시나리오 3종(안전운전자 가족 / 고령 운전자 / 2인 교대)으로 30일 가상 주행을 만들어 기준선과 알림 규칙을 시험한다.
- 모든 신호와 판정은 차 안에 날짜별 JSONL로 저장(본인만 열람).
- 화면은 `design/seatroom-screens.html` 하나. 앱에서는 WebView로 뜨고 차 신호를 받는다. 브라우저에서 열면 시뮬레이션만으로 돈다.

**바로 보기**
- 브라우저: `seatroom-app/dist/seatroom-display-0.9.0.html`
- 에뮬레이터: `adb install -r seatroom-app/dist/seatroom-0.9.0-debug.apk`

**빌드**: JDK 17, Android SDK 35 (`local.properties`에 `sdk.dir`), Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`
```
cd seatroom-app
gradlew.bat assembleDebug
```

설치·Virtual Console 시험법·검증 결과·알려진 제약은 **[seatroom-app/README.md](seatroom-app/README.md)**.

## 참고 링크

- Pleos Playground: https://pleos.ai/playground
- API 호환성: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- Vehicle 샘플 앱: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
