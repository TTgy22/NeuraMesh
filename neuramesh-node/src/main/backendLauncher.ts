import { app } from "electron";
import { spawn, type ChildProcess } from "node:child_process";
import * as fs from "node:fs";
import * as path from "node:path";

// 内置后端启动器：客户端即启动器。
// 启动时探测本机 8080：已有后端则直接复用；没有则在发布包目录寻找 neuramesh-api*.jar
// 并以 java -jar 拉起（日志写 userData/backend.log），轮询至就绪；应用退出时关闭自己拉起的后端。

const BACKEND_URL = "http://127.0.0.1:8080/chain/stats";

export type LaunchResult = "already-running" | "started" | "no-jar" | "no-java" | "timeout";

let backendProc: ChildProcess | null = null;
let spawnFailed = false;

async function probe(timeoutMs = 1500): Promise<boolean> {
  try {
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(), timeoutMs);
    const res = await fetch(BACKEND_URL, { signal: ctl.signal });
    clearTimeout(timer);
    return res.ok;
  } catch {
    return false;
  }
}

/** 寻找 java：优先发布包内置精简 JRE（jre\bin\java.exe，免装 Java），回退系统 PATH。 */
function findJavaCmd(): string {
  const exeDir = path.dirname(app.getPath("exe"));
  const candidates = [
    path.join(exeDir, "..", "jre", "bin", "java.exe"),
    path.join(exeDir, "..", "..", "jre", "bin", "java.exe"),
  ];
  for (const p of candidates) {
    try {
      if (fs.existsSync(p)) return p;
    } catch {
      // 继续候选
    }
  }
  return "java";
}

/** 在 exe 同级及上两级目录中寻找后端 jar（发布包：exe 在 NeuraMesh-Node-win32-x64\，jar 在上一级）。 */
function findBackendJar(): string | null {
  const exeDir = path.dirname(app.getPath("exe"));
  const candidates = [
    exeDir,
    path.join(exeDir, ".."),
    path.join(exeDir, "..", ".."),
    // 开发态兜底：仓库内 bootJar 产物
    path.join(exeDir, "..", "..", "..", "neuramesh-api", "build", "libs"),
  ];
  for (const dir of candidates) {
    try {
      const hit = fs.readdirSync(dir).find((f) => /^neuramesh-api.*\.jar$/i.test(f));
      if (hit) return path.join(dir, hit);
    } catch {
      // 目录不存在：继续下一个候选
    }
  }
  return null;
}

/**
 * 确保后端可用：复用已运行实例，或拉起包内 jar 并等待就绪（最长约 60s）。
 */
export async function ensureBackend(): Promise<LaunchResult> {
  if (await probe()) {
    console.log("[launcher] 检测到 8080 已有后端，直接复用");
    return "already-running";
  }
  const jar = findBackendJar();
  if (!jar) {
    console.warn("[launcher] 未找到 neuramesh-api*.jar，跳过自动启动（请手动启动后端）");
    return "no-jar";
  }

  const javaCmd = findJavaCmd();
  const logPath = path.join(app.getPath("userData"), "backend.log");
  const logFd = fs.openSync(logPath, "a");
  console.log(`[launcher] 拉起后端: ${javaCmd} -jar ${jar}（日志 ${logPath}）`);
  spawnFailed = false;
  backendProc = spawn(javaCmd, ["-jar", jar], {
    stdio: ["ignore", logFd, logFd],
    windowsHide: true,
  });
  backendProc.on("error", () => {
    // Windows 下 java 不存在为异步 ENOENT
    spawnFailed = true;
    backendProc = null;
  });
  backendProc.on("exit", () => {
    backendProc = null;
  });

  for (let i = 0; i < 40; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    if (spawnFailed) {
      console.error("[launcher] java 不可用：请安装 JDK 17+ 后重试");
      return "no-java";
    }
    if (await probe()) {
      console.log("[launcher] 后端就绪");
      return "started";
    }
    if (backendProc == null) {
      console.error("[launcher] 后端进程提前退出，详见 backend.log");
      return "no-java";
    }
  }
  console.error("[launcher] 等待后端就绪超时（60s），详见 backend.log");
  return "timeout";
}

/** 关闭由本启动器拉起的后端（外部已有的后端不受影响）。 */
export function stopBackend(): void {
  if (backendProc && backendProc.exitCode == null) {
    console.log("[launcher] 关闭内置后端");
    backendProc.kill();
  }
  backendProc = null;
}
