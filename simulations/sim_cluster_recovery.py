"""합성 데이터로 주차 클러스터 랜덤효과 u_k 복원 가능성 시뮬레이션.

모델 (NOTES_parking_microclimate.md 2.5):
    dT0_ik = b0 + b1*일사량 + b2*외기온 + b3*풍속 + u_k + c_car + e_ik

모든 분산 값은 가정이다(문헌값 아님). 시나리오로 민감도를 본다.
"""
import warnings
import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")

K = 40            # 클러스터 수
REPS = 200        # 반복 수
N_GRID = [2, 3, 4, 5, 6, 8, 10, 15, 20, 30, 50]
TARGET_CORR = 0.80  # "복원됨" 기준: corr(u_hat, u_true) >= 0.8

# name: (sigma_u, sigma_eps, sigma_car, cars_per_cluster)
SCENARIOS = {
    "A 기준 (u=2, e=3)":                (2.0, 3.0, 0.0, 1),
    "B 비관 (u=1, e=4)":                (1.0, 4.0, 0.0, 1),
    "C 센서편차, 차 1대/클러스터":       (2.0, 3.0, 1.0, 1),
    "D 센서편차, 차 3대/클러스터":       (2.0, 3.0, 1.0, 3),
}
BETA = dict(b0=0.0, irr=0.004, tamb=0.5, wind=-0.6)  # 일사 W/m2, 외기 degC, 풍속 m/s


def simulate(rng, n_per, su, se, sc, cars):
    rows = []
    u = rng.normal(0, su, K)
    for k in range(K):
        car_off = rng.normal(0, sc, cars)
        n_k = max(1, rng.poisson(n_per))  # 클러스터마다 이벤트 수 불균형
        for _ in range(n_k):
            irr = rng.uniform(100, 900)
            tamb = rng.uniform(20, 38)
            wind = rng.uniform(0, 6)
            car = rng.integers(cars)
            y = (BETA["b0"] + BETA["irr"] * irr + BETA["tamb"] * tamb
                 + BETA["wind"] * wind + u[k] + car_off[car] + rng.normal(0, se))
            rows.append((k, irr, tamb, wind, y))
    return pd.DataFrame(rows, columns=["k", "irr", "tamb", "wind", "y"]), u


def fit_uhat(df):
    """2단계 모멘트 추정 (REML 혼합모형의 근사). statsmodels MixedLM은 특이행렬로 자주 실패해 교체.

    1) 클러스터 평균을 뺀 within 회귀로 날씨 계수 추정
    2) 잔차의 클러스터 평균 m_k를 분산 성분으로 수축해 BLUP 형태의 u_hat 산출
    """
    X = df[["irr", "tamb", "wind"]].to_numpy(float)
    y = df["y"].to_numpy(float)
    g = df["k"].to_numpy()
    cnt = np.bincount(g, minlength=K).astype(float)
    Xm = np.vstack([np.bincount(g, X[:, j], K) / np.maximum(cnt, 1) for j in range(3)]).T
    ym = np.bincount(g, y, K) / np.maximum(cnt, 1)
    beta, *_ = np.linalg.lstsq(X - Xm[g], y - ym[g], rcond=None)
    r = y - X @ beta
    m = np.bincount(g, r, K) / np.maximum(cnt, 1)
    present = cnt > 0
    dof = len(y) - present.sum() - 3
    s2e = np.sum((r - m[g]) ** 2) / max(dof, 1)
    grand = np.average(m[present], weights=cnt[present])
    s2u = max(np.var(m[present], ddof=1) - np.mean(s2e / cnt[present]), 0.0)
    w = np.where(present, s2u / (s2u + s2e / np.maximum(cnt, 1)), 0.0)
    return w * (m - grand)


def run():
    rng = np.random.default_rng(42)
    out = []
    for name, (su, se, sc, cars) in SCENARIOS.items():
        for n in N_GRID:
            corrs, rmses = [], []
            for _ in range(REPS):
                df, u = simulate(rng, n, su, se, sc, cars)
                try:
                    uh = fit_uhat(df)
                except Exception:
                    continue
                c = np.corrcoef(uh, u)[0, 1]
                corrs.append(0.0 if np.isnan(c) else c)
                rmses.append(np.sqrt(np.mean((uh - u) ** 2)))
            out.append((name, n, np.mean(corrs), np.percentile(corrs, 10), np.mean(rmses) / su, len(corrs)))
    res = pd.DataFrame(out, columns=["scenario", "n_events", "corr_mean", "corr_p10", "rmse_over_su", "ok"])
    return res


if __name__ == "__main__":
    res = run()
    pd.set_option("display.width", 140)
    for name, g in res.groupby("scenario", sort=False):
        print(f"\n{name}")
        print(g[["n_events", "corr_mean", "corr_p10", "rmse_over_su", "ok"]].round(2).to_string(index=False))
        hit = g[g["corr_p10"] >= TARGET_CORR]
        mean_hit = g[g["corr_mean"] >= TARGET_CORR]
        n_mean = int(mean_hit["n_events"].iloc[0]) if len(mean_hit) else "도달 못함"
        n_p10 = int(hit["n_events"].iloc[0]) if len(hit) else "도달 못함"
        print(f"  평균 corr>={TARGET_CORR}: N_min = {n_mean} | 하위10% corr>={TARGET_CORR}: N_min = {n_p10}")
    res.to_csv("sim_cluster_recovery_results.csv", index=False)
