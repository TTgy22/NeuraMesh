import { useCallback, useEffect, useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api, type ChainStats, type NodeStatus } from "../api";

const DEMO_MODELS = ["Jetson-Orin", "RTX-4090", "Mac-Studio-M2", "Jetson-Nano",
  "RTX-3060", "Ryzen-7950X", "M3-Max", "A100-40G"];

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 8, padding: "var(--space-4)" }}>
      <div style={{ color: "var(--muted)", fontSize: 12, letterSpacing: 1 }}>{label}</div>
      <div className="display" style={{ fontSize: 30, color: "var(--accent)" }}>{value}</div>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactElement }) {
  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div className="display" style={{ marginBottom: "var(--space-2)" }}>{title}</div>
      <ResponsiveContainer width="100%" height={220}>{children}</ResponsiveContainer>
    </div>
  );
}

export function Overview() {
  const [stats, setStats] = useState<ChainStats | null>(null);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [s, n] = await Promise.all([api.stats(), api.nodeList()]);
      setStats(s); setNodes(n); setErr(null);
    } catch (e) { setErr((e as Error).message); }
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

  const weightData = nodes.map((n) => ({ id: n.nodeId.slice(2, 8), weight: Number(n.totalWeight.toFixed(1)) }));
  const earnData = nodes.map((n) => ({ id: n.nodeId.slice(2, 8), earned: n.totalEarned }));

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 className="display">网络总览</h1>
        <button onClick={seed} disabled={busy}
          style={{ background: "var(--accent)", border: "none", color: "oklch(15% 0.02 200)", borderRadius: 6,
            padding: "var(--space-2) var(--space-4)", cursor: "pointer", fontWeight: 600 }}>
          {busy ? "接入中…" : "接入 8 台演示设备"}
        </button>
      </div>
      {err && <div style={{ color: "var(--danger)", marginTop: 8 }}>后端未连接（{err}）。请先启动 :8080。</div>}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: "var(--space-3)", margin: "var(--space-4) 0" }}>
        <Stat label="区块高度" value={stats?.blockHeight ?? 0} />
        <Stat label="节点数" value={stats?.nodeCount ?? 0} />
        <Stat label="全网权重" value={stats ? stats.totalWeight.toFixed(0) : 0} />
        <Stat label="累计收益 NMT" value={stats?.totalEarned ?? 0} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
        <ChartCard title="节点权重分布">
          <BarChart data={weightData}>
            <CartesianGrid stroke="var(--grid)" strokeDasharray="2 4" />
            <XAxis dataKey="id" stroke="var(--muted)" fontSize={11} />
            <YAxis stroke="var(--muted)" fontSize={11} />
            <Tooltip contentStyle={{ background: "var(--panel-2)", border: "1px solid var(--border)", color: "var(--text)" }} />
            <Bar dataKey="weight" radius={[2, 2, 0, 0]}>
              {weightData.map((_, i) => <Cell key={i} fill="var(--accent)" />)}
            </Bar>
          </BarChart>
        </ChartCard>
        <ChartCard title="节点收益排行">
          <BarChart data={earnData}>
            <CartesianGrid stroke="var(--grid)" strokeDasharray="2 4" />
            <XAxis dataKey="id" stroke="var(--muted)" fontSize={11} />
            <YAxis stroke="var(--muted)" fontSize={11} />
            <Tooltip contentStyle={{ background: "var(--panel-2)", border: "1px solid var(--border)", color: "var(--text)" }} />
            <Bar dataKey="earned" radius={[2, 2, 0, 0]}>
              {earnData.map((_, i) => <Cell key={i} fill="var(--success)" />)}
            </Bar>
          </BarChart>
        </ChartCard>
      </div>

      {nodes.length === 0 && (
        <p style={{ color: "var(--muted)", marginTop: "var(--space-4)" }}>
          暂无节点数据。点击右上角「接入 8 台演示设备」生成数据（需后端运行）。
        </p>
      )}
    </div>
  );
}