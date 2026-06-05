import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { VendorConsole } from "./VendorConsole";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { submitTask: vi.fn() },
}));

describe("VendorConsole", () => {
  beforeEach(() => vi.clearAllMocks());

  it("渲染控制台并提交任务，触发 API 调用与历史展示", async () => {
    (api.submitTask as any).mockResolvedValue({
      taskId: "task-1", taskType: "image-classification", status: "SETTLED",
      budget: 30000, settleTxId: "0xabc", assignedNodes: ["0x7a3f"], resultUri: null,
    });

    render(<VendorConsole />);
    expect(screen.getByText("厂商控制台")).toBeInTheDocument();

    fireEvent.click(screen.getByText("发布任务"));

    await waitFor(() => expect(api.submitTask).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText("task-1")).toBeInTheDocument());
    expect(screen.getByText("SETTLED")).toBeInTheDocument();
  });
});