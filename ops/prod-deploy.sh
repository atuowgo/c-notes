#!/usr/bin/env bash
# c-notes 生产部署脚本:编译/打包 → 推送 → 启动 → 健康检查
# 用法:
#   ./ops/prod-deploy.sh build      # 本机构建 jar + dist
#   ./ops/prod-deploy.sh push       # 推送 jar + dist 到生产机(保留上一版做回滚)
#   ./ops/prod-deploy.sh start      # 生产机重启服务 + reload nginx
#   ./ops/prod-deploy.sh health     # 健康检查
#   ./ops/prod-deploy.sh all        # build → push → start → health(默认)
# 配置(环境变量覆盖):
#   PROD_HOST=user@host   SSH 目标(必填,build/health 除外)
#   PROD_DIR=/opt/cnotes  生产部署目录
#   WEB_DIR=/var/www/cnotes/dist  前端静态目录
#   NGINX_CONF=ops/nginx/cnotes.dev.conf  可选:同步 nginx 配置
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROD_HOST="${PROD_HOST:-}"
PROD_DIR="${PROD_DIR:-/opt/cnotes}"
WEB_DIR="${WEB_DIR:-/var/www/cnotes/dist}"
JAR="$ROOT/server/build/libs/cnotes-0.0.1-SNAPSHOT.jar"
DIST="$ROOT/frontend/apps/web/dist"

step(){ printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
need_host(){ [ -n "$PROD_HOST" ] || { echo "PROD_HOST 未设置(export PROD_HOST=user@host)"; exit 1; }; }

build(){
  step "编译后端(server/gradlew build)"
  ( cd "$ROOT/server" && ./gradlew build )
  step "构建前端(pnpm -r build)"
  ( cd "$ROOT/frontend" && pnpm install && pnpm -r build )
  step "产物"
  ls -lh "$JAR" && echo "dist: $(du -sh "$DIST" | cut -f1)"
}

push(){
  need_host
  step "推送 jar + dist 到 $PROD_HOST:$PROD_DIR / $WEB_DIR"
  # 后端:备份上一版 → 上传新 jar
  ssh "$PROD_HOST" "sudo mkdir -p $PROD_DIR/data && [ -f $PROD_DIR/cnotes.jar ] && sudo cp $PROD_DIR/cnotes.jar $PROD_DIR/cnotes.jar.bak || true"
  rsync -az --rsync-path='sudo rsync' "$JAR" "$PROD_HOST:$PROD_DIR/cnotes.jar.new"
  ssh "$PROD_HOST" "sudo mv $PROD_DIR/cnotes.jar.new $PROD_DIR/cnotes.jar"
  # 前端 dist
  ssh "$PROD_HOST" "sudo mkdir -p $WEB_DIR"
  rsync -az --delete --rsync-path='sudo rsync' "$DIST/" "$PROD_HOST:$WEB_DIR/"
  # nginx 配置(可选)
  if [ -n "${NGINX_CONF:-}" ] && [ -f "$ROOT/$NGINX_CONF" ]; then
    scp "$ROOT/$NGINX_CONF" "$PROD_HOST:/tmp/cnotes.conf"
    ssh "$PROD_HOST" "sudo mv /tmp/cnotes.conf /etc/nginx/conf.d/cnotes.conf && sudo nginx -t"
  fi
  echo "推送完成。生产 .env 需在 $PROD_DIR/.env(含 DEEPSEEK_API_KEY/ARK_API_KEY/JWT_SECRET/STORAGE_ROOT 等,见 docs/ops/prod-deploy.md)"
}

start(){
  need_host
  step "重启 cnotes + reload nginx"
  ssh "$PROD_HOST" "sudo systemctl daemon-reload; sudo systemctl restart cnotes; sudo systemctl reload nginx || sudo systemctl restart nginx"
  echo "等待启动..."; sleep 6
  ssh "$PROD_HOST" "sudo systemctl --no-pager -l status cnotes | head -15"
}

health(){
  need_host
  step "健康检查"
  ssh "$PROD_HOST" "curl -sS -i http://127.0.0.1:8080/actuator/health || echo 'health endpoint 不可达,看 journalctl -u cnotes'"
  echo
  echo "浏览器验证:https://<域名>/ → /login → 注册/登录 → 收件箱(全功能见 docs/ops/local-verify-stage6.md)"
}

rollback(){
  need_host
  step "回滚到 cnotes.jar.bak"
  ssh "$PROD_HOST" "sudo systemctl stop cnotes; sudo cp $PROD_DIR/cnotes.jar.bak $PROD_DIR/cnotes.jar; sudo systemctl start cnotes"
  echo "已回滚(数据库迁移不自动 undo;新表为新增,旧 jar 兼容)"
}

case "${1:-all}" in
  build) build;;
  push) push;;
  start) start;;
  health) health;;
  rollback) rollback;;
  all) build; push; start; health;;
  *) echo "用法: $0 {build|push|start|health|rollback|all}"; exit 1;;
esac
