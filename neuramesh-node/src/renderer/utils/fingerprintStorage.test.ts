import { describe, expect, it, beforeEach } from "vitest";
import { clearIdentity, getSavedIdentity, saveIdentity, type NodeIdentity } from "./fingerprintStorage";

const IDENTITY: NodeIdentity = {
  nodeId: "0xabcdef0011223344556677889900aabbccddeeff",
  fingerprint: "f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2",
  deviceModel: "RTX-4090",
  resourceGroupId: "general-purpose",
  registeredAt: 1718000000000,
};

describe("fingerprintStorage（指纹终身一次，刷新后不变）", () => {
  beforeEach(() => localStorage.clear());

  it("首次无身份返回 null", async () => {
    expect(await getSavedIdentity()).toBeNull();
  });

  it("保存后多次读取（模拟刷新）返回同一身份", async () => {
    await saveIdentity(IDENTITY);
    const first = await getSavedIdentity();
    const second = await getSavedIdentity();
    expect(first?.fingerprint).toBe(IDENTITY.fingerprint);
    expect(second?.fingerprint).toBe(first?.fingerprint);
    expect(second?.nodeId).toBe(IDENTITY.nodeId);
  });

  it("清除后恢复未绑定状态", async () => {
    await saveIdentity(IDENTITY);
    await clearIdentity();
    expect(await getSavedIdentity()).toBeNull();
  });

  it("损坏数据自动清理并返回 null", async () => {
    localStorage.setItem("neuramesh_device_fingerprint_v1", "{not-json");
    expect(await getSavedIdentity()).toBeNull();
    expect(localStorage.getItem("neuramesh_device_fingerprint_v1")).toBeNull();
  });
});
