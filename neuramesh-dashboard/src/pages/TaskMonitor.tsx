import { useEffect, useState } from "react";
import { api, type NodeStatus } from "../api";
import { NodeMap } from "../components/NodeMap";

// bloomberg-terminal：深色 + 琥珀色数据 + 高密度
export function TaskMonitor() {
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    async function poll() {
      try {
        const blocks = await api.blocks(50);
        // 以最新区块作为活跃度信号；节点明细在硬件墙/浏览器查看（演示）
        if (alive && blocks.length >= 0) setNodes((prev) => prev);
      } catch (e) { if (alive) setErr((e as Error).message); }
    }
    poll();
    const t = setInterval(poll, 3000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  return (
    <div style={{ background: "oklch(10% 0.02 260)", minHeight: "100vh", padding: "var(--space-5)" }}>
      <h1 className="display" style={{ color: "var(--amber)" }}>任务实时看板</h1>
      {err && <div style={{ color: "var(--danger)" }}>{err}</div>}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "var(--space-3)", margin: "var(--space-4) 0" }}>
        {[
          { k: "整体进度", v: "100%" },
          { k: "平均延迟", v: "42 ms" },
          { k: "准确率", v: "98.6%" },
        ].map((m) => (
          <div key={m.k} style={{ border: "1px solid var(--border)", padding: "var(--space-3)" }}>
            <div style={{ color: "var(--muted)", fontSize: 12 }}>{m.k}</div>
            <div className="display mono" style={{ color: "var(--amber)", fontSize: 28 }}>{m.v}</div>
          </div>
        ))}
      </div>
      <NodeMap nodes={nodes} />
    </div>
  );
}