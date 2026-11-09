# 주차 미기후: 효과 크기 확인 + 신호 설계 (초안)

> 근거 수치는 문헌, "추정"은 내 계산(미검증). 직접 비교 문헌(그늘 vs 햇볕, 실외 vs 차고의 kWh)은 못 찾음.

## 1. 효과 크기

| | 문헌 근거 | 추정 차이(주차 환경 간) | 판정 |
|---|---|---|---|
| 여름 | 일사 800 W/m²에서 실내 76 °C(외기 41 °C), 햇볕 주차 시 유리·내장재 외기+17 °C, 반사율 +0.5 → 냉방 용량 -13% | 시동 직후 15분 냉각 0.5~0.8 kWh 중 20~40% → **0.1~0.3 kWh** | 한계선. 에너지 분해능 0.1 kWh와 비슷 → 군집 평균 필수 |
| 겨울 | SINTEF 51회 예열: 초기 3~8 kW → 10~20분 후 2~4 kW, 대부분 ≤2 kWh, 최대 5 kWh. Argonne: 20°F 난방으로 항속거리 -20~59% | 차고/실외 냉각 상태 차이 → 시동 직후 난방 에너지 **수백 Wh~1 kWh대** | 여름보다 큼. 주력 후보 |
| 서리 | 제상 1회 4 kWh 초과(특허 주장), 열풍 제상 10~12 kW(특허 계산) | 미확인 | 근거 약함(특허·계산치). 측정 문헌 못 찾음 |

출처:
- https://pmc.ncbi.nlm.nih.gov/articles/PMC10909751/
- https://outage.nrel.gov/transportation/assets/pdfs/40986.pdf
- https://www.osti.gov/biblio/1212442
- https://sintef.brage.unit.no/sintef-xmlui/handle/11250/3064999
- https://www.insideevsforum.com/community/threads/cold-weather-driving-can-reduce-electric-car-range-over-40-percent-aaa-study-finds.4630/

## 2. 신호 설계 (v2: 주신호 = 실내 온도 곡선)

> v1(공조 에너지 잔차)은 폐기: SOC가 정수 %(≈0.7~0.8 kWh/칸, 용량 가정)라 해상도 부족, 공조 전력 신호 없음. 에너지는 보조 검증용으로만 남김.

### 2.1 원리
주차 중 실내는 외기·일사·바람과 열평형을 이룬다. 주차 위치(그늘/햇볕, 차고/실외, 바람길)가 다르면 **같은 날씨에서도 시동 시점의 실내 온도와 공조 후 변화 속도가 달라진다.** 이 차이가 숨은 변수(미기후)다. 에너지를 거치지 않고 °C로 직접 읽는다.

### 2.2 이벤트 창
시동 ON(또는 공조 ON) 시점 t0 ~ t0+15분을 한 "주차 이벤트"로 기록. 콜백 `registerHvacTemperatureCurrent`(구역별)로 샘플링, 주기는 에뮬레이터에서 확인(미확인).

### 2.3 특징량 (이벤트당)
| 특징량 | 정의 | 의미 |
|---|---|---|
| ΔT0 | T_실내(t0) − T_외기(기상청) | **주차 노출의 직접 신호.** 여름 +, 겨울 −. 공조 영향 이전 값 |
| r5 | t0~t0+5분 구간의 dT/dt (기울기) | 초기 공조 반응 속도 (열 부하가 클수록 느림) |
| t_reach | 설정 온도 ±1 °C 도달까지 시간 (미도달 시 우측 절단) | 부하 총량의 대리 지표 |
| ΔT_zone | 구역 간 차(운전석 vs 뒷좌석) | 일사 방향(어느 면이 햇볕을 받았나) |
| 제상 사용 | `getHvacDefrosterState`/`MaxDefrost` ON 여부 | 서리 유무의 사용자 행동 대리 지표 (실측 아님) |

### 2.4 통제 변수 (같이 기록)
설정 온도, 팬 단계, A/C·자동·내외기 순환 상태, 시동 후 주행 시작 여부(주행 풍은 환기 증가), 도어 열림 횟수, 동승자(좌석 점유), 시각·계절, 외기온·일사량·풍속(기상청 가까운 관측소).

### 2.5 추정 모델
위치 클러스터 k, 이벤트 i에 대해 혼합효과 모형:

    ΔT0_ik = β0 + β1·일사량 + β2·외기온 + β3·풍속 + β4·경과시간 + u_k + ε_ik

