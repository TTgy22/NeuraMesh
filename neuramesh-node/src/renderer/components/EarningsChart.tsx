import { useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type EarningsPoint } from "../api";

export function EarningsChart({ nodeId }: { nodeId: string }) {
  const [range, setRange] = useState<7 | 30>(7);
  const [data, setData] = useState<EarningsPoint[]>([]);

  useEffect(() => {
    if (!nodeId) return;
    api.earnings(nodeId, range).then(setData).catch(() => setData([]));
  }, [nodeId, range]);

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-3)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "var(--space-2)" }}>
        <span className="display">收益曲线</span>
        <div style={{ display: "flex", gap: "var(--space-2)" }}>
          {([7, 30] as const).map((r) => (
            <button key={r} onClick={() => setRange(r)}
              style={{ background: range === r ? "var(--accent)" : "transparent", color: "var(--text)",
                border: "1px solid var(--border)", borderRadius: 4, padding: "2px 10px", cursor: "pointer" }}>
              {r}日
            </button>
          ))}
        </div>
      </div>
      <ResponsiveContainer width="100%" height={180}>
        <LineChart data={data}>
          <CartesianGrid stroke="oklch(30% 0.02 260)" strokeDasharray="2 4" />
          <XAxis dataKey="day" stroke="oklch(60% 0 0)" fontSize={11} />
          <YAxis stroke="oklch(60% 0 0)" fontSize={11} />
          <Tooltip contentStyle={{ background: "oklch(20% 0.03 260)", border: "1px solid oklch(30% 0.02 260)" }} />
          <Line type="monotone" dataKey="earnings" stroke="oklch(65% 0.15 300)" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}