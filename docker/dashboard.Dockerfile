# 控制台前端镜像：多阶段构建。构建期 Node 18 产出 Vite 静态文件，运行期 Nginx 托管。
# 构建上下文 = 仓库根目录（见 docker-compose.yml 的 context: ..）。

# ---- build stage ----
FROM node:18-alpine AS build
WORKDIR /src
COPY neuramesh-dashboard/package.json neuramesh-dashboard/package-lock.json* ./
# 使用 npmmirror 加速依赖安装（中国大陆）
RUN npm config set registry https://registry.npmmirror.com && npm install
COPY neuramesh-dashboard/ ./
# 容器内 API 走相对路径 /api，由 nginx 反向代理到 api 服务
ENV VITE_API_BASE=/api
RUN npm run build

# ---- runtime stage ----
FROM nginx:1.27-alpine AS runtime
COPY --from=build /src/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