- 고정효과는 기상청 값으로 설명되는 부분, **u_k(클러스터 랜덤효과) = 그 주차 위치의 미기후 점수**.
- r5, t_reach는 통제 변수(설정 온도·팬)를 넣은 같은 형태 모형(t_reach는 생존분석/절단 회귀).
- 한 대·한 번은 노이즈. 같은 클러스터의 반복 이벤트(본인 재방문 + 다른 차량)가 쌓여야 u_k가 수렴. 클러스터당 최소 이벤트 수 N_min은 시뮬레이션으로 정함.

### 2.6 개인 출력
- "이 주차 위치는 같은 날씨 대비 평균 +x °C 더움/추움" (여름 주 출력은 ΔT0, 겨울은 r5·t_reach·제상 사용률).
- 의사결정 연결: 주차 위치 선택, 예약 공조 시작 시점(t_reach 기반 필요 선행 시간).
- 2차 출력(트렁크 지도)은 같은 클러스터·이벤트 로그를 재사용.

### 2.7 검증 계획
1. **합성 데이터**: 클러스터별 u_k를 심고 날씨·잡음을 섞어 이벤트 생성 → 모델이 u_k를 N_min 이벤트로 복원하는지(해상도 °C 단위, 샘플 주기·결측 포함).
2. **에뮬레이터**: Virtual Console에 실내 온도 카드가 있는지 먼저 확인(없으면 수동 주입 불가 → 합성 데이터만). 위치는 `adb emu geo fix`. 값이 get에 반영 안 될 수 있음(알려진 에뮬레이터 제약).
3. **에너지 교차검증(보조)**: 군집 평균에서 SOC 감소량이 u_k와 같은 방향인지만 확인(절대값 추정은 하지 않음).

### 2.7.1 합성 복원 시뮬레이션 결과 (`sim_cluster_recovery.py`, 클러스터 40개 × 200회 반복)

**분산 값은 전부 가정**(문헌값 아님): σu = 클러스터 효과 SD, σe = 이벤트 잡음 SD(기상 잔차 + 센서), σc = 차량 센서 편차 SD. 기준은 "복원된 u_k와 참값의 상관 ≥ 0.8". 추정은 2단계 모멘트 추정(within 회귀 → 분산 성분으로 수축)으로, REML 혼합모형의 근사다.

| 시나리오 | 평균 상관≥0.8 최소 이벤트/클러스터 | 하위 10% 반복도 ≥0.8 |
|---|---|---|
| A 기준 (σu=2, σe=3 °C) | **5** | 8 |
| B 비관 (σu=1, σe=4 °C) | 30 | 50에서도 미달 |
| C 센서편차 σc=1, 차 1대/클러스터 | 10 (상한 정체, 50개에서도 0.88) | 30 |
| D 센서편차 σc=1, 차 3대/클러스터 | 6 | 15 |

해석:
- 복원 가능 여부는 **σu/σe 비**가 결정. 비 ≈ 0.67이면 클러스터당 5~8회, 비 ≈ 0.25면 30회 이상. 실제 σu, σe가 어느 쪽인지는 에뮬레이터가 아니라 **실차 데이터**로만 알 수 있음 → 이 숫자는 설계 기준선이지 예측이 아님.
- 같은 위치를 **차 1대만** 쓰면 차량 센서 편차가 클러스터 효과와 분리되지 않아 이벤트를 늘려도 상관이 ~0.88 근처에서 정체(C). **여러 대(≥3)가 같은 클러스터를 공유**하면 이 한계가 풀림(D) → 군집 데이터가 필요한 구체적 이유.
- 개인 주차(자택)는 사실상 차 1대이므로 u_k와 센서 편차 분리 불가. 대안: 차량별 오프셋을 시동 전 외기 평형 시점(예: 장시간 주차 후 야간) 값으로 보정.
- 이벤트 수는 클러스터마다 불균형(푸아송)으로 시뮬레이션했고 일부 클러스터는 1회만 방문.
- 한계: 이벤트 간 독립 가정(같은 날 반복 방문의 자기상관 미반영), 날씨 계수는 선형, 기상 관측소 거리 오차는 σe에 뭉뚱그림, 위치 클러스터링 오류(다른 주차칸 혼합)는 미반영.

