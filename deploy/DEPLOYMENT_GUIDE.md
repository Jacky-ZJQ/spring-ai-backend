# Spring AI 上线指导（master-deploy）

本文用于 `spring-ai-backend` + `spring-ai-portal` 在同一台云服务器的部署与日常维护。

## 1. 部署架构

- 运行方式：Docker Compose
- 服务清单：
  - `mysql`（数据库）
  - `backend`（Spring Boot）
  - `portal`（Nginx 托管前端，反向代理 `/api` 到 backend）
- 对外端口：
  - `80`（前端入口）
  - `22`（SSH）
  - `443`（可选，HTTPS）

## 2. 分支与仓库

- 后端仓库：<https://github.com/Jacky-ZJQ/spring-ai-backend.git>
- 前端仓库：<https://github.com/Jacky-ZJQ/spring-ai-portal.git>
- 部署分支：`master-deploy`

## 3. 服务器准备

```bash
# Ubuntu / Debian
apt update && apt install -y git curl
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker -v
docker compose version
```

```bash
# CentOS / RHEL（如需）
yum install -y git curl
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker -v
docker compose version
```

## 4. 首次部署（推荐：服务器可直接访问 GitHub）

```bash
mkdir -p /opt/spring-ai
cd /opt/spring-ai

git clone -b master-deploy https://github.com/Jacky-ZJQ/spring-ai-backend.git spring-ai-backend
git clone -b master-deploy https://github.com/Jacky-ZJQ/spring-ai-portal.git spring-ai-portal
```

### 4.1 配置环境变量

```bash
cd /opt/spring-ai/spring-ai-backend
cp .env.prod.example .env.prod
vi .env.prod
```

至少修改以下项：

- `OPENAI_API_KEY`
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `PORTAL_BUILD_CONTEXT=../spring-ai-portal`
- `OLLAMA_BASE_URL`（如云服务器不跑 Ollama，请填写可访问的外部地址）

### 4.2 启动服务

```bash
cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

### 4.3 验证服务

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 portal
```

访问：

- `http://<你的公网IP>/`
- `http://<你的公网IP>/api/actuator/health`

## 5. 标准发布与回滚（脚本化，推荐）

本节放在首次部署后立即执行，后续所有发布都使用脚本。

### 5.1 安装 `deploy.sh`

```bash
install -m 755 /opt/spring-ai/spring-ai-backend/deploy/deploy.sh /opt/spring-ai/deploy.sh
```

说明：

- 仓库内置脚本路径：`/opt/spring-ai/spring-ai-backend/deploy/deploy.sh`
- 安装到服务器快捷入口后，统一通过 `/opt/spring-ai/deploy.sh` 调用
- 新脚本会按 `TARGET` 精确拉仓库：
  - `pull backend` 只更新 `spring-ai-backend`
  - `pull portal` 只更新 `spring-ai-portal`
  - `pull all` 才会同时更新两个仓库
- 不带参数时默认等价于 `/opt/spring-ai/deploy.sh nopull all`
- 发布完成后会自动执行一次 `curl http://127.0.0.1/api/actuator/health || true`

### 5.2 创建 `rollback.sh`

```bash
cat > /opt/spring-ai/rollback.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

BACKEND_COMMIT="${1:-}"
PORTAL_COMMIT="${2:-${1:-}}"

if [[ -z "$BACKEND_COMMIT" ]]; then
  echo "用法: /opt/spring-ai/rollback.sh <backend_commit> [portal_commit]"
  exit 1
fi

cd /opt/spring-ai/spring-ai-backend
git fetch origin
git checkout "$BACKEND_COMMIT"

cd /opt/spring-ai/spring-ai-portal
git fetch origin
git checkout "$PORTAL_COMMIT"

cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build backend portal
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
EOF
chmod +x /opt/spring-ai/rollback.sh
```

### 5.3 发布与回滚用法（整合了原 6.6/6.7/6.8）

