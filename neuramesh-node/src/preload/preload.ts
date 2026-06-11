import { contextBridge, ipcRenderer } from "electron";

// 渲染层桥（contextIsolation 安全边界内）：暴露主进程文件级节点身份存储。
// 渲染层经 window.neuraIdentity 读写 userData/neuramesh-fingerprint.json，保证指纹终身一致。
contextBridge.exposeInMainWorld("neuraIdentity", {
  load: () => ipcRenderer.invoke("identity:load"),
  save: (identity: unknown) => ipcRenderer.invoke("identity:save", identity),
  clear: () => ipcRenderer.invoke("identity:clear"),
});