### 2.7.2 실제 효과 크기 범위 추정 (문헌 → 분산 파라미터) + 재시뮬레이션 (`sim_crossed_car.py`)

**문헌 수치**
| 항목 | 수치 | 출처 |
|---|---|---|
| 햇볕 vs 그늘 (1시간 주차, 애리조나 맑은 날) | 실내 공기 평균 116 °F vs 약 100 °F → **약 9 °C 차** (대시보드 157 vs 118 °F) | Vanos 2018 |
| 맑은 날 vs 흐린 날 최고 실내 온도 | 흐린 날이 평균 **약 10 °C 낮음**, 최고 41~76 °C | Grundstein 2009 |
| 햇볕 아래 평형 온도 | 외기 대비 **+20~35 K**, 평형 50% 도달 **10~30분** | Horak 2016 |
| 물리 모델 + 기상관측소 시간자료 오차 | RMSE **≈0.5 K**(관측소와 현장 하늘이 일치할 때). 현장이 흐린데 관측소가 맑으면 과대추정(최대 오차 사례) | Horak 2016 |
| 차량 간 차이 | 흰색 61 °C vs 검정 80 °C(시뮬레이션), 유리 투과율 ±0.07 → 약 1 K, 틴팅 → 최대 5 K | Horak 2016 |
| 겨울 차고 vs 실외 | 비단열 차고 +3~8 °C, 단열 +8~14 °C | 특허·2차 자료 (약한 근거) |

**분산 파라미터로 옮기기 (가정, 내 판단)**
- σu(실외 주차 위치 간, 맑은 날): 햇볕-그늘 극단 차 약 9 °C → 부분 그늘이 섞인 분포에서 SD **2~3 °C**.
- σe(이벤트 잡음): 물리 모델 자체 오차는 0.5 K로 작지만 현장-관측소 구름 불일치, 주차 시간 30분 미만(평형 전), 창문 개방, 센서 위치 차가 더해짐 → **3~4 °C**.
- σc(차량 효과): 색상·틴팅·센서 위치 → **4~5 °C. 위치 효과보다 클 수 있음** (이번 추정의 새 위험).
- 유효 이벤트 비율: 위치 효과는 일사가 있을 때만 드러남(ΔT = s·u, 흐림·야간·짧은 주차 s≈0). 낮 1시간 이상 + 맑음 비율을 **25~35%**로 가정.
- 겨울 야간(실외 위치 간 차: 바람막이·하늘 노출에 따른 복사냉각): σu 1~2 °C, σe 1.5 °C — **근거 약함(일반 지식 + 2차 자료)**.

**재시뮬레이션 결과** (클러스터 40, 차량 100대, 차 1대당 3개 위치 방문, 200회 반복; 기준 = 평균 상관 ≥ 0.8)
| 시나리오 | 차량 효과 무시 | 차량 효과 같이 추정 |
|---|---|---|
| 여름 현실 (u=3, e=3, car=4, 유효 35%) | 80회에도 미달(0.78) | **클러스터당 15회** (하위10% 기준 20회) |
| 여름 비관 (u=2, e=4, car=5, 유효 25%) | 미달(0.53) | 80회 |
| 겨울 야간 (u=1.5, e=1.5, car=0.5, 유효 60%) | 10회 | 10회 |

