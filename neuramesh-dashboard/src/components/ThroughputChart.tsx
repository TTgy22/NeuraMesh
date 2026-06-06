import { useEffect, useRef, useState } from "react";
import {
  Area, AreaChart, CartesianGrid, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api } from "../api";
import { axis, CHART, tooltipStyle } from "../chartConfig";

interface Sample { t: string; tps: number; blocks: number; }

// bloomberg-terminal：实时吞吐折线 + 面积。客户端 5s 轮询区块高度，差分计算 TPS，缓存最近 50 点。
export function ThroughputChart() {
  const [series, setSeries] = useState<Sample[]>([]);
  const last = useRef<{ height: number; ts: number } | null>(null);

  useEffect(() => {
    const tick = async () => {
      try {
        const stats = await api.stats();
        const now = Date.now();
        if (last.current) {
          const dh = stats.blockHeight - last.current.height;
          const dt = Math.max(1, (now - last.current.ts) / 1000);
          const tps = Math.max(0, dh / dt);
          const label = new Date(now).toLocaleTimeString("zh-CN", { hour12: false });
          setSeries((prev) => [...prev, { t: label, tps: Number(tps.toFixed(2)), blocks: stats.blockHeight }].slice(-50));
        }
        last.current = { height: stats.blockHeight, ts: now };
      } catch { /* 后端未启动 */ }
    };
    tick();
    const t = setInterval(tick, 5000);
    return () => clearInterval(t);
  }, []);

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div className="display" style={{ marginBottom: "var(--space-2)" }}>
        网络吞吐 <span className="mono" style={{ fontSize: 11, color: "var(--muted)" }}>· 5s 采样 · tx/s</span>
      </div>
      <ResponsiveContainer width="100%" height={240}>
        <AreaChart data={series} margin={{ top: 8, right: 12, left: -12, bottom: 0 }}>
          <defs>
            <linearGradient id="tpsFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={CHART.throughput} stopOpacity={0.35} />
              <stop offset="100%" stopColor={CHART.throughput} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={CHART.grid} strokeDasharray="2 4" />
          <XAxis dataKey="t" stroke={axis.stroke} fontSize={axis.fontSize} minTickGap={32} />
          <YAxis stroke={axis.stroke} fontSize={axis.fontSize} allowDecimals />
          <Tooltip contentStyle={tooltipStyle} />
          <Area type="monotone" dataKey="tps" stroke="none" fill="url(#tpsFill)" />
          <Line type="monotone" dataKey="tps" stroke={CHART.throughput} strokeWidth={2} dot={false} isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
      {series.length === 0 && (
        <p style={{ color: "var(--muted)", fontSize: 12, marginTop: 4 }}>正在采集吞吐样本…（需后端 :8080）</p>
      )}
    </div>
  );
}
