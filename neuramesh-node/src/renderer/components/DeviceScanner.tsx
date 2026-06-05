import { useState } from "react";
import { api, type NodeStatus } from "../api";

// 设备检测：脉冲扫描动画 + 预估日收益 + 生成设备指纹（经后端注册）
export function DeviceScanner({ onRegistered }: { onRegistered: (n: NodeStatus) => void }) {
  const [scanning, setScanning] = useState(false);
  const [model, setModel] = useState("RTX-4090");
  const [error, setError] = useState<string | null>(null);

  async function scan() {
    setScanning(true); setError(null);
    try {
      const node = await api.register(model);
      onRegistered(node);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setScanning(false);
    }
  }

  return (
    <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10,
      padding: "var(--space-4)", textAlign: "center" }}>
      <div style={{ width: 64, height: 64, margin: "0 auto var(--space-3)", borderRadius: "50%",
        border: "2px solid var(--accent)", animation: scanning ? "pulse 1.2s ease-in-out infinite" : "none" }} />
      <input className="mono" value={model} onChange={(e) => setModel(e.target.value)}
        style={{ background: "var(--bg)", color: "var(--text)", border: "1px solid var(--border)",
                 borderRadius: 6, padding: "var(--space-2)", marginBottom: "var(--space-3)", textAlign: "center" }} />
      <button onClick={scan} disabled={scanning}
        style={{ display: "block", width: "100%", background: "var(--accent)", color: "white", border: "none",
                 borderRadius: 6, padding: "var(--space-2)", cursor: "pointer", transition: "200ms ease-out" }}>
        {scanning ? "检测中…" : "扫描设备并生成指纹"}
      </button>
      {error && <div style={{ color: "oklch(60% 0.15 30)", marginTop: "var(--space-2)" }}>{error}</div>}
    </div>
  );
}