해석:
- **물리적 효과 자체는 충분히 크다.** 햇볕-그늘 9 °C 대 모델 잡음 0.5~4 °C → 신호대잡음 문제는 아님.
- **진짜 문제는 차량 효과.** 무시하면 이벤트를 아무리 늘려도 0.8에 도달 못 함. 같은 차가 여러 위치를, 같은 위치를 여러 차가 오가는 **교차 구조**가 있어야 분리됨 → 군집 데이터가 필요한 핵심 이유가 "평균으로 잡음 줄이기"가 아니라 "차량 효과 분리"로 바뀜.
- **개인 비교는 차량 효과가 상쇄됨.** 한 사람이 자기 차로 다닌 위치끼리 비교할 때는 c_j가 공통이라 사라짐. 군집은 "가 보지 않은 위치"를 예측할 때 필요.
- 여름 현실 15회/클러스터 = 유효(맑은 낮) 이벤트 약 5회.
- **정정 (추가 검증):** "차 1대만 쓰는 위치는 분리 불가"는 틀림. 그 차가 다른 공용 위치(직장·마트)도 다니면, 거기서 다른 차들과 비교해 차량 효과가 추정되고 집 위치로 옮겨짐. 검증: 공용 20곳 + 차 1대 전용 집 20곳, 차 20대 → 집 위치 복원 상관 n=15회 0.83, n=20회 0.88 (보정 안 하면 0.43~0.48). 조건은 "위치마다 차 여러 대"가 아니라 **"차·위치 방문 관계가 하나로 연결돼 있을 것"**. 정말 한 곳만 다니는 차만 보정 불가.
- 차종·트림은 `getVINInfo`로 알 수 있어 차량 효과의 사전값(같은 차종끼리 묶기)으로 쓰면 필요 이벤트가 더 줄 수 있음(미검증).
- 미반영 위험: 그늘은 시간·계절에 따라 움직임(u_k가 시각대별로 다름 → 필요 데이터 3~4배), 지하주차장 vs 실외처럼 **사용자가 이미 아는 대비가 분산 대부분을 차지**(새로움·개인 가치 약화), 센서 의미 미확인.

출처: https://www.tandfonline.com/doi/full/10.1080/23328940.2018.1468205 · https://www.researchgate.net/publication/24032501 · https://d-nb.info/110720741X/34 (Horak et al. 2016, Int J Biometeorol) · https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/11276303

### 2.8 위험·미확인
- `TemperatureCurrent`가 실내 공기 온도 센서인지, 공조 ON일 때만 유효한 덕트/흡입 온도인지 **문서에 명시 없음**(설명은 "internal temperature"). 시동 전 값이 안 읽히면 ΔT0는 무효 → r5·t_reach로 후퇴.
- 센서 위치에 따라 일사 직격 시 과대 판독 가능, 갱신 주기·null 반환 조건 미확인.
- 겨울 실내 온도 상승은 히터 방식(PTC/히트펌프)·배터리 상태에 의존 → 차종 보정 필요.
- 서리 실측은 여전히 없음(제상 사용은 행동 지표일 뿐).
- 위치·동승 정보 사용 시 동의·집계·최소 인원 기준(보안 가이드).

## 3. 2차 출력: 트렁크 활동 지도
- 같은 위치 클러스터에서 트렁크 개폐·도어·체류(ACC/시동 OFF 시간) 이벤트를 집계 → 주차 위치별 활동 지도.
- 위치·동승 정보는 동의·집계·최소 인원 기준 필요(보안 가이드).

## 3.5 SDK 신호 가용성 (문서 확인, 2026-10-26)

출처: `document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/entry` 및 `.../h-v-a-c/entry`, `.../ev-battery/entry`. 메서드 개별 페이지는 본문이 비어 있어(WebFetch로는 이름·내비만 보임) 아래 의미는 **메서드 이름에서 추정**한 것.

| 필요한 신호 | SDK 후보 | 상태 |
|---|---|---|
| 실내 온도 | `getHvacTemperatureCurrent` / `registerHvacTemperatureCurrent` (`checkHvacTemperatureCurrentCapabilities`) | 이름상 "현재 온도". 실내인지 덕트인지 문서로 미확인 |
| 외기온 | Vehicle API 19개 클래스에 없음 | **없음**. (클라우드 Vehicle Data API 응답에는 `outsideTemperature` 필드가 있으나 별개 API) → 기상청 데이터로 대체 |
| 일사량 | 없음 | 기상청 일사/운량 데이터로 대체 |
| 공조 에너지(전력) | 없음. HVAC `...Power...`는 ON/OFF 상태 | **직접 신호 없음** |
| 공조 상태 | `getHvacAcState`, `getHvacFanSpeed`, `getHvacTemperature`(설정), `getHvacDefrosterState`, `getHvacMaxAcState` 등 + register 콜백 | 있음 (A/C 컴프레서 상태 여부는 불명) |
| 배터리 에너지 | EvBattery에 `getEvBatteryStateOfCharge`(SOC)만. kWh 절대값 getter 없음 | SOC(%)만. Virtual Console에는 Battery Energy(kWh) 카드가 있으나 SDK 메서드는 미확인 |
| 전비 | `get/registerInstantaneousEvEfficiency`, `CurrentEvEfficiency`, `SinceChargeEvEfficiency`, `CumulativeEvEfficiency` | 있음 (단위 미확인) |
| 속도 | 19개 클래스에 없음 (Virtual Console 속도 카드도 없음, 속도 권한은 존재) | 불명 |
| 시동/주차 상태 | 별도 클래스 없음. `DrivingMode`·`Door`·`Seat`·`Odometer` 등으로 추정 | 미확인 |

