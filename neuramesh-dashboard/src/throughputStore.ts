import { api } from "./api";

// 吞吐采样单例：模块级持有采样历史与定时器，组件只订阅渲染。
// 首次订阅启动 5s 轮询后便后台常驻 —— 切换页面（组件卸载）不清零、不断采，回来即见完整曲线。

export interface ThroughputSample { t: string; tps: number; blocks: number; }

const MAX_SAMPLES = 50;
const INTERVAL_MS = 5000;

let series: ThroughputSample[] = [];
let last: { height: number; ts: number } | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
const listeners = new Set<(s: ThroughputSample[]) => void>();

async function tick(): Promise<void> {
  try {
    const stats = await api.stats();
    const now = Date.now();
    if (last) {
      const dh = stats.blockHeight - last.height;
      const dt = Math.max(1, (now - last.ts) / 1000);
      const tps = Math.max(0, dh / dt);
      const label = new Date(now).toLocaleTimeString("zh-CN", { hour12: false });
      series = [...series, { t: label, tps: Number(tps.toFixed(2)), blocks: stats.blockHeight }].slice(-MAX_SAMPLES);
      listeners.forEach((fn) => fn(series));
    }
    last = { height: stats.blockHeight, ts: now };
  } catch {
    // 后端未启动：保留已有样本，下个周期重试
  }
}

/**
 * 订阅吞吐采样序列（立即回放历史）；返回取消订阅函数。
 * 定时器在首次订阅时启动且常驻，保证切页期间数据持续累积。
 */
export function subscribeThroughput(fn: (s: ThroughputSample[]) => void): () => void {
  listeners.add(fn);
  fn(series);
  if (!timer) {
    void tick();
    timer = setInterval(() => void tick(), INTERVAL_MS);
  }
  return () => listeners.delete(fn);
}
