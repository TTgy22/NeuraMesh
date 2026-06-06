# API 网关镜像：多阶段构建。构建期产出 Spring Boot bootJar，运行期 JRE 17 启动。
# 构建上下文 = 仓库根目录（见 docker-compose.yml 的 context: ..）。

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :neuramesh-api:bootJar --no-daemon

# ---- runtime stage ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /src/neuramesh-api/build/libs/*.jar /app/api.jar
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
