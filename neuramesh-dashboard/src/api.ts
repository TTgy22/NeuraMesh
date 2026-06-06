// 统一 API 客户端：对接 neuramesh-api 网关（ApiResponse<T>）。
const BASE = (import.meta as any).env?.VITE_API_BASE ?? "http://localhost:8080";

export interface ApiResponse<T> { code: number; data: T; message: string; }

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
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
}

export const api = {
  blocks: (limit = 20) => call<BlockInfo[]>(`/chain/blocks?limit=${limit}`),
  stats: () => call<ChainStats>(`/chain/stats`),
  nodeList: () => call<NodeStatus[]>(`/node/list`),
  tx: (hash: string) => call<TxInfo>(`/chain/tx/${hash}`),
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
};