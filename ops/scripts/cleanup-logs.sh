#!/usr/bin/env bash
# ops/scripts/cleanup-logs.sh —— 清理超过 KEEP_DAYS 天的历史日志归档(后端 + nginx),
# 不碰当前活跃文件(cnotes.log/cnotes-error.log/access.log/error.log 文件名不带日期/序号后缀,
# 天然不会被下面的 find 匹配条件命中)。
# 用法: sudo ./scripts/cleanup-logs.sh [KEEP_DAYS]   # 不传默认 14 天
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "请用 sudo/root 运行本脚本" >&2; exit 1; }

KEEP_DAYS="${1:-14}"
[[ "$KEEP_DAYS" =~ ^[0-9]+$ ]] || { echo "KEEP_DAYS 必须是正整数,收到: $KEEP_DAYS" >&2; exit 1; }

BACKEND_LOG_DIR=/root/service/cnotes/logs
NGINX_LOG_DIR=/var/log/nginx

step(){ printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }

file_size() { stat -c%s "$1" 2>/dev/null || stat -f%z "$1" 2>/dev/null || echo 0; }

cleanup_dir() {
  local dir="$1" desc="$2"
  shift 2
  [ -d "$dir" ] || { echo "$desc 目录不存在,跳过: $dir"; return; }

  local count=0 freed=0 f
  while IFS= read -r -d '' f; do
    freed=$((freed + $(file_size "$f")))
    rm -f "$f" || { echo "  删除失败,跳过: $f" >&2; continue; }
    count=$((count + 1))
    echo "  删除: $f"
  done < <(find "$dir" \( "$@" \) -type f -mtime "+${KEEP_DAYS}" -print0)

  if [ "$count" -eq 0 ]; then
    echo "$desc:没有超过 ${KEEP_DAYS} 天的历史文件"
  else
    echo "$desc:共删除 ${count} 个文件,释放约 $((freed / 1024 / 1024))MB"
  fi
}

step "清理后端历史日志(${BACKEND_LOG_DIR},保留 ${KEEP_DAYS} 天)"
cleanup_dir "$BACKEND_LOG_DIR" "后端日志" -name '*.gz'

step "清理 nginx 历史日志(${NGINX_LOG_DIR},保留 ${KEEP_DAYS} 天)"
cleanup_dir "$NGINX_LOG_DIR" "nginx 日志" -name '*.gz' -o -name '*.log.[0-9]*'
