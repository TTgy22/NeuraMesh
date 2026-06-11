import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { NodeDashboard } from "./NodeDashboard";
import { api } from "../api";

vi.mock("../api", () => ({
  api: {
    register: vi.fn(), start: vi.fn(), stop: vi.fn(), status: vi.fn(),
    earnings: vi.fn().mockResolvedValue([]),
    groups: vi.fn().mockResolvedValue([
      { groupId: "general-purpose", region: "通用-全局", minBenchmarkScore: 0, requiredHttp2: false, nodeCount: 0, totalWeight: 0, pricePerHour: 8000, category: "通用型 g6·入门" },
      { groupId: "north-china-qingdao", region: "华北-青岛", minBenchmarkScore: 50, requiredHttp2: false, nodeCount: 0, totalWeight: 0, pricePerHour: 20000, category: "通用型 g7" },
    ]),
  },
}));

// 隔离被测组件：EarningsChart 依赖 recharts ResponsiveContainer，在 jsdom 下无布局，桩替换之。
vi.mock("./EarningsChart", () => ({ EarningsChart: () => null }));

const NODE = {
  nodeId: "0x7a3f00112233445566778899aabbccddeeff0011", online: true,
  deviceModel: "RTX-4090", hardwareScore: 500, qualityScore: 800, uptimeScore: 900,
  bandwidthScore: 700, totalWeight: 760, totalEarned: 0, level: "钻石",
  fingerprint: "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12",
};

describe("NodeDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("扫描设备触发注册（强制携带资源组），注册成功后显示仪表盘与开关", async () => {
    (api.register as any).mockResolvedValue(NODE);

    render(<NodeDashboard />);
    // 等待身份恢复完成 + 资源组加载后按钮可用（默认选中 general-purpose）
    await waitFor(() => expect(screen.getByText("扫描设备并生成指纹")).toBeEnabled());
    fireEvent.click(screen.getByText("扫描设备并生成指纹"));

    await waitFor(() => expect(api.register).toHaveBeenCalledWith("RTX-4090", "general-purpose"));
    await waitFor(() => expect(screen.getByText("暂停节点")).toBeInTheDocument());
    expect(screen.getByText("首次生成")).toBeInTheDocument();
  });

  it("已持久化身份：启动直接恢复（不再注册），显示已永久绑定", async () => {
    localStorage.setItem("neuramesh_device_fingerprint_v1", JSON.stringify({
      nodeId: NODE.nodeId, fingerprint: NODE.fingerprint,
      deviceModel: "RTX-4090", resourceGroupId: "general-purpose", registeredAt: 1,
    }));
    (api.status as any).mockResolvedValue(NODE);

    render(<NodeDashboard />);

    await waitFor(() => expect(api.status).toHaveBeenCalledWith(NODE.nodeId));
    await waitFor(() => expect(screen.getByText("暂停节点")).toBeInTheDocument());
    expect(screen.getByText("已永久绑定")).toBeInTheDocument();
    expect(api.register).not.toHaveBeenCalled();
  });
});
