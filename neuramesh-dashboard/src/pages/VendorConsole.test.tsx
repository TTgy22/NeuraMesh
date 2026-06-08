import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { VendorConsole } from "./VendorConsole";
import { api } from "../api";

vi.mock("../api", () => ({
  auth: { isLoggedIn: true },
  api: {
    me: vi.fn(),
    myGroups: vi.fn(),
    allocateGroupTask: vi.fn(),
    submitTask: vi.fn(),
  },
}));

describe("VendorConsole", () => {
  beforeEach(() => vi.clearAllMocks());

  it("登录厂商选组后下发任务，触发组内分配并展示历史", async () => {
    (api.me as any).mockResolvedValue({
      userId: "u1", username: "demo", role: "VENDOR", address: "0xabc", publicKey: "", balance: 5_000_000,
    });
    (api.myGroups as any).mockResolvedValue([{
      groupId: "north-china-qingdao", region: "华北-青岛", category: "通用型 g7", hours: 24,
      totalCost: 480000, purchasedAt: 1, expiresAt: Date.now() + 1e7, remainingMs: 1e7,
      active: true, settleTxId: "0x1", nodeCount: 4, groupPublicKey: "PUBKEYabcdef0123456789abcdef", groupPrivateKey: "PRIV",
    }]);
    (api.allocateGroupTask as any).mockResolvedValue({
      taskId: "task-1", taskType: "image-classification", status: "SETTLED",
      budget: 30000, settleTxId: "0xabc", assignedNodes: ["0x7a3f"], resultUri: null,
    });

    render(<MemoryRouter><VendorConsole /></MemoryRouter>);
    expect(screen.getByText("厂商控制台")).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText("华北-青岛")).toBeInTheDocument());

    fireEvent.click(screen.getByText("发布任务"));

    await waitFor(() => expect(api.allocateGroupTask).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText("task-1")).toBeInTheDocument());
    expect(screen.getByText("SETTLED")).toBeInTheDocument();
  });
});
