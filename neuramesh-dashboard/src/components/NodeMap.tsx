import type { NodeStatus } from "../api";

// 节点分配表（TaskMonitor 用，bloomberg-terminal 密度风格）
export function NodeMap({ nodes }: { nodes: NodeStatus[] }) {
  return (
    <table className="mono" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
      <thead>
        <tr style={{ textAlign: "left", color: "var(--amber)", borderBottom: "1px solid var(--border)" }}>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>NodeID</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>权重</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>等级</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>累计收益</th>
        </tr>
      </thead>
      <tbody>
        {nodes.map((n) => (
          <tr key={n.nodeId} style={{ borderBottom: "1px solid var(--border)", color: "var(--amber)" }}>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.nodeId.slice(0, 14)}…</td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.totalWeight.toFixed(1)}</td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.level}</td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.totalEarned}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}