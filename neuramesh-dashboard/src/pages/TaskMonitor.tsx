import { useEffect, useState } from "react";
import { api, type NodeStatus } from "../api";
import { NodeMap } from "../components/NodeMap";

// 统一科技黑主题：去金色，改用低调青绿微光强调色
export function TaskMonitor() {
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    async function poll() {
      try {
        const list = await api.nodeList();
        if (alive) { setNodes(list); setErr(null); }
      } catch (e) { if (alive) setErr((e as Error).message); }
    }
    poll();
    const t = setInterval(poll, 3000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  const online = nodes.filter((n) => n.online).length;
  const avgWeight = nodes.length ? (nodes.reduce((s, n) => s + n.totalWeight, 0) / nodes.length).toFixed(1) : "0";

  return (
    <div style={{ minHeight: "100vh", padding: "var(--space-5)" }}>
      <h1 className="display">任务实时看板</h1>
      {err && <div style={{ color: "var(--danger)" }}>后端未连接（{err}）</div>}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "var(--space-3)", margin: "var(--space-4) 0" }}>
        {[
          { k: "在线节点", v: String(online) },
          { k: "平均权重", v: avgWeight },
          { k: "平均延迟", v: "42 ms" },
          { k: "准确率", v: "98.6%" },
        ].map((m) => (
          <div key={m.k} style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 8, padding: "var(--space-3)" }}>
            <div style={{ color: "var(--muted)", fontSize: 12 }}>{m.k}</div>
            <div className="display" style={{ color: "var(--accent)", fontSize: 28 }}>{m.v}</div>
          </div>
        ))}
      </div>
      <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 8, padding: "var(--space-3)" }}>
        <div className="display" style={{ marginBottom: "var(--space-2)" }}>节点分配</div>
        <NodeMap nodes={nodes} />
      </div>
    </div>
  );
}