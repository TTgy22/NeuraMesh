import type { NodeStatus } from "../api";

// 节点分配表（统一科技黑主题，强调色 accent）
export function NodeMap({ nodes }: { nodes: NodeStatus[] }) {
  return (
    <table className="mono" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
      <thead>
        <tr style={{ textAlign: "left", color: "var(--accent)", borderBottom: "1px solid var(--border)" }}>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>NodeID</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>状态</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>权重</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>等级</th>
          <th style={{ padding: "var(--space-1) var(--space-2)" }}>累计收益</th>
        </tr>
      </thead>
      <tbody>
        {nodes.map((n) => (
          <tr key={n.nodeId} style={{ borderBottom: "1px solid var(--border)", color: "var(--text)" }}>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.nodeId.slice(0, 14)}…</td>
            <td style={{ padding: "var(--space-1) var(--space-2)", color: n.online ? "var(--success)" : "var(--muted)" }}>
              {n.online ? "在线" : "离线"}
            </td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.totalWeight.toFixed(1)}</td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.level}</td>
            <td style={{ padding: "var(--space-1) var(--space-2)" }}>{n.totalEarned}</td>
          </tr>
        ))}
        {nodes.length === 0 && (
          <tr><td colSpan={5} style={{ padding: "var(--space-3)", color: "var(--muted)" }}>暂无节点</td></tr>
        )}
      </tbody>
    </table>
  );
}