# NeuraMesh

去中心化边缘智算网络（DePIN）。从零自研区块链：BFT-PoS 共识、P2P 网络、DePIN 状态机、设备指纹，
并提供 Spring Boot 网关、Electron 节点客户端与 React 厂商控制台/区块链浏览器。

## 技术栈
- Java 17 · Gradle 8.10.2 · RocksDB 8.x · Netty 4.1 · Kryo 5 · BouncyCastle 1.80
- Spring Boot 3.2.10（API 网关）
- React 18 + TypeScript + Vite · Electron 28 · Recharts
- 测试：JUnit 5 + AssertJ + JaCoCo（后端）、Vitest + Testing Library（前端）

## 模块
| 模块 | 阶段 | 说明 |
|---|---|---|
| neuramesh-core | P0 | Block / Transaction / CryptoUtils / MerkleTree |
| neuramesh-storage | P0 | RocksDB 分区封装 |
| neuramesh-network | P1 | Netty P2P / Gossip / 交易广播 / 区块同步 |
| neuramesh-consensus | P1+P2 | TxPool + BFT-PoS（PBFT 三阶段） |
| neuramesh-vm | P3 | 状态机 + 4 种交易处理器 + 权重交叉验证 |
| neuramesh-benchmark | P3 | 设备 Benchmark + 指纹 |
| neuramesh-api | P4 | Spring Boot 网关（Node/Vendor/Chain API） |
| neuramesh-node | P4 | Electron 节点客户端 |
| neuramesh-dashboard | P4 | React 厂商控制台 + 区块链浏览器 |

## 构建与测试（后端）
```powershell
.\gradlew.bat test            # 全部后端测试 + JaCoCo 聚合
.\gradlew.bat :neuramesh-api:bootRun   # 启动 API 网关（:8080）
```
覆盖率报告：`neuramesh-test\build\reports\jacoco\testCodeCoverageReport\`

## 一键启动演示（Windows PowerShell）
```powershell
# 首次：安装前端依赖（自动）
.\scripts\setup-frontend.ps1
# 一键启动后端 + 控制台 + 节点客户端（各开一个窗口）
.\scripts\dev-all.ps1
```
或分别启动：
```powershell
.\scripts\run-backend.ps1     # API 网关 http://localhost:8080
.\scripts\run-dashboard.ps1   # 控制台/浏览器 http://localhost:5173
.\scripts\run-node.ps1        # 节点客户端（Electron 桌面窗口）
```

## 演示动线
1. 启动后端 → 2. 控制台「硬件墙」点「接入 8 台演示设备」（节点注册上链）→
3. 「厂商控制台」发布任务（按权重链上结算）→ 4. 「区块浏览器」查看区块/交易、硬件墙看收益。

> 后端为内存链，重启即重置。

## 主要 API
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /node/register | 注册节点（设备型号）|
| GET | /node/{id}/status | 节点状态 |
| POST | /node/start \| /node/stop | 启停节点 |
| GET | /node/{id}/earnings?days=7 | 收益曲线 |
| POST | /task/submit | 提交任务（厂商结算）|
| GET | /vendor/{id}/balance | 厂商余额 |
| GET | /chain/blocks?limit=20 | 最新区块 |
| GET | /chain/tx/{hash} | 交易详情 |
| GET | /chain/node/{id} | 节点档案 |

统一返回：`{ "code": 0, "data": {...}, "message": "ok" }`

## 设计系统（前端）
oklch 色彩 token · Space Grotesk / JetBrains Mono / Plus Jakarta Sans · linear / notion-pre-ai / bloomberg-terminal / tufte-dataink 风格配方。

## 已知债务
真实 AI 推理（TFLite/ONNX）、移动端、排行榜、WebSocket 实时推送、真实设备接入、状态 RocksDB 持久化装配 — 留待后续 Pause。