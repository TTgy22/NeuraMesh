# 种子节点镜像：多阶段构建。构建期用 JDK 17 + Gradle Wrapper 产出 application 分发，
# 运行期仅 JRE 17，启动 com.neuramesh.network.SeedNode。
# 构建上下文 = 仓库根目录（见 docker-compose.yml 的 context: ..）。

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
# 仅构建种子节点所需分发，加速镜像构建
RUN chmod +x gradlew && ./gradlew :neuramesh-network:installDist --no-daemon

# ---- runtime stage ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /src/neuramesh-network/build/install/neura-seed/ /app/
# 监听端口由 NEURA_PORT 注入（默认 30000）
ENV NEURA_PORT=30000
EXPOSE 30000
ENTRYPOINT ["/app/bin/neura-seed"]
