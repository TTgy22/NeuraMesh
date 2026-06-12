import { app, BrowserWindow, Tray, Menu, nativeImage, ipcMain } from "electron";
import path from "node:path";
import { fileURLToPath } from "node:url";
// ESM 运行时要求相对导入带显式扩展名（编译产物为 .js）
import { loadIdentity, saveIdentity, clearIdentity, type NodeIdentity } from "./fingerprintManager.js";

// Electron 主进程：创建窗口、系统托盘（最小化到托盘），后台运行节点逻辑。
// ESM 主进程（package.json type=module）无 __dirname，由 import.meta.url 推导。
const HERE = path.dirname(fileURLToPath(import.meta.url));

let win: BrowserWindow | null = null;
let tray: Tray | null = null;

function createWindow(): void {
  win = new BrowserWindow({
    width: 900,
    height: 650,
    backgroundColor: "#0b0d12",
    webPreferences: {
      contextIsolation: true,
      preload: path.join(HERE, "preload.js"),
    },
  });
  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    win.loadURL(devUrl);
  } else {
    win.loadFile(path.join(HERE, "../dist/index.html"));
  }
  win.on("close", (e) => {
    // 关闭按钮最小化到托盘而非退出
    if (!(app as unknown as { isQuiting?: boolean }).isQuiting) {
      e.preventDefault();
      win?.hide();
    }
  });
}

function createTray(): void {
  tray = new Tray(nativeImage.createEmpty());
  tray.setToolTip("NeuraMesh 节点 — 实时收益运行中");
  const menu = Menu.buildFromTemplate([
    { label: "显示仪表盘", click: () => win?.show() },
    { type: "separator" },
    {
      label: "退出",
      click: () => {
        (app as unknown as { isQuiting?: boolean }).isQuiting = true;
        app.quit();
      },
    },
  ]);
  tray.setContextMenu(menu);
}

// 节点身份（设备指纹）IPC：渲染层经 preload 桥读写主进程文件，保证终身一致
function registerIdentityIpc(): void {
  ipcMain.handle("identity:load", () => loadIdentity());
  ipcMain.handle("identity:save", (_event, identity: NodeIdentity) => {
    saveIdentity(identity);
  });
  ipcMain.handle("identity:clear", () => {
    clearIdentity();
  });
}

app.whenReady().then(() => {
  registerIdentityIpc();
  // 启动时预加载指纹（终身一次，永久复用）
  const saved = loadIdentity();
  if (saved) {
    console.log(`[main] 设备指纹已加载（终身绑定）: ${saved.fingerprint.slice(0, 16)}… 节点 ${saved.nodeId.slice(0, 12)}…`);
  } else {
    console.log("[main] 未发现已绑定设备指纹，待首次注册生成");
  }
  createWindow();
  createTray();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});
