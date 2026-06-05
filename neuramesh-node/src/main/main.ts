import { app, BrowserWindow, Tray, Menu, nativeImage } from "electron";
import path from "node:path";

// Electron 主进程：创建窗口、系统托盘（最小化到托盘），后台运行节点逻辑。
let win: BrowserWindow | null = null;
let tray: Tray | null = null;

function createWindow(): void {
  win = new BrowserWindow({
    width: 900,
    height: 650,
    backgroundColor: "#0b0d12",
    webPreferences: { contextIsolation: true },
  });
  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    win.loadURL(devUrl);
  } else {
    win.loadFile(path.join(__dirname, "../dist/index.html"));
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

app.whenReady().then(() => {
  createWindow();
  createTray();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});