**내장 브라우저로 메서드 페이지 확인 (본문은 화면 캡처로 읽음, 쿠키 배너가 일부 가려 문장 앞부분은 잘림):**

| 메서드 | 확인된 내용 |
|---|---|
| `getHvacTemperatureCurrent(area: SeatArea, onComplete: (Float?))` | "vehicle의 현재 **실내(internal) 온도**, °C". 좌석 area별. 권한 `CAR_CLIMATE`(normal) |
| `getInstantaneousEvEfficiency(onComplete: (Float?))` | 순간 전비, **km/kWh**. 향후 AAOS 표준 속성 `INSTANTANEOUS_EV_EFFICIENCY`로 대체되며 deprecated 예정. 권한 `CAR_ENERGY`(normal) |
| `getSinceChargeEvEfficiency(Float?)` | 마지막 충전 이후 전비. 단위는 km/kWh 계열(문장 일부 가려짐) |
| `getEvBatteryStateOfCharge(onComplete: (Int?))` | SOC **정수 %** (1% 단위). 권한 `CAR_ENERGY` |

시사점 (확인 반영):
- 실내 온도는 **좌석 area별 °C Float**로 확정 → 실내 온도 곡선 신호 사용 가능. 구역별 차이(운전석 vs 뒷좌석 일사 노출)도 볼 수 있음.
- SOC는 **정수 %**. 배터리 용량 약 70~80 kWh로 가정(추정)하면 1% ≈ 0.7~0.8 kWh → **여름 0.1~0.3 kWh 효과는 SOC로 직접 못 잡음**. 겨울 수백 Wh~1 kWh도 한 번에는 1칸 안팎이라 군집 평균으로만 가능.
- 순간 전비(km/kWh)는 정차·저속에서 발산(0 나눔)하므로 시동 직후 정차 구간 에너지 추정에 부적합. 주행 중 전비로 에너지 환산하려면 속도가 필요(속도 신호 불명).
- 따라서 **주신호를 에너지가 아닌 실내 온도 곡선(상승률·냉각 시간)으로 옮기는 편이 현실적**. 에너지는 보조.

시사점 (이전):
- 잔차 신호의 핵심인 **공조 에너지를 직접 못 읽음** → 시동 직후 구간의 SOC 감소(또는 전비 신호) - 구동 모델로 간접 추정해야 함. SOC 분해능이 거칠면 0.1~0.3 kWh(여름)급 효과는 못 잡을 수 있음. 겨울(수백 Wh~1 kWh)은 가능성 있음.
- 대안/보조 신호: 실내 온도 상승·강하 곡선(`TemperatureCurrent`), 설정 온도 도달 시간, 팬 단계·A/C 상태 로그.
- 외기온·일사량은 기상청으로 보정.
- 속도 신호가 불명이라 구동 모델 구축에 `FusedLocation`(위치 차분) 필요할 수 있음.

## 3.6 에뮬레이터 / 이벤트 신호 확인 (문서, 2026-10-26)

**Virtual Console (문서 기준)**
- Climate 카드: HVAC Power, Air Conditioning, HVAC Auto, HVAC Dual, Air Recirculation, Fan Speed(0~8), Fan Direction(Face/Floor/Defrost), Defroster(전면/후면), Target Temperature(16~30 °C, 0.5 °C 단위, 운전석/동승석), Seat Temperature(통풍 3단/Off/열선 3단), Air Cleaner.
- **현재(측정) 실내 온도를 넣는 카드는 없음.** 외기온 카드도 없음. → `getHvacTemperatureCurrent`에 값을 주입하는 공식 방법이 Virtual Console에는 없다. 에뮬레이터가 이 속성에 기본값을 주는지, 값이 움직이는지는 **실제로 띄워 봐야** 앎.
- 미검증 우회(AAOS 일반 지식, Pleos 에뮬레이터 적용 여부 미확인): `adb shell cmd car_service inject-vhal-event <HVAC_TEMPERATURE_CURRENT 속성 ID> <area> <값>`로 VHAL 이벤트 주입. 속성 ID·area 값은 에뮬레이터에서 `dumpsys car_service`로 먼저 확인.
- 위치만 `adb emu geo fix <lon> <lat>`로 주입 가능. 속도 카드 없음. 에뮬레이터 제약: set 성공해도 get 값이 안 바뀔 수 있음.
- 결론: **실내 온도 곡선은 에뮬레이터에서 앱 로직(콜백→로그→이벤트 창)까지만 검증 가능하고, 곡선의 물리적 정확성은 못 본다.** 곡선 통계 검증은 합성 데이터(2.7.1) 몫.

