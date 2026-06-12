import { useState } from "react";
import { NodeDashboard } from "./components/NodeDashboard";

export function App() {
  const [tab, setTab] = useState<"dashboard" | "settings">("dashboard");
  return (
    <div style={{ minHeight: "100vh" }}>
      <header style={{ display: "flex", gap: "var(--space-3)", padding: "var(--space-3) var(--space-4)",
        borderBottom: "1px solid var(--border)", alignItems: "center" }}>
        <img src="./logo.png" alt="NeuraMesh" width={24} height={24} style={{ borderRadius: 5 }} />
        <span className="display" style={{ fontWeight: 700 }}>Neura<span style={{ color: "var(--accent)" }}>Mesh</span> 节点</span>
        <nav style={{ display: "flex", gap: "var(--space-2)", marginLeft: "auto" }}>
          {(["dashboard", "settings"] as const).map((t) => (
            <button key={t} onClick={() => setTab(t)}
              style={{ background: tab === t ? "var(--panel)" : "transparent", color: "var(--text)",
                border: "1px solid var(--border)", borderRadius: 6, padding: "var(--space-2) var(--space-3)", cursor: "pointer" }}>
              {t === "dashboard" ? "仪表盘" : "设置"}
            </button>
          ))}
        </nav>
      </header>
      {tab === "dashboard" ? <NodeDashboard /> : (
        <div style={{ padding: "var(--space-5)", color: "var(--muted)" }}>
          <h2 className="display" style={{ color: "var(--text)" }}>设置</h2>
          <p>夜间模式运行时段：22:00 – 6:00（可调）</p>
          <p>CPU 占用上限：15%，温度阈值：40℃ 自动暂停</p>
        </div>
      )}
    </div>
  );
}