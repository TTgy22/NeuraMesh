import { useState } from "react";

const TYPES = [
  { id: "image-classification", label: "图像分类" },
  { id: "ocr", label: "OCR 文字识别" },
  { id: "defect-detection", label: "缺陷检测" },
];

export function TaskForm({ onSubmit }: { onSubmit: (taskType: string, budget: number) => void }) {
  const [taskType, setTaskType] = useState(TYPES[0].id);
  const [budget, setBudget] = useState(30000);
  const inputStyle = {
    width: "100%", padding: "var(--space-2)", background: "var(--bg-2)", color: "var(--text)",
    border: "1px solid var(--border)", borderRadius: 6, marginBottom: "var(--space-3)",
  } as const;
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit(taskType, budget); }}
      style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10, padding: "var(--space-4)" }}>
      <div style={{ border: "1px dashed var(--border)", borderRadius: 8, padding: "var(--space-5)",
        textAlign: "center", color: "var(--muted)", marginBottom: "var(--space-4)" }}>
        [icon: upload] 拖拽数据集到此处上传（演示占位）
      </div>
      <label style={{ display: "block", marginBottom: "var(--space-2)", color: "var(--muted)" }}>任务类型</label>
      <select value={taskType} onChange={(e) => setTaskType(e.target.value)} style={inputStyle}>
        {TYPES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
      </select>
      <label style={{ display: "block", marginBottom: "var(--space-2)", color: "var(--muted)" }}>预算（NMT）</label>
      <input type="number" value={budget} min={1} onChange={(e) => setBudget(Number(e.target.value))} className="mono" style={inputStyle} />
      <button type="submit"
        style={{ background: "var(--accent)", color: "oklch(15% 0.02 200)", border: "none", borderRadius: 6,
          padding: "var(--space-2) var(--space-4)", cursor: "pointer", fontWeight: 600 }}>
        发布任务
      </button>
    </form>
  );
}