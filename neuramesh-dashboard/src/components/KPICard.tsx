import { useEffect, useRef, useState } from "react";
import { api, type ChainStats, type NodeStatus } from "../api";

// tufte-dataink：高数据墨水比，大数字 Space Grotesk，单位 muted 小字。
export function KPICard({ label, value, unit, accent }: {
  label: string; value: string | number; unit?: string; accent?: string;
}) {
  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 8, padding: "var(--space-3)" }}>
      <div style={{ color: "var(--muted)", fontSize: 11, letterSpacing: 1, textTransform: "uppercase" }}>{label}</div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 4, marginTop: 4 }}>
        <span className="display" style={{ fontSize: 28, color: accent ?? "var(--text)", lineHeight: 1 }}>{value}</span>
        {unit && <span className="mono" style={{ fontSize: 11, color: "var(--muted)" }}>{unit}</span>}
      </div>
    </div>
  );
}

// 网络健康 6 卡 KPI：在线率、平均权重、TPS、节点数、交易数、收益总量。
export function NetworkKPIs() {
  const [stats, setStats] = useState<ChainStats | null>(null);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [tps, setTps] = useState(0);
  const last = useRef<{ height: number; ts: number } | null>(null);

  useEffect(() => {
    const tick = async () => {
      try {
        const [s, n] = await Promise.all([api.stats(), api.nodeList()]);
        const now = Date.now();
        if (last.current) {
          const dh = s.blockHeight - last.current.height;
          const dt = Math.max(1, (now - last.current.ts) / 1000);
          setTps(Math.max(0, dh / dt));
        }
        last.current = { height: s.blockHeight, ts: now };
        setStats(s); setNodes(n);
      } catch { /* 后端未启动 */ }
    };
    tick();
    const t = setInterval(tick, 5000);
    return () => clearInterval(t);
  }, []);

  const onlineCount = nodes.filter((n) => n.online).length;
  const onlineRate = nodes.length ? (onlineCount / nodes.length) * 100 : 0;
  const avgWeight = stats && stats.nodeCount ? stats.totalWeight / stats.nodeCount : 0;

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: "var(--space-2)" }}>
      <KPICard label="在线率" value={onlineRate.toFixed(0)} unit="%" accent="var(--success)" />
      <KPICard label="平均权重" value={avgWeight.toFixed(1)} accent="var(--accent)" />
      <KPICard label="TPS" value={tps.toFixed(2)} unit="tx/s" accent="var(--accent)" />
      <KPICard label="节点数" value={stats?.nodeCount ?? 0} />
      <KPICard label="交易数" value={stats?.txCount ?? 0} />
      <KPICard label="收益总量" value={stats?.totalEarned ?? 0} unit="NMT" accent="oklch(80% 0.13 95)" />
    </div>
  );
}
