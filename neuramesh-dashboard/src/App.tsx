import { useState } from "react";
import { HashRouter, NavLink, Route, Routes } from "react-router-dom";
import { PixelIntro } from "./components/PixelIntro";
import { Overview } from "./pages/Overview";
import { VendorConsole } from "./pages/VendorConsole";
import { TaskMonitor } from "./pages/TaskMonitor";
import { BlockExplorer } from "./pages/BlockExplorer";
import { HardwareWall } from "./pages/HardwareWall";

const NAV = [
  { to: "/overview", label: "网络总览" },
  { to: "/vendor", label: "厂商控制台" },
  { to: "/monitor", label: "任务看板" },
  { to: "/explorer", label: "区块浏览器" },
  { to: "/wall", label: "硬件墙" },
];

export function App() {
  const [intro, setIntro] = useState(true);
  if (intro) return <PixelIntro onDone={() => setIntro(false)} />;

  return (
    <HashRouter>
      <div style={{ display: "flex", minHeight: "100vh" }}>
        <nav style={{ width: 200, background: "var(--bg-2)", borderRight: "1px solid var(--border)", padding: "var(--space-4)" }}>
          <div className="display" style={{ fontWeight: 700, fontSize: 18, marginBottom: "var(--space-5)" }}>
            Neura<span style={{ color: "var(--accent)" }}>Mesh</span>
          </div>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to}
              style={({ isActive }) => ({
                display: "block", padding: "var(--space-2) var(--space-3)", marginBottom: "var(--space-1)",
                borderRadius: 6, fontSize: 14, color: isActive ? "var(--text)" : "var(--muted)",
                background: isActive ? "var(--panel-2)" : "transparent",
                borderLeft: isActive ? "2px solid var(--accent)" : "2px solid transparent",
              })}>
              {n.label}
            </NavLink>
          ))}
          <div className="mono" style={{ position: "absolute", bottom: 16, fontSize: 10, color: "var(--muted)" }}>
            v0.4 · edge compute
          </div>
        </nav>
        <main style={{ flex: 1, overflow: "auto" }}>
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/overview" element={<Overview />} />
            <Route path="/vendor" element={<VendorConsole />} />
            <Route path="/monitor" element={<TaskMonitor />} />
            <Route path="/explorer" element={<BlockExplorer />} />
            <Route path="/wall" element={<HardwareWall />} />
          </Routes>
        </main>
      </div>
    </HashRouter>
  );
}