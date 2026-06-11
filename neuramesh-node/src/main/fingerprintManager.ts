import { app } from "electron";
import * as fs from "node:fs";
import * as path from "node:path";

// 节点身份（设备指纹）主进程持久化：userData 下 JSON 文件。
// 规则：终身只生成一次（首次注册时写入），后续启动永久复用，不重新生成。

export interface NodeIdentity {
  nodeId: string;
  fingerprint: string;
  deviceModel: string;
  resourceGroupId: string;
  registeredAt: number;
}

function fpFile(): string {
  return path.join(app.getPath("userData"), "neuramesh-fingerprint.json");
}

export function loadIdentity(): NodeIdentity | null {
  try {
    if (!fs.existsSync(fpFile())) return null;
    const parsed = JSON.parse(fs.readFileSync(fpFile(), "utf-8")) as NodeIdentity;
    return parsed && typeof parsed.nodeId === "string" && parsed.nodeId ? parsed : null;
  } catch (e) {
    console.error("[fingerprint] 读取失败（视为未绑定）:", e);
    return null;
  }
}

export function saveIdentity(identity: NodeIdentity): void {
  try {
    fs.writeFileSync(fpFile(), JSON.stringify(identity, null, 2), "utf-8");
  } catch (e) {
    console.error("[fingerprint] 保存失败:", e);
  }
}

export function clearIdentity(): void {
  try {
    if (fs.existsSync(fpFile())) fs.unlinkSync(fpFile());
  } catch (e) {
    console.error("[fingerprint] 清除失败:", e);
  }
}
