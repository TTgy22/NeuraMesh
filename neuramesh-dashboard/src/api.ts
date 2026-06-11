// 统一 API 客户端：对接 neuramesh-api 网关（ApiResponse<T>）。
const BASE = (import.meta as any).env?.VITE_API_BASE ?? "http://localhost:8080";

export interface ApiResponse<T> { code: number; data: T; message: string; }

const TOKEN_KEY = "neuramesh.accessToken";
const REFRESH_KEY = "neuramesh.refreshToken";

export const auth = {
  get token() { return localStorage.getItem(TOKEN_KEY); },
  get refreshToken() { return localStorage.getItem(REFRESH_KEY); },
  set(access: string, refresh: string) {
    localStorage.setItem(TOKEN_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(REFRESH_KEY); },
  get isLoggedIn() { return !!localStorage.getItem(TOKEN_KEY); },
};

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth.token) headers["Authorization"] = `Bearer ${auth.token}`;
  const res = await fetch(BASE + path, { headers: { ...headers, ...(init?.headers as any) }, ...init });
  const json = (await res.json()) as ApiResponse<T>;
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

export interface BlockInfo { height: number; hash: string; prevHash: string; timestamp: number; txCount: number; }
export interface TxInfo { txId: string; type: string; from: string; to: string; nonce: number; timestamp: number; }
export interface NodeStatus {
  nodeId: string; online: boolean; deviceModel: string; hardwareScore: number; qualityScore: number;
  uptimeScore: number; bandwidthScore: number; totalWeight: number; totalEarned: number; level: string;
}
export interface TaskStatus {
  taskId: string; taskType: string; status: string; budget: number; settleTxId: string | null;
  assignedNodes: string[]; resultUri: string | null;
}
export interface ChainStats {
  blockHeight: number; txCount: number; nodeCount: number; accountCount: number;
  totalWeight: number; totalEarned: number; totalBalance: number;
}
export interface LevelDistribution { level: string; count: number; }
export interface ResourceGroup {
  groupId: string; region: string; minBenchmarkScore: number; requiredHttp2: boolean;
  nodeCount: number; totalWeight: number; averageLatency: number; onlineRate: number;
  pricePerHour: number; groupPublicKey: string;
  category: string; reliabilityPct: number; multiNodePct: number; tags: string[];
}
export interface TokenResponse {
  accessToken: string; refreshToken: string; userId: string; username: string;
  role: string; address: string;
}
export interface UserProfile {
  userId: string; username: string; role: string; address: string; publicKey: string; balance: number;
}
export interface PurchaseReceipt {
  groupId: string; region: string; hours: number; totalCost: number; expiresAt: number;
  settleTxId: string; groupPrivateKey: string; remainingBalance: number;
}
export interface MyGroup {
  groupId: string; region: string; category: string; hours: number; totalCost: number; purchasedAt: number;
  expiresAt: number; remainingMs: number; active: boolean; settleTxId: string; nodeCount: number;
  groupPublicKey: string; groupPrivateKey: string;
}

export const api = {
  blocks: (limit = 20) => call<BlockInfo[]>(`/chain/blocks?limit=${limit}`),
  stats: () => call<ChainStats>(`/chain/stats`),
  nodeList: () => call<NodeStatus[]>(`/node/list`),
  tx: (hash: string) => call<TxInfo>(`/chain/tx/${hash}`),
  txStatus: (hash: string) => call<{ hash: string; status: string }>(`/chain/tx/${hash}/status`),
  submitTask: (vendorId: string, taskType: string, budget: number) =>
    call<TaskStatus>(`/task/submit`, { method: "POST", body: JSON.stringify({ vendorId, taskType, budget }) }),
  taskStatus: (id: string) => call<TaskStatus>(`/task/${id}/status`),
  vendorBalance: (id: string) => call<{ balance: number }>(`/vendor/${id}/balance`),
  registerNode: (deviceModel: string) =>
    call<NodeStatus>(`/node/register`, { method: "POST", body: JSON.stringify({ deviceModel }) }),
  nodeStatus: (id: string) => call<NodeStatus>(`/node/${id}/status`),
  nodesByLevel: () => call<LevelDistribution[]>(`/chain/nodes?groupBy=level`),
  nodesByWeight: () => call<NodeStatus[]>(`/chain/nodes?sortBy=weight`),
  groups: () => call<ResourceGroup[]>(`/groups`),
  groupNodes: (id: string) => call<NodeStatus[]>(`/groups/${id}/nodes`),
  joinGroup: (id: string, nodeId: string) =>
    call<ResourceGroup>(`/groups/${id}/join`, { method: "POST", body: JSON.stringify({ nodeId }) }),
  allocateGroupTask: (id: string, vendorId: string, taskType: string, budget: number) =>
    call<TaskStatus>(`/groups/${id}/allocate`, { method: "POST", body: JSON.stringify({ vendorId, taskType, budget }) }),

  // 用户系统
  register: (username: string, password: string, role: string) =>
    call<TokenResponse>(`/auth/register`, { method: "POST", body: JSON.stringify({ username, password, role }) }),
  login: (username: string, password: string) =>
    call<TokenResponse>(`/auth/login`, { method: "POST", body: JSON.stringify({ username, password }) }),
  me: () => call<UserProfile>(`/user/me`),
  myBalance: () => call<{ balance: number }>(`/user/balance`),

  // 市场 / 购买 / 我的资源组
  market: () => call<ResourceGroup[]>(`/market/groups`),
  marketDetail: (id: string) => call<ResourceGroup>(`/market/groups/${id}`),
  buyGroup: (id: string, hours: number) =>
    call<PurchaseReceipt>(`/market/groups/${id}/buy`, { method: "POST", body: JSON.stringify({ hours }) }),
  renewGroup: (id: string, hours: number) =>
    call<PurchaseReceipt>(`/market/groups/${id}/renew`, { method: "POST", body: JSON.stringify({ hours }) }),
  myGroups: () => call<MyGroup[]>(`/vendor/groups`),
};