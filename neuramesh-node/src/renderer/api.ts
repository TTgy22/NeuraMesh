const BASE = (import.meta as any).env?.VITE_API_BASE ?? "http://localhost:8080";
export interface ApiResponse<T> { code: number; data: T; message: string; }
export interface NodeStatus {
  nodeId: string; online: boolean; deviceModel: string; hardwareScore: number; qualityScore: number;
  uptimeScore: number; bandwidthScore: number; totalWeight: number; totalEarned: number; level: string;
  fingerprint?: string;
}
export interface EarningsPoint { day: string; earnings: number; }
export interface ResourceGroup {
  groupId: string; region: string; minBenchmarkScore: number; requiredHttp2: boolean;
  nodeCount: number; totalWeight: number; pricePerHour: number; category: string;
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, { headers: { "Content-Type": "application/json" }, ...init });
  const json = (await res.json()) as ApiResponse<T>;
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

export const api = {
  register: (deviceModel: string, resourceGroupId: string) =>
    call<NodeStatus>("/node/register", { method: "POST", body: JSON.stringify({ deviceModel, resourceGroupId }) }),
  status: (id: string) => call<NodeStatus>(`/node/${id}/status`),
  start: (id: string) => call<NodeStatus>(`/node/start?id=${id}`, { method: "POST" }),
  stop: (id: string) => call<NodeStatus>(`/node/stop?id=${id}`, { method: "POST" }),
  earnings: (id: string, days: number) => call<EarningsPoint[]>(`/node/${id}/earnings?days=${days}`),
  groups: () => call<ResourceGroup[]>("/groups"),
};
