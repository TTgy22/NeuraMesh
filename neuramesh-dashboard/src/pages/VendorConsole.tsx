import { useState } from "react";
import { TaskForm } from "../components/TaskForm";
import { api, type TaskStatus } from "../api";

// notion-pre-ai：米白页面 + 友好排版
export function VendorConsole() {
  const [vendorId] = useState("acme-corp");
  const [history, setHistory] = useState<TaskStatus[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function submit(taskType: string, budget: number) {
    setError(null);
    try {
      const task = await api.submitTask(vendorId, taskType, budget);
      setHistory((h) => [task, ...h]);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div style={{ background: "var(--paper)", color: "var(--ink)", minHeight: "100vh", padding: "var(--space-6)" }}>
      <h1 className="display" style={{ fontWeight: 700 }}>厂商控制台</h1>
      <p style={{ color: "var(--ink-muted)" }}>发布算力任务，按节点权重自动结算到链上。厂商：<span className="mono">{vendorId}</span></p>
      <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "var(--space-5)", marginTop: "var(--space-4)" }}>
        <TaskForm onSubmit={submit} />
        <div>
          <h3>历史任务</h3>
          {error && <div style={{ color: "var(--danger)" }}>提交失败：{error}</div>}
          {history.length === 0 && <p style={{ color: "var(--ink-muted)" }}>暂无任务</p>}
          {history.map((t) => (
            <div key={t.taskId} style={{ background: "white", border: "1px solid var(--paper-border)",
              borderRadius: 8, padding: "var(--space-3)", marginBottom: "var(--space-2)" }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="mono">{t.taskId}</span>
                <span style={{ color: t.status === "SETTLED" ? "oklch(45% 0.12 150)" : "var(--danger)" }}>{t.status}</span>
              </div>
              <div style={{ color: "var(--ink-muted)", fontSize: 13 }}>
                {t.taskType} · 预算 {t.budget} · 分配 {t.assignedNodes.length} 节点
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}