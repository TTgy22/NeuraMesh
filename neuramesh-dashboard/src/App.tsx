import { useEffect, useState } from "react";
import { HashRouter, NavLink, Route, Routes } from "react-router-dom";
import { PixelIntro } from "./components/PixelIntro";
import { Overview } from "./pages/Overview";
import { VendorConsole } from "./pages/VendorConsole";
import { TaskMonitor } from "./pages/TaskMonitor";
import { BlockExplorer } from "./pages/BlockExplorer";
import { HardwareWall } from "./pages/HardwareWall";
import { NetworkMonitor } from "./pages/NetworkMonitor";
import { MarketPage } from "./pages/MarketPage";
import { MyGroupsPage } from "./pages/MyGroupsPage";
import { LoginPage } from "./pages/LoginPage";
import { api, auth, type UserProfile } from "./api";

// roles 为空数组 = 公共（所有人含未登录可见）；否则仅对应角色（ADMIN 始终可见全部）可见。
const NAV: { to: string; label: string; roles: string[] }[] = [
  { to: "/overview", label: "网络总览", roles: [] },
  { to: "/network", label: "网络监控", roles: [] },
  { to: "/market", label: "资源组市场", roles: [] },
  { to: "/vendor-groups", label: "我的资源组", roles: ["VENDOR"] },
  { to: "/vendor", label: "厂商控制台", roles: ["VENDOR"] },
  { to: "/monitor", label: "任务看板", roles: ["VENDOR"] },
  { to: "/wall", label: "硬件墙", roles: ["NODE_OPERATOR"] },
  { to: "/explorer", label: "区块浏览器", roles: [] },
];

export function App() {
  const [intro, setIntro] = useState(true);
  const [loggedIn, setLoggedIn] = useState(auth.isLoggedIn);
  const [me, setMe] = useState<UserProfile | null>(null);

  useEffect(() => {
    if (loggedIn) api.me().then(setMe).catch(() => { auth.clear(); setLoggedIn(false); setMe(null); });
    else setMe(null);
  }, [loggedIn]);

  // 任意请求遇 401（token 过期 / 链重置后用户不存在）→ api 层已清 token，这里同步 UI 登录态
  useEffect(() => {
    const onLogout = () => { setLoggedIn(false); setMe(null); };
    window.addEventListener("neura:logout", onLogout);
    return () => window.removeEventListener("neura:logout", onLogout);
  }, []);

  if (intro) return <PixelIntro onDone={() => setIntro(false)} />;

  function logout() { auth.clear(); setLoggedIn(false); }

  return (
    <HashRouter>
      <div style={{ display: "flex", minHeight: "100vh" }}>
        <nav style={{ width: 200, background: "var(--bg-2)", borderRight: "1px solid var(--border)", padding: "var(--space-4)", position: "relative" }}>
          <div className="display" style={{ fontWeight: 700, fontSize: 18, marginBottom: "var(--space-5)",
            display: "flex", alignItems: "center", gap: 8 }}>
            <img src="/logo.png" alt="NeuraMesh" width={26} height={26} style={{ borderRadius: 6 }} />
            <span>Neura<span style={{ color: "var(--accent)" }}>Mesh</span></span>
          </div>
          {NAV.filter((n) => n.roles.length === 0
            || (me != null && (me.role === "ADMIN" || n.roles.includes(me.role)))).map((n) => (
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

          <div style={{ position: "absolute", bottom: 16, left: 16, right: 16 }}>
            {loggedIn && me ? (
              <div style={{ fontSize: 12 }}>
                <div className="mono" style={{ color: "var(--accent)" }}>{me.username}</div>
                <div style={{ color: "var(--muted)" }}>{me.role} · {me.balance.toLocaleString()} NMT</div>
                <button onClick={logout} style={{ marginTop: 6, fontSize: 11, background: "none",
                  border: "1px solid var(--border)", color: "var(--muted)", borderRadius: 4, padding: "2px 8px", cursor: "pointer" }}>退出</button>
              </div>
            ) : (
              <NavLink to="/login" style={{ fontSize: 12, color: "var(--accent)" }}>登录 / 注册 →</NavLink>
            )}
          </div>
        </nav>
        <main style={{ flex: 1, overflow: "auto" }}>
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/overview" element={<Overview />} />
            <Route path="/network" element={<NetworkMonitor />} />
            <Route path="/market" element={<MarketPage />} />
            <Route path="/vendor-groups" element={<MyGroupsPage />} />
            <Route path="/vendor" element={<VendorConsole />} />
            <Route path="/monitor" element={<TaskMonitor />} />
            <Route path="/explorer" element={<BlockExplorer />} />
            <Route path="/wall" element={<HardwareWall />} />
            <Route path="/login" element={<LoginPage onAuth={() => setLoggedIn(true)} />} />
          </Routes>
        </main>
      </div>
    </HashRouter>
  );
}
