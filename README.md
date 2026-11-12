# 시트룸 (SeatRoom) — 좌석마다 나만의 방

**한 줄 소개:** 차가 자리마다 앉은 사람을 알아보고, 그 사람에게서 배운 온도·바람·시트로 먼저 맞추며, 운전자는 "평소의 나"와 비교해 깨어 있게 돕는 Pleos Connect 앱.

> **가족은 편히 자고, 운전자는 깨어 있게.**
> 이미 하는 이동에서, 좌석별로 자는 사람은 편히 자게 하고 운전석은 깨어 있게 한다.

## 제출 정보

| 항목 | 내용 |
|---|---|
| 팀명 | SKAI_4 |
| 앱 이름 | 시트룸 (SeatRoom) |
| APK 파일 | [`apk/SKAI_4_SeatRoom_release.apk`](apk/SKAI_4_SeatRoom_release.apk) (release, 11.7 MB) |
| 버전 | 0.9.3 (versionCode 18) |
| 패키지 | `com.seatroom.app` |
| 소스 코드 | [`seatroom-app/`](seatroom-app/) (Kotlin + WebView 화면) |
| 소개 영상 | [`seatroom-app/dist/seatroom-0.9.3-demo.mp4`](seatroom-app/dist/seatroom-0.9.3-demo.mp4) (4분 45초, 자막·음성) |

## 실행 방법

1. Pleos Connect 에뮬레이터를 **Cold boot**로 켠다(Quick boot면 VHAL이 동작하지 않아 차 신호가 들어오지 않는다).
2. APK 설치:
   ```
   adb install -r apk/SKAI_4_SeatRoom_release.apk
   ```
3. 앱 목록에서 **시트룸**을 실행한다. 처음 실행 때 알림 권한을 허용한다(보고서·CO₂·졸음 알림).
4. 첫 화면에서 시나리오(가족 / 고령 운전자 / 2인 교대 / 혼자 출퇴근)를 고르면 **방** 화면이 뜬다.
   - 좌석을 누르면 그 자리의 맞춤 내역, 빈자리는 "타면 이렇게 바뀐다" 미리보기와 가상 탑승 버튼.
   - **평소의 나** 탭: 오늘 운전을 평소와 비교, 알림 기준 시험.
   - 상단 **보고서**: 휴대폰으로 보내는 리포트 미리보기.
