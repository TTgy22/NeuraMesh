import { HashRouter, NavLink, Route, Routes } from "react-router-dom";
import { VendorConsole } from "./pages/VendorConsole";
import { TaskMonitor } from "./pages/TaskMonitor";
import { BlockExplorer } from "./pages/BlockExplorer";
import { HardwareWall } from "./pages/HardwareWall";

const NAV = [
  { to: "/vendor", label: "厂商控制台" },
  { to: "/monitor", label: "任务看板" },
  { to: "/explorer", label: "区块浏览器" },
  { to: "/wall", label: "硬件墙" },
];

export function App() {
  return (
    <HashRouter>
      <div style={{ display: "flex", minHeight: "100vh" }}>
        <nav style={{ width: 200, background: "var(--panel)", borderRight: "1px solid var(--border)", padding: "var(--space-4)" }}>
          <div className="display" style={{ fontWeight: 700, fontSize: 18, marginBottom: "var(--space-5)" }}>
            Neura<span style={{ color: "var(--accent)" }}>Mesh</span>
          </div>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to}
              style={({ isActive }) => ({
                display: "block", padding: "var(--space-2) var(--space-3)", marginBottom: "var(--space-1)",
                borderRadius: 6, color: isActive ? "var(--text)" : "var(--muted)",
                background: isActive ? "var(--panel-2)" : "transparent",
              })}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <main style={{ flex: 1, overflow: "auto" }}>
          <Routes>
            <Route path="/" element={<HardwareWall />} />
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