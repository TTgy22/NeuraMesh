// 设备指纹 / 节点身份持久化：终身只注册一次，重启与刷新后永久复用同一身份。
// 双层存储：优先 Electron 主进程文件（window.neuraIdentity，userData 下 JSON，更可靠），
// 浏览器 / dev 环境回退 localStorage；写入时双写，读取时主进程优先。

export interface NodeIdentity {
  nodeId: string;
  fingerprint: string;
  deviceModel: string;
  resourceGroupId: string;
  registeredAt: number;
}

const FINGERPRINT_KEY = "neuramesh_device_fingerprint_v1";

interface IdentityBridge {
  load: () => Promise<NodeIdentity | null>;
  save: (identity: NodeIdentity) => Promise<void>;
  clear: () => Promise<void>;
}

function bridge(): IdentityBridge | null {
  return (window as unknown as { neuraIdentity?: IdentityBridge }).neuraIdentity ?? null;
}

function isValid(identity: unknown): identity is NodeIdentity {
  return !!identity && typeof (identity as NodeIdentity).nodeId === "string"
    && (identity as NodeIdentity).nodeId.length > 0;
}

function loadLocal(): NodeIdentity | null {
  const raw = localStorage.getItem(FINGERPRINT_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (isValid(parsed)) return parsed;
  } catch {
    // 损坏数据：清除后视为未绑定
  }
  localStorage.removeItem(FINGERPRINT_KEY);
  return null;
}

/** 读取已保存的节点身份；不存在返回 null（主进程文件优先，localStorage 兜底）。 */
export async function getSavedIdentity(): Promise<NodeIdentity | null> {
  const b = bridge();
  if (b) {
    try {
      const fromMain = await b.load();
      if (isValid(fromMain)) return fromMain;
    } catch {
      // 主进程不可用：回退 localStorage
    }
  }
  return loadLocal();
}

/** 注册成功后持久化身份（双写：localStorage + 主进程文件）。 */
export async function saveIdentity(identity: NodeIdentity): Promise<void> {
  localStorage.setItem(FINGERPRINT_KEY, JSON.stringify(identity));
  const b = bridge();
  if (b) {
    try {
      await b.save(identity);
    } catch {
      // 文件写入失败不阻塞：localStorage 已生效
    }
  }
}

/** 清除身份（仅当链上确认节点不存在时调用；调试用，生产勿随意调用）。 */
export async function clearIdentity(): Promise<void> {
  localStorage.removeItem(FINGERPRINT_KEY);
  const b = bridge();
  if (b) {
    try {
      await b.clear();
    } catch {
      // 忽略：本地已清除
    }
  }
}
