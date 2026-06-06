#!/usr/bin/env sh
# NeuraMesh 启动编排辅助脚本：等待种子节点与 API 就绪后打印拓扑状态。
# 用法：在 docker-compose 网络内运行，或本机 docker compose up 后执行 `sh docker/init.sh`。
set -eu

API_HOST="${API_HOST:-localhost}"
API_PORT="${API_PORT:-8080}"
SEED_HOSTS="${SEED_HOSTS:-localhost:30001 localhost:30002 localhost:30003 localhost:30004}"

echo "[init] 等待 API 就绪 http://${API_HOST}:${API_PORT} ..."
i=0
until curl -sf "http://${API_HOST}:${API_PORT}/chain/stats" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "[init] API 等待超时" >&2
    exit 1
  fi
  sleep 1
done
echo "[init] API 已就绪"

echo "[init] 检查种子节点 TCP 端口 ..."
for hp in $SEED_HOSTS; do
  host="${hp%%:*}"
  port="${hp##*:}"
  if nc -z "$host" "$port" 2>/dev/null; then
    echo "[init]   种子节点 $hp 在线"
  else
    echo "[init]   种子节点 $hp 暂未就绪"
  fi
done

echo "[init] 触发 API 注册演示设备（资源组任务可在控制台手动分配）..."
for m in Jetson-Orin RTX-4090 Mac-Studio-M2 Jetson-Nano A100-40G Ryzen-7950X M3-Max RTX-3060; do
  curl -sf -X POST "http://${API_HOST}:${API_PORT}/node/register" \
    -H 'Content-Type: application/json' \
    -d "{\"deviceModel\":\"${m}\"}" >/dev/null 2>&1 || true
done

echo "[init] 完成。控制台: http://localhost:8088  API: http://${API_HOST}:${API_PORT}/chain/stats"
