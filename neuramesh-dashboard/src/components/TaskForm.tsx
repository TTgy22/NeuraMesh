import { useState } from "react";

const TYPES = [
  { id: "image-classification", label: "图像分类" },
  { id: "ocr", label: "OCR 文字识别" },
  { id: "defect-detection", label: "缺陷检测" },
];

export function TaskForm({ onSubmit }: { onSubmit: (taskType: string, budget: number) => void }) {
  const [taskType, setTaskType] = useState(TYPES[0].id);
  const [budget, setBudget] = useState(30000);
  return (
    <form
      onSubmit={(e) => { e.preventDefault(); onSubmit(taskType, budget); }}
      style={{ background: "white", border: "1px solid var(--paper-border)", borderRadius: 8, padding: "var(--space-4)" }}>
      <div style={{
        border: "2px dashed var(--paper-border)", borderRadius: 8, padding: "var(--space-5)",
        textAlign: "center", color: "var(--ink-muted)", marginBottom: "var(--space-4)" }}>
        [icon: upload] 拖拽数据集到此处上传（演示占位）
      </div>
      <label style={{ display: "block", marginBottom: "var(--space-2)", color: "var(--ink-muted)" }}>任务类型</label>
      <select value={taskType} onChange={(e) => setTaskType(e.target.value)}
        style={{ width: "100%", padding: "var(--space-2)", marginBottom: "var(--space-3)",
                 border: "1px solid var(--paper-border)", borderRadius: 6 }}>
        {TYPES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
      </select>
      <label style={{ display: "block", marginBottom: "var(--space-2)", color: "var(--ink-muted)" }}>预算（NMT）</label>
      <input type="number" value={budget} min={1} onChange={(e) => setBudget(Number(e.target.value))}
        className="mono"
        style={{ width: "100%", padding: "var(--space-2)", marginBottom: "var(--space-4)",
                 border: "1px solid var(--paper-border)", borderRadius: 6 }} />
      <button type="submit"
        style={{ background: "var(--ink)", color: "var(--paper)", border: "none", borderRadius: 6,
                 padding: "var(--space-2) var(--space-4)", cursor: "pointer" }}>
        发布任务
      </button>
    </form>
  );
}