import { useCallback, useEffect, useMemo, useState } from "react";
import { api, auth, type ResourceGroup } from "../api";
import { BuyModal } from "./BuyModal";

// 阿里云风：左侧规格族/地区筛选 + 右侧规格卡片（组成占比条 + 标签 + 安全组）。
export function MarketPage() {
  const [groups, setGroups] = useState<ResourceGroup[]>([]);
  const [sort, setSort] = useState<"price" | "online" | "nodes">("price");
  const [cats, setCats] = useState<Set<string>>(new Set());
  const [regions, setRegions] = useState<Set<string>>(new Set());
  const [buying, setBuying] = useState<ResourceGroup | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() => {
    api.market().then(setGroups).catch((e) => setErr((e as Error).message));
  }, []);
  useEffect(() => { load(); }, [load]);

  const allCats = useMemo(() => [...new Set(groups.map((g) => g.category))], [groups]);
  const allRegions = useMemo(() => [...new Set(groups.map((g) => g.region.split("-")[0]))], [groups]);

  const filtered = groups
    .filter((g) => cats.size === 0 || cats.has(g.category))
    .filter((g) => regions.size === 0 || regions.has(g.region.split("-")[0]))
    .sort((a, b) => sort === "price" ? a.pricePerHour - b.pricePerHour
      : sort === "online" ? b.onlineRate - a.onlineRate : b.nodeCount - a.nodeCount);

  function toggle(set: Set<string>, v: string, fn: (s: Set<string>) => void) {
    const n = new Set(set); n.has(v) ? n.delete(v) : n.add(v); fn(n);
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h1 className="display">资源组市场</h1>
        <span style={{ color: "var(--muted)", fontSize: 12 }}>{filtered.length} / {groups.length} 个规格</span>
      </div>
      {!auth.isLoggedIn && <div style={{ color: "var(--muted)", marginTop: 8, fontSize: 13 }}>提示：浏览免登录，购买需先登录。</div>}
      {err && <div style={{ color: "var(--danger)", marginTop: 8 }}>后端未连接（{err}）。</div>}

      <div style={{ display: "grid", gridTemplateColumns: "200px 1fr", gap: "var(--space-4)", marginTop: "var(--space-4)" }}>
        {/* 左侧筛选 */}
        <aside style={{ background: "var(--panel)", border: "1px solid var(--border)", borderRadius: 10,
          padding: "var(--space-3)", height: "fit-content", position: "sticky", top: 16 }}>
          <FilterBlock title="规格族">
            {allCats.map((c) => <Check key={c} label={c} on={cats.has(c)} onClick={() => toggle(cats, c, setCats)} />)}
          </FilterBlock>
          <FilterBlock title="地域">
            {allRegions.map((r) => <Check key={r} label={r} on={regions.has(r)} onClick={() => toggle(regions, r, setRegions)} />)}
          </FilterBlock>
          <FilterBlock title="排序">
            {(["price", "online", "nodes"] as const).map((s) => (
              <div key={s} onClick={() => setSort(s)} style={{ cursor: "pointer", fontSize: 13, padding: "4px 0",
                color: sort === s ? "var(--accent)" : "var(--muted)" }}>
                {sort === s ? "● " : "○ "}{s === "price" ? "价格优先" : s === "online" ? "在线率优先" : "节点数优先"}
              </div>
            ))}
          </FilterBlock>
        </aside>

        {/* 卡片网格 */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "var(--space-3)" }}>
          {filtered.map((g) => (
            <div key={g.groupId} className="market-card" style={{ background: "var(--panel)", border: "1px solid var(--border)",
              borderRadius: 12, padding: "var(--space-4)", display: "flex", flexDirection: "column", gap: 10,
              transition: "transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontSize: 11, color: "var(--accent)", border: "1px solid var(--accent-dim)",
                  borderRadius: 4, padding: "2px 8px" }}>{g.category}</span>
                {g.requiredHttp2 && <span className="mono" style={{ fontSize: 10, color: "var(--muted)" }}>HTTP/2</span>}
              </div>
              <div>
                <div className="display" style={{ fontSize: 16 }}>{g.region}</div>
                <div className="mono" style={{ fontSize: 11, color: "var(--muted)" }}>{g.groupId}</div>
              </div>

              {/* 规格组成条（阿里云风：可靠性 vs 多节点） */}
              <Compose label="可靠性硬件" pct={g.reliabilityPct} color="var(--accent)" />
              <Compose label="多节点冗余" pct={g.multiNodePct} color="oklch(80% 0.13 95)" />

              <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                {g.tags.map((t) => <span key={t} style={{ fontSize: 10, color: "var(--muted)",
                  background: "var(--panel-2)", borderRadius: 4, padding: "2px 6px" }}>{t}</span>)}
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 4, fontSize: 12 }}>
                <M label="节点" v={`${g.nodeCount}`} /><M label="在线率" v={`${(g.onlineRate * 100).toFixed(0)}%`} />
                <M label="总权重" v={g.totalWeight.toFixed(0)} /><M label="延迟" v={`${g.averageLatency.toFixed(0)}ms`} />
              </div>

              {/* 安全组 */}
              <div style={{ fontSize: 10, color: "var(--muted)", display: "flex", alignItems: "center", gap: 4,
                borderTop: "1px solid var(--border)", paddingTop: 8 }}>
                <span style={{ color: "var(--success)" }}>🔒 安全组</span>
                <span className="mono" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {g.groupPublicKey ? g.groupPublicKey.slice(0, 24) + "…" : "—"}
                </span>
              </div>

              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginTop: 2 }}>
                <span><span className="display" style={{ fontSize: 22, color: "var(--accent)" }}>{g.pricePerHour.toLocaleString()}</span>
                  <span style={{ fontSize: 11, color: "var(--muted)" }}> NMT/时</span></span>
                <button disabled={!auth.isLoggedIn} onClick={() => setBuying(g)}
                  style={{ padding: "var(--space-2) var(--space-4)", borderRadius: 6, border: "none",
                    background: auth.isLoggedIn ? "var(--accent)" : "var(--border)", color: "oklch(15% 0.02 200)",
                    cursor: auth.isLoggedIn ? "pointer" : "not-allowed", fontWeight: 600, fontSize: 13 }}>
                  {auth.isLoggedIn ? "立即购买" : "登录后购买"}
                </button>
              </div>
            </div>
          ))}
          {filtered.length === 0 && !err && <p style={{ color: "var(--muted)" }}>无匹配规格。</p>}
        </div>
      </div>

      {buying && <BuyModal group={buying} onClose={() => setBuying(null)} onPurchased={load} />}
      <style>{`.market-card:hover { transform: translateY(-3px); box-shadow: 0 8px 28px oklch(50% 0.05 200 / 0.18); border-color: var(--accent-dim) !important; }`}</style>
    </div>
  );
}

function FilterBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: "var(--space-3)" }}>
      <div style={{ fontSize: 12, color: "var(--text)", fontWeight: 600, marginBottom: 6 }}>{title}</div>
      {children}
    </div>
  );
}
function Check({ label, on, onClick }: { label: string; on: boolean; onClick: () => void }) {
  return (
    <div onClick={onClick} style={{ cursor: "pointer", fontSize: 13, padding: "3px 0",
      color: on ? "var(--accent)" : "var(--muted)" }}>{on ? "☑ " : "☐ "}{label}</div>
  );
}
function Compose({ label, pct, color }: { label: string; pct: number; color: string }) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "var(--muted)" }}>
        <span>{label}</span><span>{pct}%</span>
      </div>
      <div style={{ height: 6, background: "var(--panel-2)", borderRadius: 3, overflow: "hidden", marginTop: 2 }}>
        <div style={{ width: `${pct}%`, height: "100%", background: color, transition: "width 500ms ease" }} />
      </div>
    </div>
  );
}
function M({ label, v }: { label: string; v: string }) {
  return <div><span style={{ color: "var(--muted)" }}>{label} </span><span style={{ color: "var(--text)" }}>{v}</span></div>;
}
