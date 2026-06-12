import { useEffect, useState } from "react";
import {
  Area, AreaChart, CartesianGrid, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api } from "../api";
import { axis, CHART, tooltipStyle } from "../chartConfig";
import { subscribeThroughput, type ThroughputSample } from "../throughputStore";

// bloomberg-terminal：实时吞吐折线 + 面积。采样由模块级单例（throughputStore）后台常驻执行，
// 组件仅订阅渲染 —— 切换页面不清零、不断采。链为交易驱动出块：无操作时 TPS 为 0；
// 可开启"演示流量"（后端定时提交真实互转交易）让曲线持续波动。
export function ThroughputChart() {
  const [series, setSeries] = useState<ThroughputSample[]>([]);
  const [demoOn, setDemoOn] = useState(false);
  const [toggling, setToggling] = useState(false);

  useEffect(() => {
    api.demoTrafficStatus().then((s) => setDemoOn(s.enabled)).catch(() => { /* 后端未启动 */ });
    return subscribeThroughput(setSeries);
  }, []);

  async function toggleDemo() {
    setToggling(true);
    try {
      const s = await api.setDemoTraffic(!demoOn);
      setDemoOn(s.enabled);
    } catch { /* 后端未启动 */ } finally {
      setToggling(false);
    }
  }

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div className="display" style={{ marginBottom: "var(--space-2)", display: "flex", alignItems: "center" }}>
        网络吞吐 <span className="mono" style={{ fontSize: 11, color: "var(--muted)", marginLeft: 6 }}>· 5s 采样 · tx/s · 交易驱动出块</span>
        <button onClick={toggleDemo} disabled={toggling} title="开启后后端定时提交小额真实互转交易（非前端模拟），驱动持续出块"
          style={{ marginLeft: "auto", fontSize: 11, padding: "2px 10px", borderRadius: 999, cursor: "pointer",
            border: `1px solid ${demoOn ? "var(--accent)" : "var(--border)"}`,
            background: demoOn ? "var(--panel-2)" : "transparent",
            color: demoOn ? "var(--accent)" : "var(--muted)", transition: "200ms ease-out" }}>
          {demoOn ? "● 演示流量 开" : "○ 演示流量 关"}
        </button>
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
