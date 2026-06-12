import { useEffect, useState } from "react";
import { api, type NodeStatus, type ResourceGroup } from "../api";
import { saveIdentity } from "../utils/fingerprintStorage";

// 设备检测：脉冲扫描动画 + 强制选择资源组 + 生成设备指纹（经后端注册，终身一次）
export function DeviceScanner({ onRegistered }: { onRegistered: (n: NodeStatus) => void }) {
  const [scanning, setScanning] = useState(false);
  const [model, setModel] = useState("RTX-4090");
  const [groups, setGroups] = useState<ResourceGroup[]>([]);
  const [groupId, setGroupId] = useState("");
  const [connecting, setConnecting] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stopped = false;
    // 内置启动器拉起后端约需 10~30s：失败则每 3s 自动重试，就绪后即可注册
    const load = () => {
      api.groups()
        .then((gs) => {
          if (stopped) return;
          setGroups(gs);
          setConnecting(false);
          setError(null);
          // 必须归属一组：默认选中兜底组 general-purpose，不存在则取第一个
          setGroupId((cur) => cur || (gs.find((g) => g.groupId === "general-purpose")?.groupId ?? gs[0]?.groupId ?? ""));
        })
        .catch(() => {
          if (stopped) return;
          setConnecting(true);
          setTimeout(load, 3000);
        });
    };
    load();
    return () => { stopped = true; };
  }, []);

  async function scan() {
    if (!groupId) {
      setError("请先选择资源组");
      return;
    }
    setScanning(true); setError(null);
    try {
      const node = await api.register(model, groupId);
      // 指纹终身一次：注册成功立即持久化，重启 / 刷新后永久复用同一身份
      await saveIdentity({
        nodeId: node.nodeId,
        fingerprint: node.fingerprint ?? "",
        deviceModel: model,
        resourceGroupId: groupId,
        registeredAt: Date.now(),
      });
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
        style={{ display: "block", width: "100%", boxSizing: "border-box", background: "var(--bg)",
                 color: "var(--text)", border: "1px solid var(--border)",
                 borderRadius: 6, padding: "var(--space-2)", marginBottom: "var(--space-2)", textAlign: "center" }} />
      <select className="mono" value={groupId} onChange={(e) => setGroupId(e.target.value)}
        aria-label="选择资源组"
        style={{ display: "block", width: "100%", boxSizing: "border-box", background: "var(--bg)",
                 color: "var(--text)", border: "1px solid var(--border)",
                 borderRadius: 6, padding: "var(--space-2)", marginBottom: "var(--space-3)" }}>
        {groups.length === 0 && (
          <option value="">{connecting ? "正在连接后端（启动器拉起中）…" : "加载资源组中…"}</option>
        )}
        {groups.map((g) => (
          <option key={g.groupId} value={g.groupId}>
            {g.region}（{g.groupId}）· 门槛 {g.minBenchmarkScore}
          </option>
        ))}
      </select>
      <button onClick={scan} disabled={scanning || !groupId}
        style={{ display: "block", width: "100%", background: "var(--accent)", color: "white", border: "none",
                 borderRadius: 6, padding: "var(--space-2)", cursor: "pointer", transition: "200ms ease-out",
                 opacity: scanning || !groupId ? 0.6 : 1 }}>
        {scanning ? "检测中…" : "扫描设备并生成指纹"}
      </button>
      <div style={{ color: "var(--muted)", fontSize: 11, marginTop: "var(--space-2)" }}>
        指纹终身只生成一次，注册后与本机永久绑定
      </div>
      {error && <div style={{ color: "oklch(60% 0.15 30)", marginTop: "var(--space-2)" }}>{error}</div>}
    </div>
  );
}
