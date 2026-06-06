import { useEffect, useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api, type NodeStatus } from "../api";
import { axis, CHART, tooltipStyle } from "../chartConfig";

// linear-style：双 Y 轴柱状图，权重（左）+ 收益（右），按权重降序取前 12 节点。
export function WeightEarningsChart() {
  const [nodes, setNodes] = useState<NodeStatus[]>([]);

  useEffect(() => {
    const load = () => api.nodesByWeight().then(setNodes).catch(() => { /* 后端未启动 */ });
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, []);

  const data = nodes.slice(0, 12).map((n) => ({
    id: n.nodeId.slice(2, 8),
    weight: Number(n.totalWeight.toFixed(1)),
    earned: n.totalEarned,
  }));

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div className="display" style={{ marginBottom: "var(--space-2)" }}>权重 / 收益对照</div>
      {data.length === 0 ? (
        <p style={{ color: "var(--muted)", fontSize: 12, height: 240, display: "flex", alignItems: "center", justifyContent: "center" }}>
          暂无节点数据
        </p>
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
            <CartesianGrid stroke={CHART.grid} strokeDasharray="2 4" />
            <XAxis dataKey="id" stroke={axis.stroke} fontSize={axis.fontSize} />
            <YAxis yAxisId="w" stroke={CHART.weight} fontSize={axis.fontSize} />
            <YAxis yAxisId="e" orientation="right" stroke={CHART.earnings} fontSize={axis.fontSize} />
            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "oklch(50% 0.02 260 / 0.15)" }} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Bar yAxisId="w" dataKey="weight" name="权重" fill={CHART.weight} radius={[2, 2, 0, 0]} />
            <Bar yAxisId="e" dataKey="earned" name="收益" fill={CHART.earnings} radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
