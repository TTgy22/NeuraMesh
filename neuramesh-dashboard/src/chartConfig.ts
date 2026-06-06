// Recharts 统一配置：颜色引用 oklch token，避免散落硬编码。
// 设计系统：tech-black pixel theme（见 tokens.css）。

/** 等级 → oklch 渐变色（钻石冷青 → 青铜暖橙）。 */
export const LEVEL_COLORS: Record<string, string> = {
  钻石: "oklch(80% 0.12 200)",
  铂金: "oklch(78% 0.10 170)",
  黄金: "oklch(80% 0.13 95)",
  白银: "oklch(72% 0.02 260)",
  青铜: "oklch(62% 0.10 50)",
};

/** 环形/饼图渐变序列（oklch，绕色相一周）。 */
export const PIE_GRADIENT = [
  "oklch(80% 0.12 200)",
  "oklch(78% 0.12 160)",
  "oklch(80% 0.13 120)",
  "oklch(80% 0.13 90)",
  "oklch(75% 0.13 50)",
  "oklch(70% 0.14 25)",
];

export const CHART = {
  accent: "var(--accent)",
  success: "var(--success)",
  danger: "var(--danger)",
  grid: "var(--grid)",
  muted: "var(--muted)",
  text: "var(--text)",
  panel: "var(--panel)",
  panel2: "var(--panel-2)",
  border: "var(--border)",
  // 收益柱（暖金）与权重柱（青绿）双色，bloomberg 风格
  weight: "oklch(74% 0.10 200)",
  earnings: "oklch(80% 0.13 95)",
  throughput: "oklch(74% 0.10 200)",
};

/** 统一 Tooltip 容器样式。 */
export const tooltipStyle = {
  background: "var(--panel-2)",
  border: "1px solid var(--border)",
  color: "var(--text)",
  fontSize: 12,
  borderRadius: 6,
};

/** 统一坐标轴样式。 */
export const axis = {
  stroke: "var(--muted)",
  fontSize: 11,
};
