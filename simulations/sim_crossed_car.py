"""문헌 기반 파라미터로 '위치 효과 u_k'를 '차량 효과 c_j'와 분리해 복원할 수 있는지 시뮬레이션.

sim_cluster_recovery.py와의 차이:
- 차량 효과가 위치 효과보다 클 수 있다(색상·틴팅·센서 위치). 차량은 여러 위치를 방문(교차 설계).
- 위치 효과는 일사가 있을 때만 드러난다: dT0 = s_i * u_k + c_j + e  (s_i: 일사 노출 계수 0~1)
  흐림·야간·짧은 주차 이벤트는 s_i ≈ 0 → 정보 없음.
- 날씨 고정효과는 앞 시뮬레이션에서 제거 가능함을 확인했으므로 여기서는 생략.

분산 값은 문헌 수치에서 거칠게 옮긴 가정이다(NOTES 2.7.2 참조).
"""
import numpy as np
import pandas as pd

K = 40             # 위치 클러스터
J = 100            # 차량 수
SPOTS_PER_CAR = 3  # 차 한 대가 드나드는 클러스터 수 (집·직장·자주 가는 곳)
REPS = 200
N_GRID = [5, 10, 15, 20, 30, 50, 80]
TARGET_CORR = 0.80
ITERS = 30

# name: (sigma_u, sigma_e, sigma_c, informative_fraction)
SCENARIOS = {
    "여름 현실 (u=3, e=3, car=4, 유효 35%)": (3.0, 3.0, 4.0, 0.35),
    "여름 비관 (u=2, e=4, car=5, 유효 25%)": (2.0, 4.0, 5.0, 0.25),
    "겨울 야간 (u=1.5, e=1.5, car=0.5, 유효 60%)": (1.5, 1.5, 0.5, 0.60),
}


def simulate(rng, n_per, su, se, sc, frac):
    u = rng.normal(0, su, K)
    c = rng.normal(0, sc, J)
    pools = [[] for _ in range(K)]
    for j in range(J):
        for k in rng.choice(K, SPOTS_PER_CAR, replace=False):
            pools[k].append(j)
    rows = []
    for k in range(K):
        if not pools[k]:
            continue
        for _ in range(max(1, rng.poisson(n_per))):
            j = rng.choice(pools[k])
            s = rng.uniform(0.6, 1.0) if rng.random() < frac else rng.uniform(0.0, 0.1)
            rows.append((k, j, s, s * u[k] + c[j] + rng.normal(0, se)))
    df = pd.DataFrame(rows, columns=["k", "j", "s", "y"])
    return df, u


def fit(df, su, se, sc, model_car):
    """교대 최소제곱 + 수축(오라클 분산비). model_car=False면 차량 효과를 무시."""
    k = df["k"].to_numpy(); j = df["j"].to_numpy()
    s = df["s"].to_numpy(); y = df["y"].to_numpy()
    y = y - y.mean()
    lu, lc = se**2 / su**2, se**2 / sc**2
    uh = np.zeros(K); ch = np.zeros(J)
    for _ in range(ITERS if model_car else 1):
        r = y - ch[j]
        uh = np.bincount(k, s * r, K) / (np.bincount(k, s * s, K) + lu)
        if model_car:
            r = y - s * uh[k]
            ch = np.bincount(j, r, J) / (np.bincount(j, minlength=J) + lc)
    return uh


def run():
    rng = np.random.default_rng(7)
    out = []
    for name, (su, se, sc, frac) in SCENARIOS.items():
        for n in N_GRID:
            for model_car in (False, True):
                cs = []
                for _ in range(REPS):
                    df, u = simulate(rng, n, su, se, sc, frac)
                    uh = fit(df, su, se, sc, model_car)
                    cc = np.corrcoef(uh, u)[0, 1]
                    cs.append(0.0 if np.isnan(cc) else cc)
                out.append((name, n, "차량효과 모델링" if model_car else "무시",
                            np.mean(cs), np.percentile(cs, 10)))
    return pd.DataFrame(out, columns=["scenario", "n_events", "car", "corr_mean", "corr_p10"])


if __name__ == "__main__":
    res = run()
    for name, g in res.groupby("scenario", sort=False):
        print(f"\n{name}")
        wide = g.pivot(index="n_events", columns="car", values=["corr_mean", "corr_p10"]).round(2)
        print(wide.to_string())
        for car, gg in g.groupby("car"):
            hit = gg[gg["corr_mean"] >= TARGET_CORR]
            print(f"  [{car}] 평균 corr>={TARGET_CORR}: N_min =",
                  int(hit["n_events"].iloc[0]) if len(hit) else "도달 못함")
    res.to_csv("sim_crossed_car_results.csv", index=False)
