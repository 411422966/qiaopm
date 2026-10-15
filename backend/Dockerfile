# =============================================================================
# Meiyetec API — 后端镜像
# 多阶段构建：COPY 预编译 jar → Eclipse Temurin JRE 17 运行时
# 适用：本地 docker compose（容器端口 8080）+ 生产 SRE 蓝绿发布（DEPLOY-SOP §2）
# 镜像标签约定：
#   - dev：本地 `docker build -t meiyetec/api:dev .`
#   - ci：CI build（docker-build job，push=false）
#   - 生产：meiyetec/api:${GIT_SHA}（短 SHA）/ meiyetec/api:2.0.${BUILD_NUMBER}
#
# 前置要求（必须先做，否则构建失败）：
#   mvn "-Dmaven.test.skip=true" package      # 1 次即可（如已生成 target/meiyetec-api-*.jar）
#
# 构建示例：
#   cd backend
#   docker build -t meiyetec/api:dev .
#
# 设计取舍：
#   - 不在 Dockerfile 内跑 mvn package（国内 maven central 慢，会卡 30min+）
#   - CI 已在 backend job 跑过 mvn，docker-build job 只需 COPY 预编译 jar
#   - 本地开发者只需先跑一次 mvn，再反复 docker build（依赖层命中缓存）
# =============================================================================

# -----------------------------------------------------------------------------
# Builder stage：COPY 预编译 jar
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS builder

WORKDIR /build

# COPY 预编译 jar（mvn package 产物）
#   路径相对于 build context（= backend/）
#   如未生成 jar，docker build 报错：提示先跑 mvn package
COPY target/meiyetec-api-*.jar /build/app.jar

# -----------------------------------------------------------------------------
# Runtime stage：JRE 17 运行时（剥离编译工具，只保留 JRE）
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# 元数据（OCI label）
LABEL org.opencontainers.image.title="meiyetec-api" \
      org.opencontainers.image.description="美业SaaS系统后端API" \
      org.opencontainers.image.source="https://github.com/meiyetec/qiaopm" \
      org.opencontainers.image.licenses="Proprietary"

# tini 作为 PID 1（正确转发信号 + 收割僵尸进程）
# alpine 自带 tini 包（apk add），替代之前从 GitHub 下载的方案：
# GitHub release 在国内/受限网络常被屏蔽，ADD https:// 会"静默成功"
# （缓存 404 HTML 而非报错），导致容器 ENTRYPOINT 启动即崩（exit 255）
# 当前 hotfix 引入 — issue 编号待 audit/issues/ 登记后回填
RUN apk add --no-cache tini

# 非 root 用户（uid 1000）
RUN addgroup -S -g 1000 meiyetec \
    && adduser  -S -u 1000 -G meiyetec -h /app -s /sbin/nologin meiyetec \
    && mkdir -p /app /opt/plugins \
    && chown -R meiyetec:meiyetec /app /opt/plugins

WORKDIR /app

# 从 builder 拷贝可执行 jar（Spring Boot fat jar）
COPY --from=builder --chown=meiyetec:meiyetec /build/app.jar /app/app.jar

# 暴露端口（Spring Boot 默认 8080）
EXPOSE 8080

# 健康检查端点（Spring Boot Actuator；如未启用 actuator，需在 application.yml 暴露 /healthz）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://127.0.0.1:8080/actuator/health || wget -q -O- http://127.0.0.1:8080/healthz || exit 1

# 容器感知 JVM 参数：
#   -XX:+UseContainerSupport      读取 cgroup 内存/CPU 限制（默认开启，显式声明）
#   -XX:MaxRAMPercentage=75.0     堆内存占容器内存上限 75%（剩 25% 给 JVM 内部/直接内存）
#   -XX:+ExitOnOutOfMemoryError   OOM 时快速退出（由编排器/K8s 重启，符合生产期望）
#   -Djava.security.egd=file:/dev/./urandom  加速 Tomcat 启动（SecureRandom）
USER meiyetec:meiyetec

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true" \
    SPRING_PROFILES_ACTIVE=prod \
    PLUGIN_PLUGINS_ROOT=/opt/plugins \
    TZ=Asia/Shanghai

# 插件外挂卷：宿主机 plugins 目录挂到 /opt/plugins
VOLUME ["/opt/plugins"]

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]