**시동 이벤트 신호**
- SDK에 **시동(ignition)·기어·주차 브레이크 메서드를 찾지 못함.** Virtual Console에는 Gear Selection(P/R/N/D) 카드가 있으나 대응 SDK 메서드는 확인 못함.
- 사용 가능한 후보 신호(이름 기준, 동작은 미확인): `registerHvacPowerState`, `registerSeatOccupancy`(운전석 Empty→Occupied), `registerDoorPosition`, `registerBrakePedalStatus`, `registerDrivingModeState`/`getCurrentDrivingModeState`, `registerSeatBeltBuckledState`, Odometer(`registerDistanceDriven`, Trip 시간/거리), HVAC 상태 콜백들.
- 트렁크 전용 메서드는 Door 클래스에 없음(Virtual Console엔 트렁크 카드 있음) → 도어 area에 트렁크가 포함되는지 `checkDoorPositionCapabilities`로 확인 필요. 2차 출력(트렁크 지도)의 전제.

## 3.7 이벤트 로깅 앱 최소 구조

목표: 주차 이벤트마다 §2.3 특징량 계산에 필요한 원시 로그만 남긴다. 분석·날씨 결합·모델은 서버/노트북에서.

**구성 요소**
1. `VehicleClient`: `initialize()` / `release()` 수명 관리, `Vehicle`에서 `getHvac()`, `getSeat()`, `getDoor()`, `getDrivingMode()`, `getBrake()`, `getEvBattery()` 획득(도메인 객체 직접 생성 금지). `release()`는 비동기 콜백이 끝난 뒤.
2. `CapabilityGate`: 시작 시 `check*Capabilities`(HvacTemperatureCurrent, SeatOccupancy, DoorPosition, HvacDefroster 등)로 사용 가능 신호를 확정하고 차종 정보와 함께 기록. 미지원 신호는 특징량에서 제외(§2.8 후퇴 경로).
3. `SignalSources`: 콜백 등록(`register*`) + 폴링 병행. 모든 get/set은 IPC 비동기(`onComplete`/`onFailed`). 갱신 주기 미확인이므로 on-change 기록 + 5초 폴링으로 시작하고 실측 후 조정.
4. `EventDetector`(상태기계): 아래 참조.
5. `SessionRecorder`: 이벤트 창 15분 동안 샘플을 메모리에 쌓다 종료 시 한 번에 저장.
6. `Store`: 로컬 JSON Lines(또는 Room). 업로드·동의 처리는 후속 단계.
7. `LocationTag`: 이벤트 시작 시 위치 1점(FusedLocation, `isSupported` 확인; 상태 불일치 있음). 정밀 좌표 대신 클러스터링용 지오해시 저장 고려(동의·집계 기준).

**이벤트 상태기계 (시동 신호 부재를 우회)**

    IDLE
      └─(운전석 도어 열림 또는 SeatOccupancy[운전석]=Occupied) → ARRIVED   // ΔT0용 첫 실내 온도 샘플
    ARRIVED
      └─(HvacPower ON 또는 BrakePedal 눌림 또는 DrivingMode 변화) → RECORDING(t0)
      └─(60초 내 위 신호 없음) → IDLE (취소)
    RECORDING
      └─(t0+15분 경과 또는 운전석 Empty) → FLUSH → IDLE

**기록 스키마 (샘플 한 줄)**

    ts, event_id, vehicle_model, area(SeatArea), temp_current_C,
    hvac: power, ac, auto, recirc, fan_speed, target_C, defroster_front/rear, seat_temp/vent,
    driving_mode, brake_pedal, door_positions, seat_occupancy, soc_pct, odometer_dist,
    (이벤트 헤더) t0, arrived_ts, loc_geohash, capability_flags