```bash
# 标准发布：分别拉取 backend + portal 仓库，再发布前后端
/opt/spring-ai/deploy.sh pull all

# Bugfix 快速发布：只拉对应仓库，只发布对应服务
/opt/spring-ai/deploy.sh pull backend
/opt/spring-ai/deploy.sh pull portal

# GitHub 网络异常时：不拉代码，仅按当前目录重建发布
/opt/spring-ai/deploy.sh nopull backend
/opt/spring-ai/deploy.sh nopull portal
/opt/spring-ai/deploy.sh nopull all

# 查询可回滚提交
cd /opt/spring-ai/spring-ai-backend && git log --oneline -n 10
cd /opt/spring-ai/spring-ai-portal && git log --oneline -n 10

# 回滚（同版本或不同版本）
/opt/spring-ai/rollback.sh <commit_id>
/opt/spring-ai/rollback.sh <backend_commit_id> <portal_commit_id>
```

回滚或发布完成后建议立即验收：

```bash
cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
curl http://127.0.0.1/api/actuator/health
```

## 6. 备用方案（服务器拉 GitHub 超时时）

在本地打包并上传：

```bash
# backend
cd /path/to/spring-ai-backend
git checkout master-deploy
tar --exclude=.git --exclude=target --exclude=storage --exclude=chat-pdf.properties --exclude=chat-pdf.json -czf /tmp/spring-ai-backend.tar.gz .

# portal
cd /path/to/spring-ai-portal
git checkout master-deploy
tar --exclude=.git --exclude=node_modules --exclude=dist -czf /tmp/spring-ai-portal.tar.gz .

# upload
scp /tmp/spring-ai-backend.tar.gz root@<公网IP>:/opt/spring-ai/
scp /tmp/spring-ai-portal.tar.gz root@<公网IP>:/opt/spring-ai/
```

服务器解压：

```bash
cd /opt/spring-ai
rm -rf spring-ai-backend spring-ai-portal spring-ai-protal
mkdir -p spring-ai-backend spring-ai-portal
tar -xzf spring-ai-backend.tar.gz -C spring-ai-backend
tar -xzf spring-ai-portal.tar.gz -C spring-ai-portal
```

然后执行：

```bash
/opt/spring-ai/deploy.sh nopull all
```

## 7. 日常运维

### 7.1 查看状态

```bash
cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep spring-ai
```

### 7.2 查看日志

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 mysql
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 portal
```

### 7.3 重启

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml restart
# or
docker compose --env-file .env.prod -f docker-compose.prod.yml restart backend
```

### 7.4 停止与启动

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml down
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

> 提示：`docker compose down` 不会删除卷，MySQL 数据会保留；不要执行 `down -v`。

## 8. 常见问题

### 8.1 `The "MYSQL_..." variable is not set`

原因：执行 `docker compose` 时没有带 `--env-file .env.prod`。  
解决：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

可选简化：

```bash
cp .env.prod .env
```

### 8.2 `unable to prepare context: path ".../spring-ai-protal" not found`

原因：前端目录名与 `PORTAL_BUILD_CONTEXT` 不一致。  
解决：确认 `.env.prod` 中 `PORTAL_BUILD_CONTEXT` 与真实目录一致（如 `../spring-ai-portal`）。

### 8.3 页面打不开

按顺序排查：

1. 检查云防火墙/安全组是否放行 `80`
2. `docker compose ... ps` 看 `portal` 是否 Up
3. 查看 `portal` 与 `backend` 日志

### 8.4 `git pull` 或 `docker build` 超时

常见现象：

- `fatal: unable to access 'https://github.com/...': Operation timed out`
- `docker build` 卡在拉 `node:20-alpine`、`nginx:alpine`、`maven:*` 等基础镜像

原因：云服务器访问 `GitHub` 或 `Docker Hub` 链路不稳定。

处理：

1. 如果代码已经在服务器目录里，直接执行 `nopull`：

```bash
/opt/spring-ai/deploy.sh nopull backend
/opt/spring-ai/deploy.sh nopull portal
```

2. 如果连基础镜像也拉不下来，改用“本地构建 + 上传镜像”或第 6 节的“本地打包源码再上传”方案。

## 9. 安全建议

- 对公网仅保留：`22`、`80`、`443`
- 关闭公网暴露：`3306`、`8080`、`9000`（如非必要）
- `OPENAI_API_KEY` 仅放在 `.env.prod`，不要提交到 Git
- 定期更换数据库密码与 API Key

## 10. 建议提交到仓库的文件

- `DEPLOYMENT_GUIDE.md`（本文件）
- `.env.prod.example`（模板，可提交）

不要提交：

- `.env.prod`
- 任何真实密钥、密码