5. 차 신호로 시험하려면 Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)에서
   - Gear D/R ↔ P: 주행 중 화면 ↔ 방 화면, D → P 한 번이 주행 1회(1분 이상)
   - 좌석 점유 OFF → ON: 그 자리에 사람 표시, 탑승 인식 시작
   - 좌석 점유 3~4개 + 내기 순환 ON: 몇 분 뒤 CO₂ 추정이 1,500 ppm을 넘으면 "외기로" 알림

   자세한 시험법은 [seatroom-app/README.md](seatroom-app/README.md#virtual-console로-시험하기).

에뮬레이터 없이 화면만 보려면 `seatroom-app/dist/seatroom-display-0.9.3.html`을 브라우저로 연다(앱 안 화면과 같은 파일, 차 신호 대신 시나리오 값으로 동작).

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 대상 | Pleos Connect (Android Automotive OS), **minSdk 28**, targetSdk/compileSdk 35 |
| 차량 SDK | Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (`https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 권한 | 차량: `pleos.car.permission.CAR_CLIMATE·CAR_DOORS·CAR_DRIVE·CAR_POWERTRAIN·CAR_LIGHTS·CAR_SEATS·CAR_WINDOWS·READ_CAR_STEERING`, 제어: `CONTROL_CAR_CLIMATE·CONTROL_CAR_LIGHTS·CONTROL_CAR_SEATS`. 일반: `POST_NOTIFICATIONS`(실행 시 요청), `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED` |
| 실행 조건 | 에뮬레이터 Cold boot, GPU 그래픽 Hardware, RAM 4 GB |
| 서명 | 심사용 설치를 위해 Android 디버그 키로 서명한 release 빌드(코드 축소 없음) |
| 데이터 | 신호·판정은 기기 안 앱 폴더에 날짜별 JSONL로 저장(30일 보관, 보고서 20개). 외부 전송 없음 |
| 확인 상태 | 단위 테스트 9개 통과. 에뮬레이터 실행 확인은 0.9.0까지, 0.9.3은 브라우저 화면 확인 |

## 개발에 사용한 에뮬레이터와 화면 규격

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Android Studio Device Manager → Automotive → "Pleos Connect v2.1.0") |
| 시스템 이미지 | Android **API 34**, "Pleos Connect v2.1.0 APIs" |
| 화면 | 가로(landscape) 전체 화면. 기기 프로필 "Pleos Connect v2.1.0"의 기본 해상도 사용 |
| 화면 대응 | 화면은 WebView 하나가 창 전체를 채우는 반응형 레이아웃. 앱 모드로 **1920×1080, 1920×720, 1280×720, 2560×1440(2x), 1024×600**에서 잘림 없이 표시되는 것을 확인 |

에뮬레이터 해상도는 `adb shell wm size` / `adb shell wm density`로 확인할 수 있다.

## 빌드 방법

JDK 17, Android SDK 35 (`seatroom-app/local.properties`에 `sdk.dir`).

```
cd seatroom-app
gradlew.bat testDebugUnitTest assembleRelease
```

결과물은 `seatroom-app/app/build/outputs/apk/release/app-release.apk`. 경로에 한글이 있으면 Android Gradle Plugin이 빌드를 거부하므로, 그럴 때는 `seatroom-app`을 영문 경로로 복사해서 빌드한다.

## 개념

현대차그룹 **Pleos Connect SDK** 기반 개념 앱 기획과 시뮬레이션 APK. 실차 배포·사업화가 아닌 **개념 증명**이다.
- 목표: 스마트폰은 알 수 없는 차 안의 행위·환경 신호를 통계적으로 해석해, 지금까지 측정하지 못한 값을 만들어 낸다.
- 핵심 원칙: 진짜 맞춤은 본인의 판단이 아니라 **몸이 실제로 반응한 지점**(운전 행동·생리 신호)을 근거로 한다.

## 폴더 구성

```
.
├── apk/             제출용 release APK (SKAI_4_SeatRoom_release.apk)
├── docs/            기획·근거 문서
├── visuals/         개념 시각화 (HTML, 스토리 이미지)
├── simulations/     군집 복원 시뮬레이션 (Python)
└── seatroom-app/    Android 앱 (Kotlin + WebView 화면)
    ├── app/         소스 (SDK 연결, 판정 로직, 단위 테스트)
    ├── design/      차 화면 HTML (빌드 시 앱 assets로 복사)
    └── dist/        시험용 debug APK·브라우저용 화면·소개 영상·보고서 견본
```

## 문서 (`docs/`)

| 파일 | 내용 |
|---|---|
| [APPLICATION_seatroom.md](docs/APPLICATION_seatroom.md) | 제출용 항목 초안 — 한 줄 소개, 기획 배경, 주요 기능, 사용 시나리오, 개발 계획, 기대효과 |
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

## 앱 (`seatroom-app/`) — 0.9.3

1단계(Vehicle SDK만으로 가능한 기능)를 담은 시뮬레이션 앱.

| 화면 | 기능 |
|---|---|
| 방 | 좌석별 모드(운전·휴식·기본·집중) → 그 자리 주인에게서 배운 온도·바람으로 공조·시트·조명 제어 |
| 방 | 탑승 인식: 사람이 타면 평소 그 사람인지 자리가 자동으로 판정(시트 위치·처음 맞춘 온도·30일 탑승 횟수) → 같으면 배운 설정, 다르면 기본값으로 새로 배움. 사람은 틀렸을 때만 한 번 고침 |
| 방 | CO₂ 추정(센서 없이, 좌석 점유·내기 순환·바람·시간) + "외기로 환기". 계속 높으면 10분마다 다시 알림 |
| 방 | 졸음 자구책(창문 열기·온도 내림·바람 세게) 감지 → 휴식 권고 |
| 평소의 나 | 4축(수행·자세·시야 보상·계획)을 본인 평소와 비교(±2σ), 운전자 자동 구분 |
| 평소의 나 | 승하차 시간, 열 감각, 귀가 전 머무름, 공조 다툼 |
| 주행 중 | 큰 타일 화면 + 백그라운드 서비스로 수집·판정·알림 계속(시동과 함께 시작) |

- 시나리오 4종(안전운전자 가족 / 고령 운전자 / 2인 교대 / 혼자 출퇴근·오늘은 동승)으로 30일 가상 주행을 만들어 기준선과 알림 규칙을 시험한다.
- 모든 신호와 판정은 차 안에 날짜별 JSONL로 저장(본인만 열람, 30일 보관).
- 화면은 `design/seatroom-screens.html` 하나. 앱에서는 WebView로 뜨고 차 신호를 받는다. 브라우저에서 열면 시뮬레이션만으로 돈다.

**시험용 파일 (`seatroom-app/dist/`)** — 제출용 APK는 위의 `apk/` 폴더

| 파일 | 내용 |
|---|---|
| `seatroom-0.9.3-debug.apk` | 0.9.3 debug APK(14 MB), 개발 시험용 |
| `seatroom-display-0.9.3.html` | 브라우저용 화면(앱 안 화면과 같은 파일) |
| `seatroom-0.9.3-demo.mp4` · `.srt` | 0.9.3 기능 소개 영상(자막·음성)과 자막 파일 |
| `seatroom-display-learnviz.html` | 0.8.2 학습 과정 시각화 변형(참고용) |
| `report-sample-senior.html` | HTML 보고서 견본(고령 운전자 시나리오) |

설치·Virtual Console 시험법·검증 결과·알려진 제약은 **[seatroom-app/README.md](seatroom-app/README.md)**.

## 참고 링크

- Pleos Playground: https://pleos.ai/playground
- API 호환성: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- Vehicle 샘플 앱: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
