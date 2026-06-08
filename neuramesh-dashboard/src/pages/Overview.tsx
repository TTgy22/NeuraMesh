import { useCallback, useEffect, useRef, useState } from "react";
import { api, type ChainStats } from "../api";
import { NetworkKPIs } from "../components/KPICard";
import { ThroughputChart } from "../components/ThroughputChart";
import { LevelDistributionChart } from "../components/LevelDistributionChart";
import { ActivityStream } from "../components/ActivityStream";
import { WeightEarningsChart } from "../components/WeightEarningsChart";

const DEMO_MODELS = ["Jetson-Orin", "RTX-4090", "Mac-Studio-M2", "Jetson-Nano",
  "RTX-3060", "Ryzen-7950X", "M3-Max", "A100-40G"];

// 数字滚动动画：值变化时在 ~600ms 内平滑过渡。
function useCountUp(target: number, ms = 600) {
  const [val, setVal] = useState(0);
  const ref = useRef({ from: 0, start: 0, raf: 0 });
  useEffect(() => {
    const s = ref.current; s.from = val; s.start = performance.now();
    cancelAnimationFrame(s.raf);
    const tick = (t: number) => {
      const p = Math.min(1, (t - s.start) / ms);
      const eased = 1 - Math.pow(1 - p, 3);
      setVal(s.from + (target - s.from) * eased);
      if (p < 1) s.raf = requestAnimationFrame(tick);
    };
    s.raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(s.raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);
  return val;
}

function Hero({ stats }: { stats: ChainStats | null }) {
  const height = useCountUp(stats?.blockHeight ?? 0);
  const nodes = useCountUp(stats?.nodeCount ?? 0);
  const weight = useCountUp(stats?.totalWeight ?? 0);
  const earned = useCountUp(stats?.totalEarned ?? 0);
  const items = [
    { label: "区块高度", value: Math.round(height).toLocaleString(), unit: "" },
    { label: "在线节点", value: Math.round(nodes).toLocaleString(), unit: "" },
    { label: "全网权重", value: Math.round(weight).toLocaleString(), unit: "" },
    { label: "累计收益", value: Math.round(earned).toLocaleString(), unit: "NMT" },
  ];
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: "var(--space-3)" }}>
      {items.map((it, i) => (
        <div key={it.label} className="hero-card" style={{ position: "relative", overflow: "hidden",
          background: "linear-gradient(135deg, var(--panel), var(--panel-2))",
          border: "1px solid var(--border)", borderRadius: 12, padding: "var(--space-4)",
          animation: `hero-in 500ms ease both`, animationDelay: `${i * 70}ms` }}>
          <div style={{ color: "var(--muted)", fontSize: 12, letterSpacing: 1 }}>{it.label}</div>
          <div className="display" style={{ fontSize: 32, color: "var(--accent)", lineHeight: 1.2 }}>
            {it.value}<span style={{ fontSize: 12, color: "var(--muted)", marginLeft: 4 }}>{it.unit}</span>
          </div>
          <div className="hero-glow" />
        </div>
      ))}
    </div>
  );
}

export function Overview() {
  const [stats, setStats] = useState<ChainStats | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try { setStats(await api.stats()); setErr(null); }
    catch (e) { setErr((e as Error).message); }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 4000);
    return () => clearInterval(t);
  }, [refresh]);

  async function seed() {
    setBusy(true);
    for (const m of DEMO_MODELS) { try { await api.registerNode(m); } catch { /* 后端未启动 */ } }
    await refresh();
    setBusy(false);
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h1 className="display" style={{ marginBottom: 2 }}>网络总览</h1>
          <div style={{ color: "var(--muted)", fontSize: 13 }}>去中心化边缘智算网络 · 实时态势</div>
        </div>
        <button onClick={seed} disabled={busy} className="cta"
          style={{ background: "var(--accent)", border: "none", color: "oklch(15% 0.02 200)", borderRadius: 8,
            padding: "var(--space-2) var(--space-4)", cursor: "pointer", fontWeight: 600,
            transition: "transform 150ms ease, filter 150ms ease" }}>
          {busy ? "接入中…" : "接入 8 台演示设备"}
        </button>
      </div>
      {err && <div style={{ color: "var(--danger)", marginTop: 8 }}>后端未连接（{err}）。请先启动 :8080。</div>}

      <div style={{ marginTop: "var(--space-4)" }}><Hero stats={stats} /></div>

      <div style={{ marginTop: "var(--space-4)" }}><NetworkKPIs /></div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "var(--space-3)", marginTop: "var(--space-3)" }}>
        <ThroughputChart />
        <LevelDistributionChart />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: "var(--space-3)", marginTop: "var(--space-3)", alignItems: "start" }}>
        <WeightEarningsChart />
        <ActivityStream />
      </div>

      <style>{`
        @keyframes hero-in { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
        .hero-card:hover { border-color: var(--accent-dim); }
        .hero-glow { position: absolute; right: -30px; top: -30px; width: 90px; height: 90px; border-radius: 50%;
          background: radial-gradient(circle, oklch(74% 0.10 200 / 0.18), transparent 70%); pointer-events: none; }
        .cta:hover { transform: translateY(-1px); filter: brightness(1.08); }
      `}</style>
    </div>
  );
}
