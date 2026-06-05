import type { BlockInfo } from "../api";

export function TransactionTable({ blocks }: { blocks: BlockInfo[] }) {
  return (
    <table className="mono" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
      <thead>
        <tr style={{ textAlign: "left", color: "var(--muted)", borderBottom: "1px solid var(--border)" }}>
          <th style={{ padding: "var(--space-2)" }}>高度</th>
          <th style={{ padding: "var(--space-2)" }}>区块哈希</th>
          <th style={{ padding: "var(--space-2)" }}>前驱</th>
          <th style={{ padding: "var(--space-2)" }}>交易数</th>
          <th style={{ padding: "var(--space-2)" }}>时间</th>
        </tr>
      </thead>
      <tbody>
        {blocks.map((b) => (
          <tr key={b.height} style={{ borderBottom: "1px solid var(--border)" }}>
            <td style={{ padding: "var(--space-2)" }}>#{b.height}</td>
            <td style={{ padding: "var(--space-2)", color: "var(--accent)" }}>{b.hash.slice(0, 16)}…</td>
            <td style={{ padding: "var(--space-2)", color: "var(--muted)" }}>{b.prevHash.slice(0, 12)}…</td>
            <td style={{ padding: "var(--space-2)" }}>{b.txCount}</td>
            <td style={{ padding: "var(--space-2)", color: "var(--muted)" }}>
              {new Date(b.timestamp).toLocaleTimeString()}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}