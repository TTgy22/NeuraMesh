import { useEffect, useState } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { api, type LevelDistribution } from "../api";
import { LEVEL_COLORS, PIE_GRADIENT, tooltipStyle } from "../chartConfig";

// linear-style：极简环形图，节点等级分布。数据源 /chain/nodes?groupBy=level
export function LevelDistributionChart() {
  const [data, setData] = useState<LevelDistribution[]>([]);

  useEffect(() => {
    const load = () => api.nodesByLevel().then(setData).catch(() => { /* 后端未启动 */ });
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, []);

  const nonEmpty = data.filter((d) => d.count > 0);
  const total = nonEmpty.reduce((s, d) => s + d.count, 0);

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div className="display" style={{ marginBottom: "var(--space-2)" }}>节点等级分布</div>
      {total === 0 ? (
        <p style={{ color: "var(--muted)", fontSize: 12, height: 240, display: "flex", alignItems: "center", justifyContent: "center" }}>
          暂无节点数据
        </p>
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <PieChart>
            <Pie data={nonEmpty} dataKey="count" nameKey="level" cx="50%" cy="50%"
              innerRadius={55} outerRadius={90} paddingAngle={2} stroke="var(--panel)">
              {nonEmpty.map((d, i) => (
                <Cell key={d.level} fill={LEVEL_COLORS[d.level] ?? PIE_GRADIENT[i % PIE_GRADIENT.length]} />
              ))}
            </Pie>
            <Tooltip contentStyle={tooltipStyle} />
            <Legend wrapperStyle={{ fontSize: 12, color: "var(--muted)" }} />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
