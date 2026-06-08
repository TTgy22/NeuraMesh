import { useEffect, useState } from "react";
import { api, type PurchaseReceipt, type ResourceGroup } from "../api";

// linear 风：购买确认弹窗。时长选择 + 费用预估 + 余额校验 + 确认扣款 + 凭证展示。
export function BuyModal({ group, onClose, onPurchased }: {
  group: ResourceGroup; onClose: () => void; onPurchased: () => void;
}) {
  const [hours, setHours] = useState(24);
  const [balance, setBalance] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<PurchaseReceipt | null>(null);

  useEffect(() => { api.myBalance().then((b) => setBalance(b.balance)).catch(() => setBalance(null)); }, []);

  const totalCost = group.pricePerHour * hours;
  const affordable = balance == null || balance >= totalCost;

  async function confirm() {
    setBusy(true); setErr(null);
    try {
      const r = await api.buyGroup(group.groupId, hours);
      setReceipt(r);
      onPurchased();
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div onClick={onClose} style={{ position: "fixed", inset: 0, background: "oklch(5% 0 0 / 0.6)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100 }}>
      <div onClick={(e) => e.stopPropagation()} style={{ width: 420, background: "var(--panel)",
        border: "1px solid var(--border)", borderRadius: 12, padding: "var(--space-5)" }}>
        {!receipt ? (
          <>
            <div className="display" style={{ fontSize: 18, marginBottom: 4 }}>购买资源组</div>
            <div style={{ color: "var(--accent)", fontSize: 14 }}>{group.region} · {group.groupId}</div>
            <div style={{ color: "var(--muted)", fontSize: 12, marginTop: 4 }}>
              单价 {group.pricePerHour.toLocaleString()} NMT/小时 · 节点 {group.nodeCount} · 在线率 {(group.onlineRate * 100).toFixed(0)}%
            </div>

            <div style={{ marginTop: "var(--space-4)", fontSize: 13 }}>购买时长（小时）</div>
            <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
              {[1, 24, 168, 720].map((h) => (
                <button key={h} onClick={() => setHours(h)}
                  style={{ flex: 1, padding: "var(--space-2)", borderRadius: 6, cursor: "pointer",
                    border: `1px solid ${hours === h ? "var(--accent)" : "var(--border)"}`,
                    background: hours === h ? "var(--panel-2)" : "transparent",
                    color: hours === h ? "var(--text)" : "var(--muted)", fontSize: 12 }}>
                  {h === 1 ? "1时" : h === 24 ? "1天" : h === 168 ? "1周" : "1月"}
                </button>
              ))}
            </div>
            <input type="number" min={1} value={hours} onChange={(e) => setHours(Math.max(1, Number(e.target.value)))}
              style={{ width: "100%", marginTop: 8, padding: "var(--space-2)", background: "var(--panel-2)",
                border: "1px solid var(--border)", color: "var(--text)", borderRadius: 6 }} />

            <div style={{ marginTop: "var(--space-4)", padding: "var(--space-3)", background: "var(--panel-2)",
              borderRadius: 8, fontSize: 13 }}>
              <Row label="费用预估" value={`${totalCost.toLocaleString()} NMT`} accent />
              <Row label="当前余额" value={balance == null ? "—" : `${balance.toLocaleString()} NMT`} />
              <Row label="购买后余额" value={balance == null ? "—" : `${(balance - totalCost).toLocaleString()} NMT`} />
            </div>

            {!affordable && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 8 }}>余额不足</div>}
            {err && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 8 }}>{err}</div>}

            <div style={{ display: "flex", gap: 8, marginTop: "var(--space-4)" }}>
              <button onClick={onClose} style={{ flex: 1, padding: "var(--space-3)", borderRadius: 6,
                border: "1px solid var(--border)", background: "transparent", color: "var(--muted)", cursor: "pointer" }}>取消</button>
              <button onClick={confirm} disabled={busy || !affordable}
                style={{ flex: 2, padding: "var(--space-3)", borderRadius: 6, border: "none",
                  background: affordable ? "var(--accent)" : "var(--border)", color: "oklch(15% 0.02 200)",
                  cursor: affordable ? "pointer" : "not-allowed", fontWeight: 600 }}>
                {busy ? "扣款中…" : "确认购买"}
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="display" style={{ fontSize: 18, color: "var(--success)" }}>✓ 购买成功</div>
            <div style={{ marginTop: "var(--space-3)", padding: "var(--space-3)", background: "var(--panel-2)",
              borderRadius: 8, fontSize: 13 }}>
              <Row label="资源组" value={`${receipt.region} (${receipt.groupId})`} />
              <Row label="时长" value={`${receipt.hours} 小时`} />
              <Row label="花费" value={`${receipt.totalCost.toLocaleString()} NMT`} />
              <Row label="到期" value={new Date(receipt.expiresAt).toLocaleString("zh-CN")} />
              <Row label="剩余余额" value={`${receipt.remainingBalance.toLocaleString()} NMT`} />
            </div>
            <div style={{ marginTop: "var(--space-3)", fontSize: 12, color: "var(--muted)" }}>安全组私钥（请妥善保存）</div>
            <textarea readOnly value={receipt.groupPrivateKey} className="mono"
              style={{ width: "100%", height: 70, marginTop: 4, fontSize: 10, background: "var(--bg-2)",
                border: "1px solid var(--border)", color: "var(--success)", borderRadius: 6, padding: 8, resize: "none" }} />
            <button onClick={onClose} style={{ width: "100%", marginTop: "var(--space-3)", padding: "var(--space-3)",
              borderRadius: 6, border: "none", background: "var(--accent)", color: "oklch(15% 0.02 200)",
              cursor: "pointer", fontWeight: 600 }}>完成</button>
          </>
        )}
      </div>
    </div>
  );
}

function Row({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "3px 0" }}>
      <span style={{ color: "var(--muted)" }}>{label}</span>
      <span style={{ color: accent ? "var(--accent)" : "var(--text)" }}>{value}</span>
    </div>
  );
}
