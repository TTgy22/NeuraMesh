import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, auth } from "../api";

// linear 风：极简登录/注册表单，JWT 存 localStorage。
export function LoginPage({ onAuth }: { onAuth: () => void }) {
  const navigate = useNavigate();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("VENDOR");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setBusy(true); setErr(null);
    try {
      const res = mode === "login"
        ? await api.login(username.trim(), password)
        : await api.register(username.trim(), password, role);
      auth.set(res.accessToken, res.refreshToken);
      onAuth();
      navigate("/market");
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const input: React.CSSProperties = {
    width: "100%", padding: "var(--space-2) var(--space-3)", background: "var(--panel-2)",
    border: "1px solid var(--border)", color: "var(--text)", borderRadius: 6, fontSize: 14,
    marginTop: 4,
  };

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center" }}>
      <div style={{ width: 360, background: "var(--panel)", border: "1px solid var(--border)",
                    borderRadius: 12, padding: "var(--space-5)" }}>
        <div className="display" style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>
          Neura<span style={{ color: "var(--accent)" }}>Mesh</span>
        </div>
        <div style={{ color: "var(--muted)", fontSize: 13, marginBottom: "var(--space-4)" }}>
          {mode === "login" ? "登录以管理你的资源组" : "创建账户（自动生成密钥对与初始余额）"}
        </div>

        <label style={{ fontSize: 12, color: "var(--muted)" }}>用户名
          <input style={input} value={username} onChange={(e) => setUsername(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()} />
        </label>
        <div style={{ height: 12 }} />
        <label style={{ fontSize: 12, color: "var(--muted)" }}>密码（≥6 位）
          <input style={input} type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()} />
        </label>
        {mode === "register" && (
          <>
            <div style={{ height: 12 }} />
            <label style={{ fontSize: 12, color: "var(--muted)" }}>角色
              <select style={input} value={role} onChange={(e) => setRole(e.target.value)}>
                <option value="VENDOR">厂商 VENDOR</option>
                <option value="NODE_OPERATOR">节点运营 NODE_OPERATOR</option>
                <option value="ADMIN">管理员 ADMIN</option>
              </select>
            </label>
          </>
        )}

        {err && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 12 }}>{err}</div>}

        <button onClick={submit} disabled={busy || !username || !password}
          style={{ width: "100%", marginTop: "var(--space-4)", background: "var(--accent)", border: "none",
                   color: "oklch(15% 0.02 200)", borderRadius: 6, padding: "var(--space-3)",
                   cursor: "pointer", fontWeight: 600 }}>
          {busy ? "处理中…" : mode === "login" ? "登录" : "注册"}
        </button>

        <div style={{ textAlign: "center", marginTop: "var(--space-3)", fontSize: 13, color: "var(--muted)" }}>
          {mode === "login" ? "还没有账户？" : "已有账户？"}
          <button onClick={() => { setMode(mode === "login" ? "register" : "login"); setErr(null); }}
            style={{ background: "none", border: "none", color: "var(--accent)", cursor: "pointer", fontSize: 13 }}>
            {mode === "login" ? "去注册" : "去登录"}
          </button>
        </div>
      </div>
    </div>
  );
}
