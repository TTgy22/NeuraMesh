import { useEffect, useState } from "react";
import { api, type BlockInfo } from "../api";

// 终端像素风：monospace + 绿色文字，滚动活动流。数据源 /chain/blocks?limit=20
const GREEN = "oklch(70% 0.12 150)";

export function ActivityStream() {
  const [blocks, setBlocks] = useState<BlockInfo[]>([]);

  useEffect(() => {
    const load = () => api.blocks(20).then(setBlocks).catch(() => { /* 后端未启动 */ });
    load();
    const t = setInterval(load, 3000);
    return () => clearInterval(t);
  }, []);

  return (
    <div style={{ background: "oklch(11% 0.01 150)", border: `1px solid ${GREEN}`, borderRadius: 8,
                  padding: "var(--space-3)", boxShadow: `inset 0 0 24px oklch(70% 0.12 150 / 0.06)` }}>
      <div className="mono" style={{ color: GREEN, fontSize: 12, marginBottom: "var(--space-2)", letterSpacing: 1 }}>
        ▌ LIVE ACTIVITY STREAM
      </div>
      <div className="mono" style={{ fontSize: 12, lineHeight: 1.7, maxHeight: 300, overflowY: "auto", color: GREEN }}>
        {blocks.length === 0 && <div style={{ opacity: 0.6 }}>$ waiting for blocks… (需后端 :8080)</div>}
        {blocks.map((b) => (
          <div key={b.hash} style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", animation: "ams-flicker 1.2s ease-out" }}>
            <span style={{ opacity: 0.55 }}>[{new Date(b.timestamp).toLocaleTimeString("zh-CN", { hour12: false })}]</span>{" "}
            <span style={{ color: "oklch(82% 0.12 150)" }}>BLK#{String(b.height).padStart(5, "0")}</span>{" "}
            tx={b.txCount} {"<"}{b.hash.slice(0, 16)}…{">"}
          </div>
        ))}
      </div>
      <style>{`@keyframes ams-flicker { from { opacity: 0; transform: translateX(-4px); } to { opacity: 1; transform: none; } }`}</style>
    </div>
  );
}
