import { useEffect, useState } from "react";
import { LevelDistributionChart } from "../components/LevelDistributionChart";
import { ThroughputChart } from "../components/ThroughputChart";
import { NetworkKPIs } from "../components/KPICard";
import { api, type ResourceGroup } from "../api";

// 网络监控页：吞吐折线/面积图 + 等级环形图 + 资源组概览。
export function NetworkMonitor() {
  const [groups, setGroups] = useState<ResourceGroup[]>([]);

  useEffect(() => {
    const load = () => api.groups().then(setGroups).catch(() => { /* 后端未启动 */ });
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, []);

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <h1 className="display">网络监控</h1>

      <div style={{ margin: "var(--space-4) 0" }}>
        <NetworkKPIs />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "var(--space-3)" }}>
        <ThroughputChart />
        <LevelDistributionChart />
      </div>

      <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10,
                    padding: "var(--space-3)", marginTop: "var(--space-3)" }}>
        <div className="display" style={{ marginBottom: "var(--space-2)" }}>资源组（按地区动态分区）</div>
        <table className="mono" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead>
            <tr style={{ color: "var(--muted)", textAlign: "left" }}>
              <th style={{ padding: "6px 8px" }}>资源组</th>
              <th style={{ padding: "6px 8px" }}>地区</th>
              <th style={{ padding: "6px 8px" }}>节点</th>
              <th style={{ padding: "6px 8px" }}>总权重</th>
              <th style={{ padding: "6px 8px" }}>平均延迟</th>
              <th style={{ padding: "6px 8px" }}>在线率</th>
              <th style={{ padding: "6px 8px" }}>门槛/HTTP2</th>
            </tr>
          </thead>
          <tbody>
            {groups.map((g) => (
              <tr key={g.groupId} style={{ borderTop: "1px solid var(--border)" }}>
                <td style={{ padding: "6px 8px", color: "var(--accent)" }}>{g.groupId}</td>
                <td style={{ padding: "6px 8px" }}>{g.region}</td>
                <td style={{ padding: "6px 8px" }}>{g.nodeCount}</td>
                <td style={{ padding: "6px 8px" }}>{g.totalWeight.toFixed(1)}</td>
                <td style={{ padding: "6px 8px" }}>{g.averageLatency.toFixed(0)} ms</td>
                <td style={{ padding: "6px 8px" }}>{(g.onlineRate * 100).toFixed(0)}%</td>
                <td style={{ padding: "6px 8px", color: "var(--muted)" }}>
                  ≥{g.minBenchmarkScore} {g.requiredHttp2 ? "· H2" : ""}
                </td>
              </tr>
            ))}
            {groups.length === 0 && (
              <tr><td colSpan={7} style={{ padding: "10px 8px", color: "var(--muted)" }}>暂无资源组数据（需后端 :8080）。</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
