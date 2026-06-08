import { useCallback, useEffect, useState } from "react";
import { api, type MyGroup } from "../api";

// notion-pre-ai 风：已购资源组列表，剩余时长、任务数、续费。
export function MyGroupsPage() {
  const [groups, setGroups] = useState<MyGroup[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(() => {
    api.myGroups().then(setGroups).catch((e) => setErr((e as Error).message));
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [load]);

  async function renew(groupId: string) {
    setBusy(groupId);
    try { await api.renewGroup(groupId, 24); await load(); }
    catch (e) { setErr((e as Error).message); }
    finally { setBusy(null); }
  }

  function remaining(ms: number): string {
    if (ms <= 0) return "已过期";
    const h = Math.floor(ms / 3600_000);
    const m = Math.floor((ms % 3600_000) / 60_000);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <h1 className="display">我的资源组</h1>
      {err && <div style={{ color: "var(--danger)", marginTop: 12 }}>{err}</div>}

      <div style={{ marginTop: "var(--space-4)", display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
        {groups.map((g, i) => (
          <div key={g.groupId + i} style={{ background: "var(--panel)", border: "1px solid var(--border)",
            borderRadius: 10, padding: "var(--space-3) var(--space-4)" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr 1fr 1fr 1fr auto", alignItems: "center", gap: "var(--space-3)" }}>
              <div>
                <div className="display" style={{ fontSize: 15 }}>{g.region}</div>
                <div className="mono" style={{ fontSize: 11, color: "var(--muted)" }}>{g.category} · {g.groupId}</div>
              </div>
              <Cell label="状态" value={g.active ? "运行中" : "已过期"} color={g.active ? "var(--success)" : "var(--danger)"} />
              <Cell label="剩余时长" value={remaining(g.remainingMs)} />
              <Cell label="节点数" value={`${g.nodeCount}`} />
              <Cell label="累计花费" value={`${g.totalCost.toLocaleString()} NMT`} />
              <button onClick={() => renew(g.groupId)} disabled={busy === g.groupId}
                style={{ padding: "var(--space-2) var(--space-3)", borderRadius: 6, border: "1px solid var(--accent-dim)",
                  background: "transparent", color: "var(--accent)", cursor: "pointer", fontSize: 12 }}>
                {busy === g.groupId ? "续费中…" : "续费 +24h"}
              </button>
            </div>
            <details style={{ marginTop: 8 }}>
              <summary className="mono" style={{ cursor: "pointer", fontSize: 11, color: "var(--success)" }}>🔒 安全组密钥</summary>
              <div className="mono" style={{ fontSize: 10, color: "var(--muted)", marginTop: 6, wordBreak: "break-all" }}>
                <div><span style={{ color: "var(--accent)" }}>公钥</span> {g.groupPublicKey}</div>
                <div style={{ marginTop: 4 }}><span style={{ color: "var(--accent)" }}>私钥</span> {g.groupPrivateKey}</div>
              </div>
            </details>
          </div>
        ))}
        {groups.length === 0 && !err && (
          <p style={{ color: "var(--muted)" }}>尚未购买任何资源组。前往「资源组市场」选购。</p>
        )}
      </div>
    </div>
  );
}

function Cell({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: "var(--muted)", textTransform: "uppercase" }}>{label}</div>
      <div style={{ fontSize: 13, color: color ?? "var(--text)" }}>{value}</div>
    </div>
  );
}
