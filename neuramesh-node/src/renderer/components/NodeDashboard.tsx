import { useEffect, useState } from "react";
import { api, type NodeStatus } from "../api";
import { DeviceScanner } from "./DeviceScanner";
import { EarningsChart } from "./EarningsChart";
import { clearIdentity, getSavedIdentity } from "../utils/fingerprintStorage";

// linear-style 节点仪表盘：启动时优先恢复已持久化的节点身份（指纹终身一次）
export function NodeDashboard() {
  const [node, setNode] = useState<NodeStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [bound, setBound] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      // 关键：先查持久化身份，存在则恢复，绝不重新注册
      const saved = await getSavedIdentity();
      if (saved) {
        try {
          const status = await api.status(saved.nodeId);
          if (!cancelled) {
            setNode(status);
            setBound(true);
          }
        } catch (e) {
          const msg = (e as Error).message ?? "";
          if (msg.includes("节点不存在")) {
            // 链上无此节点（后端重启 / 链重置）：身份失效，允许重新注册
            await clearIdentity();
            if (!cancelled) setNotice("链上未找到已绑定节点（后端可能已重启），请重新扫描注册");
          } else if (!cancelled) {
            setNotice("后端不可达，暂无法恢复已绑定节点身份");
          }
        }
      }
      if (!cancelled) setRestoring(false);
    })();
    return () => { cancelled = true; };
  }, []);

  function onRegistered(n: NodeStatus) {
    setNode(n);
    setBound(false); // 首次生成
  }

  async function toggle() {
    if (!node) return;
    setBusy(true);
    try {
      const updated = node.online ? await api.stop(node.nodeId) : await api.start(node.nodeId);
      setNode(updated);
    } finally {
      setBusy(false);
    }
  }

  function copyId() {
    if (node) void navigator.clipboard?.writeText(node.nodeId);
  }

  if (restoring) {
    return (
      <div style={{ maxWidth: 420, margin: "var(--space-5) auto", textAlign: "center", color: "var(--muted)" }}>
        正在恢复设备身份…
      </div>
    );
  }

  if (!node) {
    return (
      <div style={{ maxWidth: 420, margin: "var(--space-5) auto" }}>
        {notice && (
          <div style={{ color: "var(--muted)", fontSize: 12, marginBottom: "var(--space-2)",
            border: "1px solid var(--border)", borderRadius: 6, padding: "var(--space-2)" }}>
            {notice}
          </div>
        )}
        <DeviceScanner onRegistered={onRegistered} />
      </div>
    );
  }

  const fpShort = node.fingerprint ? `${node.fingerprint.substring(0, 20)}…` : "—";

  return (
    <div style={{ padding: "var(--space-5)", display: "grid", gap: "var(--space-4)" }}>
      <div style={{ display: "flex", gap: "var(--space-4)", alignItems: "center" }}>
        <button onClick={toggle} disabled={busy}
          style={{ background: node.online ? "var(--panel)" : "var(--accent)", color: "var(--text)",
            border: "1px solid var(--border)", borderRadius: 8, padding: "var(--space-3) var(--space-5)",
            cursor: "pointer", transition: "200ms ease-out", fontSize: 16 }}>
          {node.online ? "暂停节点" : "开启节点"}
        </button>
        <div>
          <div className="mono" style={{ fontSize: 12, color: "var(--muted)", cursor: "pointer" }} onClick={copyId}>
            {node.nodeId} [icon: copy]
          </div>
          <div style={{ fontSize: 13 }}>
            <span style={{ width: 8, height: 8, borderRadius: "50%", display: "inline-block",
              background: node.online ? "var(--success)" : "var(--muted)", marginRight: 6 }} />
            {node.online ? "运行中" : "已暂停"} · 等级 {node.level} ·
            <span style={{ color: "var(--accent)", marginLeft: 4 }}>
              {bound ? "已永久绑定" : "首次生成"}
            </span>
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: "var(--space-3)" }}>
        {[
          { k: "今日收益", v: Math.round(node.totalEarned / 7) },
          { k: "本周收益", v: node.totalEarned },
          { k: "累计收益", v: node.totalEarned },
        ].map((m) => (
          <div key={m.k} style={{ background: "var(--panel)", border: "1px solid var(--border)",
            borderRadius: 10, padding: "var(--space-4)" }}>
            <div style={{ color: "var(--muted)", fontSize: 12 }}>{m.k}</div>
            <div className="display" style={{ fontSize: 30 }}>{m.v}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
        <div style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-4)" }}>
          <div className="display" style={{ marginBottom: "var(--space-2)" }}>设备信息</div>
          <div className="mono" style={{ fontSize: 13, lineHeight: 1.8, color: "var(--muted)" }}>
            <div>型号: {node.deviceModel}</div>
            <div>指纹: {fpShort}</div>
            <div>硬件分: {node.hardwareScore.toFixed(1)}</div>
            <div>总权重: {node.totalWeight.toFixed(1)}</div>
          </div>
        </div>
        <EarningsChart nodeId={node.nodeId} />
      </div>
    </div>
  );
}
