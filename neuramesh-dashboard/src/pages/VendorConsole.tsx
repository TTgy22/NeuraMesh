import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { TaskForm } from "../components/TaskForm";
import { api, auth, type MyGroup, type TaskStatus, type UserProfile } from "../api";

// 厂商控制台：关联已购资源组，在所选安全组内下发算力任务并链上结算。
export function VendorConsole() {
  const [me, setMe] = useState<UserProfile | null>(null);
  const [groups, setGroups] = useState<MyGroup[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [history, setHistory] = useState<TaskStatus[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!auth.isLoggedIn) return;
    api.me().then(setMe).catch(() => { /* token 失效 */ });
    api.myGroups().then((g) => {
      const active = g.filter((x) => x.active);
      setGroups(active);
      setSelected((s) => s || (active[0]?.groupId ?? ""));
    }).catch(() => { /* 后端未启动 */ });
  }, []);
  useEffect(() => { load(); }, [load]);

  const current = groups.find((g) => g.groupId === selected);

  async function submit(taskType: string, budget: number) {
    setError(null);
    try {
      const vendorId = me?.username ?? "acme-corp";
      const task = selected
        ? await api.allocateGroupTask(selected, vendorId, taskType, budget)
        : await api.submitTask(vendorId, taskType, budget);
      setHistory((h) => [task, ...h]);
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  if (!auth.isLoggedIn) {
    return (
      <div style={{ padding: "var(--space-5)" }}>
        <h1 className="display">厂商控制台</h1>
        <p style={{ color: "var(--muted)" }}>请先 <Link to="/login" style={{ color: "var(--accent)" }}>登录</Link> 以管理你的资源组与任务。</p>
      </div>
    );
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <h1 className="display">厂商控制台</h1>
      <p style={{ color: "var(--muted)" }}>
        厂商 <span className="mono" style={{ color: "var(--accent)" }}>{me?.username}</span> ·
        余额 <span style={{ color: "var(--accent)" }}>{(me?.balance ?? 0).toLocaleString()} NMT</span>
      </p>

      {groups.length === 0 ? (
        <div style={{ marginTop: "var(--space-4)", background: "var(--panel)", border: "1px solid var(--border)",
          borderRadius: 10, padding: "var(--space-4)" }}>
          <div style={{ color: "var(--muted)" }}>你还没有可用的资源组。前往
            <Link to="/market" style={{ color: "var(--accent)" }}> 资源组市场 </Link>选购后即可在组内下发任务。</div>
        </div>
      ) : (
        <>
          {/* 资源组选择器（关联安全组） */}
          <div style={{ marginTop: "var(--space-4)", display: "flex", gap: "var(--space-2)", flexWrap: "wrap" }}>
            {groups.map((g) => (
              <div key={g.groupId} onClick={() => setSelected(g.groupId)}
                style={{ cursor: "pointer", padding: "var(--space-2) var(--space-3)", borderRadius: 8,
                  border: `1px solid ${selected === g.groupId ? "var(--accent)" : "var(--border)"}`,
                  background: selected === g.groupId ? "var(--panel-2)" : "var(--panel)", minWidth: 180 }}>
                <div className="display" style={{ fontSize: 14 }}>{g.region}</div>
                <div style={{ fontSize: 11, color: "var(--muted)" }}>{g.category} · {g.nodeCount} 节点</div>
              </div>
            ))}
          </div>

          {current && (
            <div className="mono" style={{ marginTop: "var(--space-2)", fontSize: 11, color: "var(--muted)",
              display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ color: "var(--success)" }}>🔒 安全组</span>
              公钥 {current.groupPublicKey.slice(0, 28)}… · 私钥凭证已持有（购买交付）
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "var(--space-5)", marginTop: "var(--space-4)" }}>
            <TaskForm onSubmit={submit} />
            <div>
              <h3 className="display">历史任务 <span style={{ fontSize: 12, color: "var(--muted)" }}>· 组内按权重结算</span></h3>
              {error && <div style={{ color: "var(--danger)" }}>提交失败：{error}</div>}
              {history.length === 0 && <p style={{ color: "var(--muted)" }}>暂无任务</p>}
              {history.map((t) => (
                <div key={t.taskId} style={{ background: "var(--panel)", border: "1px solid var(--border)",
                  borderRadius: 8, padding: "var(--space-3)", marginBottom: "var(--space-2)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span className="mono">{t.taskId}</span>
                    <span style={{ color: t.status === "SETTLED" ? "var(--success)" : "var(--danger)" }}>{t.status}</span>
                  </div>
                  <div style={{ color: "var(--muted)", fontSize: 13 }}>
                    {t.taskType} · 预算 {t.budget} · 分配 {t.assignedNodes.length} 节点
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
