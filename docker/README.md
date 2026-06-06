# NeuraMesh Docker 一键启动

一条命令拉起 **4 个种子节点（P2P 网格）+ API 网关 + 控制台前端**。

## 前置条件
- Docker 24.x + Docker Compose v2
- 仓库根目录下执行（构建上下文需访问全部 Gradle 模块与前端工程）

## 启动
```bash
docker compose -f docker/docker-compose.yml up --build
```

## 访问
| 服务 | 地址 |
|------|------|
| 控制台 Dashboard | http://localhost:8088 |
| API 网关 | http://localhost:8080/chain/stats |
| 种子节点 1-4（P2P/TCP） | localhost:30001 ~ 30004 |

## 拓扑
- `seed-node-1` 为 bootstrap（无上游），`seed-node-2/3/4` 通过 `NEURA_PEERS` 依次连入，形成网格。
- `api` 为独立 Spring Boot 网关（内置内存链 + 资源组），启动即自初始化 4 验证者。
- `dashboard` 由 Nginx 托管 Vite 静态文件，`/api/*` 反向代理到 `api:8080`。

## 可选：就绪检查与播种
容器全部 up 后可运行（需本机有 curl/nc）：
```bash
sh docker/init.sh
```
脚本等待 API 就绪、探测种子节点端口、并注册 8 台演示设备。

## 说明 / 债务
- 当前 `api` 的链状态与种子节点 P2P 进程相互独立（演示态：api 自带内存链）。真实「种子节点共识 → API 读取」装配留待后续 Pause。
- 镜像构建使用腾讯云 Gradle 镜像与 npmmirror 加速（见 Dockerfile）。
- 本环境未安装 Docker，compose 文件未在本机执行验证；请在装有 Docker 的环境运行。
