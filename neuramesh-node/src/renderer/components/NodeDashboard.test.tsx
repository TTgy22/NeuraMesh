import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { NodeDashboard } from "./NodeDashboard";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { register: vi.fn(), start: vi.fn(), stop: vi.fn(), earnings: vi.fn().mockResolvedValue([]) },
}));

// 隔离被测组件：EarningsChart 依赖 recharts ResponsiveContainer，在 jsdom 下无布局，桩替换之。
vi.mock("./EarningsChart", () => ({ EarningsChart: () => null }));

describe("NodeDashboard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("扫描设备触发注册，注册成功后显示仪表盘与开关", async () => {
    (api.register as any).mockResolvedValue({
      nodeId: "0x7a3f00112233445566778899aabbccddeeff0011", online: true,
      deviceModel: "RTX-4090", hardwareScore: 500, qualityScore: 800, uptimeScore: 900,
      bandwidthScore: 700, totalWeight: 760, totalEarned: 0, level: "钻石",
    });

    render(<NodeDashboard />);
    fireEvent.click(screen.getByText("扫描设备并生成指纹"));

    await waitFor(() => expect(api.register).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText("暂停节点")).toBeInTheDocument());
  });
});