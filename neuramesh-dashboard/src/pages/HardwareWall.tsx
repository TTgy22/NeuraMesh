import { useCallback, useEffect, useState } from "react";
import { api, type NodeStatus } from "../api";
import { NetworkKPIs } from "../components/KPICard";
import { ActivityStream } from "../components/ActivityStream";

const DEMO_MODELS = ["Jetson-Orin-NX", "RTX-4090", "Mac-Studio-M2", "Jetson-Nano",
  "RTX-3060", "Ryzen-7950X", "M3-Max", "A100-40G"];

export function HardwareWall() {
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [seeding, setSeeding] = useState(false);

  const load = useCallback(async () => {
    try { setNodes(await api.nodeList()); } catch { /* 后端未启动 */ }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 4000);
    return () => clearInterval(t);
  }, [load]);

  async function seed() {
    setSeeding(true);
    for (const m of DEMO_MODELS) { try { await api.registerNode(m); } catch { /* ignore */ } }
    await load();
    setSeeding(false);
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 className="display">硬件墙</h1>
        <button onClick={seed} disabled={seeding}
          style={{ background: "var(--accent)", border: "none", color: "oklch(15% 0.02 200)", borderRadius: 6,
                   padding: "var(--space-2) var(--space-4)", cursor: "pointer", fontWeight: 600, transition: "200ms ease-out" }}>
          {seeding ? "注册中…" : "接入 8 台演示设备"}
        </button>
      </div>

      <div style={{ margin: "var(--space-4) 0" }}>
        <NetworkKPIs />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "var(--space-3)", marginTop: "var(--space-4)" }}>
        {nodes.map((n) => (
          <div key={n.nodeId} style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-4)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span className="mono" style={{ fontSize: 12, color: "var(--muted)" }}>{n.nodeId.slice(0, 12)}…</span>
              <span style={{ width: 8, height: 8, borderRadius: "50%", display: "inline-block",
                background: n.online ? "var(--success)" : "var(--muted)",
                boxShadow: n.online ? "0 0 8px var(--success)" : "none" }} />
            </div>
            <div className="display" style={{ fontSize: 14, margin: "var(--space-2) 0" }}>{n.deviceModel}</div>
            <div className="display" style={{ fontSize: 26, color: "var(--accent)" }}>{n.totalEarned}</div>
            <div style={{ fontSize: 12, color: "var(--muted)" }}>NMT 累计收益</div>
            <div style={{ marginTop: "var(--space-2)", fontSize: 12 }}>
              <span style={{ border: "1px solid var(--border)", borderRadius: 4, padding: "2px 8px" }}>{n.level}</span>
            </div>
          </div>
        ))}
        {nodes.length === 0 && <p style={{ color: "var(--muted)" }}>点击右上角按钮接入演示设备（需后端 :8080 运行）。</p>}
      </div>

      <div style={{ marginTop: "var(--space-5)" }}>
        <ActivityStream />
      </div>
    </div>
  );
}