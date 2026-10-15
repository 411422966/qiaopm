# =============================================================================
# Meiyetec Web — 前端镜像
# 多阶段构建：Node 20 构建 → Nginx Alpine 提供静态资源 + 反代 /api
# 适用：本地 docker compose（容器端口 80，宿主 8088）
#       生产 SRE 既可保留 S3+CloudFront 方案（DEPLOY-SOP §3），也可改容器化部署
# 镜像标签约定：与后端 meiyetec/api 对齐（meiyetec/web:${GIT_SHA}）
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1：构建
# -----------------------------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /build

# 先 copy package.json 让 npm ci 命中缓存层
COPY package*.json ./
RUN npm ci || npm install

# copy 源码并构建
COPY . .
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2：运行时 — Nginx Alpine
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# 元数据
LABEL org.opencontainers.image.title="meiyetec-web" \
      org.opencontainers.image.description="美业SaaS前端 SPA" \
      org.opencontainers.image.source="https://github.com/meiyetec/qiaopm" \
      org.opencontainers.image.licenses="Proprietary"

# 删除默认 nginx 配置
RUN rm -f /etc/nginx/conf.d/default.conf

# 注入 SPA fallback + /api 反代规则（覆盖 nginx 默认 try_files）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 拷贝构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 非 root 用户（nginx:alpine 默认使用 nginx 用户，uid 101）
# 这里保留官方默认，仅确认端口
EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O- http://127.0.0.1/ > /dev/null || exit 1

# nginx:alpine 镜像默认 ENTRYPOINT 已正确转发信号（PID 1 = nginx）
CMD ["nginx", "-g", "daemon off;"]