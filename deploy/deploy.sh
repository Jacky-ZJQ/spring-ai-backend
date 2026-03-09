#!/usr/bin/env bash
set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
  LINK_TARGET="$(readlink "$SCRIPT_PATH")"
  if [[ "$LINK_TARGET" = /* ]]; then
    SCRIPT_PATH="$LINK_TARGET"
  else
    SCRIPT_DIR_TMP="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
    SCRIPT_PATH="$(cd "$SCRIPT_DIR_TMP" && cd "$(dirname "$LINK_TARGET")" && pwd)/$(basename "$LINK_TARGET")"
  fi
done

SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
PORTAL_DIR_DEFAULT="$BASE_DIR/spring-ai-portal"

BRANCH="${BRANCH:-master-deploy}"
COMPOSE_FILE_REL="${COMPOSE_FILE:-deploy/docker-compose.prod.yml}"
ENV_FILE_REL="${ENV_FILE:-.env.prod}"

COMPOSE_FILE_PATH="$BACKEND_DIR/$COMPOSE_FILE_REL"
ENV_FILE_PATH="$BACKEND_DIR/$ENV_FILE_REL"

MODE="${1:-nopull}"
TARGET="${2:-all}"

log() {
  echo "[deploy] $*"
}

warn() {
  echo "[deploy][warn] $*" >&2
}

fail() {
  echo "[deploy][error] $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
用法:
  deploy.sh [pull|nopull] [all|backend|portal]

示例:
  deploy.sh pull all
  deploy.sh pull backend
  deploy.sh pull portal
  deploy.sh nopull all
  deploy.sh nopull backend
  deploy.sh nopull portal
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

require_dir() {
  local dir="$1"
  local label="$2"
  [[ -d "$dir" ]] || fail "$label 不存在: $dir"
}

require_file() {
  local file="$1"
  local label="$2"
  [[ -f "$file" ]] || fail "$label 不存在: $file"
}

get_env_value() {
  local key="$1"
  local file="$2"
  awk -F= -v k="$key" '
    $0 !~ /^[[:space:]]*#/ && $1 == k {
      print substr($0, index($0, "=")+1)
    }
  ' "$file" | tail -n1
}

compose() {
  docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" "$@"
}

check_prerequisites() {
  require_cmd git
  require_cmd docker
  require_cmd curl
  require_cmd ss

  require_dir "$BACKEND_DIR" "backend 目录"
  require_file "$COMPOSE_FILE_PATH" "Compose 文件"
  require_file "$ENV_FILE_PATH" "环境变量文件"
}

load_config() {
  PORTAL_PORT="$(get_env_value PORTAL_PORT "$ENV_FILE_PATH")"
  PORTAL_PORT="${PORTAL_PORT:-8080}"

  PORTAL_BUILD_CONTEXT="$(get_env_value PORTAL_BUILD_CONTEXT "$ENV_FILE_PATH")"
  PORTAL_BUILD_CONTEXT="${PORTAL_BUILD_CONTEXT:-$PORTAL_DIR_DEFAULT}"

  if [[ "$PORTAL_BUILD_CONTEXT" != /* ]]; then
    PORTAL_BUILD_CONTEXT="$(cd "$BACKEND_DIR" && cd "$PORTAL_BUILD_CONTEXT" 2>/dev/null && pwd || true)"
  fi
}

validate_mode_target() {
  case "$MODE" in
    pull|nopull) ;;
    *)
      usage
      fail "无效 MODE: $MODE"
      ;;
  esac

  case "$TARGET" in
    all|backend|portal) ;;
    *)
      usage
      fail "无效 TARGET: $TARGET"
      ;;
  esac
}

validate_runtime_config() {
  load_config

  if [[ "$TARGET" == "portal" || "$TARGET" == "all" ]]; then
    [[ -n "$PORTAL_PORT" ]] || fail "PORTAL_PORT 不能为空"
    [[ "$PORTAL_PORT" =~ ^[0-9]+$ ]] || fail "PORTAL_PORT 必须是数字，当前值: $PORTAL_PORT"

    require_dir "$PORTAL_BUILD_CONTEXT" "portal 构建目录"

    if ss -lnt "( sport = :$PORTAL_PORT )" | grep -q LISTEN; then
      if docker ps --format '{{.Names}} {{.Ports}}' | grep -q "^spring-ai-portal "; then
        log "检测到 spring-ai-portal 已占用端口 $PORTAL_PORT，将交由 compose 更新"
      else
        fail "端口 $PORTAL_PORT 已被其他进程占用，请修改 .env.prod 或释放该端口"
      fi
    fi
  fi
}

reset_local_changes_if_needed() {
  local repo_name="$1"

  if ! git diff --quiet || ! git diff --cached --quiet; then
    warn "$repo_name 仓库存在本地未提交修改，将自动丢弃本地改动"
    git reset --hard HEAD
  fi

  if [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    warn "$repo_name 仓库存在未跟踪文件，保留不删除"
  fi
}

pull_repo() {
  local repo_dir="$1"
  local repo_name="$2"

  require_dir "$repo_dir" "$repo_name 目录"

  log "更新 $repo_name 仓库: $repo_dir"
  (
    cd "$repo_dir"
    reset_local_changes_if_needed "$repo_name"
    git fetch origin
    git checkout "$BRANCH"
    git pull --ff-only origin "$BRANCH"
  )
}

backup_env_once() {
  cp -n "$ENV_FILE_PATH" "${ENV_FILE_PATH}.bak" >/dev/null 2>&1 || true
}

cleanup_portal_container() {
  if docker ps -a --format '{{.Names}}' | grep -Fxq spring-ai-portal; then
    log "清理旧 portal 容器"
    docker rm -f spring-ai-portal >/dev/null 2>&1 || true
  fi
}

deploy_services() {
  cd "$BACKEND_DIR"
  backup_env_once

  log "发布服务: $*"
  compose up -d --build "$@"

  log "当前服务状态"
  compose ps
}

wait_http_ok() {
  local url="$1"
  local label="$2"
  local max_attempts="${3:-20}"
  local i=1

  while (( i <= max_attempts )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$label 检查通过: $url"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done

  warn "$label 检查失败: $url"
  return 1
}

health_check_backend() {
  wait_http_ok "http://127.0.0.1:8081/actuator/health" "backend 健康检查" 20 || true
}

health_check_portal() {
  wait_http_ok "http://127.0.0.1:${PORTAL_PORT}/" "portal 可访问性检查" 20 || true
}

show_summary() {
  log "部署完成"
  echo
  echo "backend 目录: $BACKEND_DIR"
  echo "compose 文件: $COMPOSE_FILE_PATH"
  echo "env 文件:     $ENV_FILE_PATH"
  echo "portal 目录:  ${PORTAL_BUILD_CONTEXT}"
  echo "portal 端口:  ${PORTAL_PORT}"
  echo
}

main() {
  validate_mode_target
  check_prerequisites
  validate_runtime_config

  if [[ "$MODE" == "pull" ]]; then
    case "$TARGET" in
      backend)
        pull_repo "$BACKEND_DIR" "backend"
        ;;
      portal)
        pull_repo "$PORTAL_BUILD_CONTEXT" "portal"
        ;;
      all)
        pull_repo "$BACKEND_DIR" "backend"
        pull_repo "$PORTAL_BUILD_CONTEXT" "portal"
        ;;
    esac
  fi

  case "$TARGET" in
    backend)
      deploy_services backend
      health_check_backend
      ;;
    portal)
      cleanup_portal_container
      deploy_services portal
      health_check_portal
      ;;
    all)
      cleanup_portal_container
      deploy_services backend portal
      health_check_portal
      health_check_backend
      ;;
  esac

  show_summary
}

main "$@"