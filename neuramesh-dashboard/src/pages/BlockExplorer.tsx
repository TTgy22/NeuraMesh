import { useEffect, useState } from "react";
import { api, type BlockInfo, type TxInfo } from "../api";
import { TransactionTable } from "../components/TransactionTable";

// tufte-dataink：数据密度高，无 chartjunk
export function BlockExplorer() {
  const [blocks, setBlocks] = useState<BlockInfo[]>([]);
  const [query, setQuery] = useState("");
  const [tx, setTx] = useState<TxInfo | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api.blocks(30).then(setBlocks).catch((e) => setErr((e as Error).message));
  }, []);

  async function search() {
    setErr(null); setTx(null);
    try { setTx(await api.tx(query.trim())); }
    catch (e) { setErr((e as Error).message); }
  }

  return (
    <div style={{ padding: "var(--space-5)" }}>
      <h1 className="display">区块浏览器</h1>
      <div style={{ display: "flex", gap: "var(--space-2)", margin: "var(--space-3) 0" }}>
        <input className="mono" placeholder="输入 TxHash 查询" value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ flex: 1, padding: "var(--space-2)", background: "var(--panel)",
                   border: "1px solid var(--border)", color: "var(--text)", borderRadius: 6 }} />
        <button onClick={search} style={{ background: "var(--accent)", border: "none", color: "white",
          padding: "0 var(--space-4)", borderRadius: 6, cursor: "pointer" }}>查询</button>
      </div>
      {err && <div style={{ color: "var(--danger)" }}>{err}</div>}
      {tx && (
        <div className="mono" style={{ background: "var(--panel)", border: "1px solid var(--border)",
          borderRadius: 8, padding: "var(--space-3)", marginBottom: "var(--space-4)", fontSize: 13 }}>
          <div>txId: {tx.txId}</div>
          <div>type: {tx.type}</div>
          <div>from: {tx.from}</div>
          <div>to: {tx.to}</div>
          <div>nonce: {tx.nonce}</div>
        </div>
      )}
      <TransactionTable blocks={blocks} />
    </div>
  );
}