**최소 완성 기준**
- 에뮬레이터에서: 콜백 수신 → 상태기계 전이 → 15분 창 → JSONL 저장이 한 사이클 돈다.
- Virtual Console로 HVAC 상태·도어·좌석 점유를 조작해 전이를 재현한다(실내 온도 값은 불가, 위 우회 시도 또는 더미 값).

## 3.8 inject-vhal-event 우회 주입 확인 절차

> 아래 명령·ID는 AAOS 일반 지식 기반이며 Pleos 에뮬레이터에서의 동작은 **미검증**. 각 단계에 합격/불합격 기준을 둔다. 불합격 지점이 곧 우회 불가 사유.

사전: Pleos Connect Emulator(API 34, Cold boot) 기동, `adb devices`에 에뮬레이터 표시.

| 단계 | 명령 | 합격 기준 | 불합격 시 |
|---|---|---|---|
| 0. 연결 | `adb devices` | `emulator-xxxx  device` | 에뮬레이터 재기동(Cold boot) |
| 1. car_service 존재 | `adb shell cmd car_service help` | `inject-vhal-event` 항목이 도움말에 있음 | 이 우회 불가 → 4번(앱 쪽 모의) |
| 2. HVAC 속성 목록 | `adb shell cmd car_service get-carpropertyconfig` (출력에서 HVAC 항목 검색) | `HVAC_TEMPERATURE_CURRENT`(AAOS 표준 ID 358614275 = 0x15600503)가 목록에 있고 area ID가 나옴 | 속성 없음 → 에뮬레이터가 이 신호 미지원, Pleos `getHvacTemperatureCurrent`는 `checkHvacTemperatureCurrentCapabilities`로 미지원 판정될 것 |
| 3. 읽기 기준값 | `adb shell cmd car_service get-property-value 0x15600503 <area>` | 값이 반환됨(기본값 기록) | 읽기 불가 → 속성이 읽기 비허용일 수 있음 |
| 4. 주입 | `adb shell cmd car_service inject-vhal-event 0x15600503 <area> 31.5` | 명령 오류 없음 | 형식 오류 → help의 인자 순서 확인(`property [zone] data`) |
| 5. 반영 확인 | 3단계 명령 재실행 | 값이 31.5로 바뀜(또는 그 값을 낸 이벤트가 로그에 찍힘) | 안 바뀜 → 알려진 제약(set은 성공해도 get 불변). 6번으로 |
| 6. SDK 경유 확인 | 로거 앱(또는 샘플 앱)에서 `checkHvacTemperatureCurrentCapabilities` → `getHvacTemperatureCurrent(area)` + `registerHvacTemperatureCurrent` | 콜백이 31.5(°C)를 수신 | 값이 안 오면 우회는 AAOS 층에서만 되고 Pleos SDK 층에선 무용 |
| 7. 곡선 재현 | 주입을 반복(예: 5초마다 0.5 °C 하강)하며 6번 콜백 로그 확인 | 콜백이 각 변화를 1회씩 받음, 지연·누락 정도 기록 | 일부 누락 → 폴링 병행 필수 |

기록할 것: 각 단계 결과, area ID 목록(SeatArea와의 대응), 콜백 지연, 갱신 주기, 값 범위·해상도.

판정:
- 6단계 통과 = 앱 로직 + 곡선 입력을 에뮬레이터에서 end-to-end로 재현 가능 → 합성 곡선을 에뮬레이터로 주입해 로거를 검증.
- 5단계에서 멈춤 = AAOS 층에서 주입 불가 → 로거는 `SignalSources`를 인터페이스로 분리해 **가짜 구현(FakeVehicleSource)**으로 곡선을 공급해 검증(앱 로직만).
- 2단계에서 속성 없음 = 이 신호는 현 에뮬레이터 이미지에서 검증 불가. 실차/다른 이미지 필요.

## 4. 열린 질문
- SDK에서 실내 온도 / 외기온 / 일사량 신호를 읽을 수 있는가 (`checkXXXCapabilities`로 확인).
- 에뮬레이터는 속도·위치 카드가 없고 `adb emu geo fix`로 위치만 주입 가능 → 합성 데이터로 파이프라인 검증.
- 서리 쪽은 측정 문헌 부재 → 직접 데이터 아니면 근거 약함.
