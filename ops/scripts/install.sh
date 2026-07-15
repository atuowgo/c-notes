#!/usr/bin/env bash
# ops/scripts/install.sh —— 生产机一次性安装脚本(在解压后的物料包目录内执行)。
# 前置:Ubuntu 机器已装好 JDK21 / MariaDB 11(或 MySQL 8)/ nginx —— 本脚本不装这三者,只做
#       目录/建库建账号/.env/systemd/nginx 的接线。可重复执行:首次安装会建库、生成
#       .env 并等待手工填密钥;之后再跑(.env 已存在)只刷新 jar/dist/systemd/nginx 并重启服务,
#       不会碰已存在的 .env,也不会重跑建库(避免生成新密码却未真正生效的错配)。
# 服务进程以 root 运行(部署目录在 /root 下,故 systemd 单元 User=root,非独立低权账号)。
# 用法(生产机,root/sudo):
#   tar xzf cnotes-release-*.tar.gz && cd cnotes-release-*
#   sudo ./scripts/install.sh
# 环境变量覆盖(可选):
#   SERVER_NAME=cnotes.example.com   nginx server_name,默认 _ (通配,先用 IP 直接访问)
#   LISTEN_PORT=38088               nginx 监听端口,默认 80(云主机 80/443 常受安全组/ICP 限制时改用自定义端口)
#   SKIP_DB=1                       首次安装时跳过建库建账号(数据库已自行准备时,.env 需手工填三项)
#   DB_FORCE_RUN=1                  已有 .env 的情况下仍强制重跑建库(如 DB 被误删但 .env 还在)
#   DB_NAME/DB_USER/DB_PASSWORD/DB_ADMIN_USER/DB_ADMIN_HOST/DB_ADMIN_PASSWORD
#                                   透传给 scripts/install-db.sh,见该脚本注释
# 密码轮换:本脚本只在首次安装时建库;之后要换 cnotes 数据库账号密码,直接单独重跑
#   DB_PASSWORD=newpw ./scripts/install-db.sh,再手工把新密码同步进 /root/service/cnotes/.env 并重启服务。
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "请用 sudo/root 运行本脚本" >&2; exit 1; }

PKG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR=/root/service/cnotes
WEB_DIR=/var/www/cnotes/dist
ENV_FILE="$INSTALL_DIR/.env"

step(){ printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }

step "校验物料包完整性"
for f in backend/cnotes.jar frontend/dist/index.html systemd/cnotes.service \
         nginx/cnotes.prod.conf env/cnotes.env.example scripts/install-db.sh sql/00-init-database.sql; do
  [ -e "$PKG_ROOT/$f" ] || { echo "缺少 $f,物料包不完整" >&2; exit 1; }
done

# 首次安装 vs 更新,提前判定:决定是否需要建库、是否需要在结尾自动重启。
if [ -f "$ENV_FILE" ]; then FRESH_ENV=0; else FRESH_ENV=1; fi

step "创建目录"
mkdir -p "$INSTALL_DIR/data" "$INSTALL_DIR/logs" "$WEB_DIR"

step "部署后端 jar"
if [ "$FRESH_ENV" -eq 0 ] && [ -f "$INSTALL_DIR/cnotes.jar" ]; then
  cp "$INSTALL_DIR/cnotes.jar" "$INSTALL_DIR/cnotes.jar.bak"
fi
install -m 644 "$PKG_ROOT/backend/cnotes.jar" "$INSTALL_DIR/cnotes.jar"

step "部署前端静态产物"
if [ "$FRESH_ENV" -eq 0 ] && [ -d "$WEB_DIR" ] && [ -n "$(ls -A "$WEB_DIR" 2>/dev/null)" ]; then
  rsync -a --delete "$WEB_DIR/" "$WEB_DIR.bak/"
fi
rsync -a --delete "$PKG_ROOT/frontend/dist/" "$WEB_DIR/"

DB_CREDS_FILE="$(mktemp)"
chmod 600 "$DB_CREDS_FILE"
trap 'rm -f "$DB_CREDS_FILE"' EXIT

if { [ "$FRESH_ENV" -eq 1 ] || [ "${DB_FORCE_RUN:-0}" = "1" ]; } && [ "${SKIP_DB:-0}" != "1" ]; then
  step "建库建账号(MariaDB)"
  CREDENTIALS_OUT_FILE="$DB_CREDS_FILE" "$PKG_ROOT/scripts/install-db.sh"
elif [ "$FRESH_ENV" -eq 0 ]; then
  step "已有 .env,跳过建库(更新流程不重复建库;如需重置密码见脚本头部说明)"
else
  step "跳过建库(SKIP_DB=1);.env 的数据库连接三项需自行填写"
fi

if [ "$FRESH_ENV" -eq 1 ]; then
  step "生成 ${ENV_FILE}"
  ENV_TMP="$(mktemp)"
  chmod 600 "$ENV_TMP"
  ENV_CONTENT="$(cat "$PKG_ROOT/env/cnotes.env.example")"
  if [ -s "$DB_CREDS_FILE" ]; then
    CNOTES_DB_NAME=""; CNOTES_DB_USER=""; CNOTES_DB_PASSWORD=""
    # 按行读取,不对文件内容做 shell 求值(避免密码里的 shell 元字符被当命令执行)。
    while IFS='=' read -r _k _v; do
      case "$_k" in
        CNOTES_DB_NAME)     CNOTES_DB_NAME=$_v ;;
        CNOTES_DB_USER)     CNOTES_DB_USER=$_v ;;
        CNOTES_DB_PASSWORD) CNOTES_DB_PASSWORD=$_v ;;
      esac
    done < "$DB_CREDS_FILE"
    # 纯 bash 字符串替换渲染 .env(同 install-db.sh,不外部起进程、无分隔符冲突问题)。
    ENV_CONTENT="${ENV_CONTENT//__DB_NAME__/$CNOTES_DB_NAME}"
    ENV_CONTENT="${ENV_CONTENT//__DB_USER__/$CNOTES_DB_USER}"
    ENV_CONTENT="${ENV_CONTENT//__DB_PASSWORD__/$CNOTES_DB_PASSWORD}"
    echo "已生成 $ENV_FILE(数据库三项已自动填好;DEEPSEEK_API_KEY/ARK_API_KEY/JWT_SECRET 等仍需手工填写)"
  else
    echo "已生成 $ENV_FILE(数据库三项为空,SKIP_DB=1 场景需手工填写;其余密钥也需手工填写)"
  fi
  # 先落到限权临时文件再原子改名,全程不出现世界可读的中间态(模板本身在包里是 0644)。
  printf '%s\n' "$ENV_CONTENT" > "$ENV_TMP"
  mv "$ENV_TMP" "$ENV_FILE"
else
  echo "$ENV_FILE 已存在,不覆盖(如需更新数据库连接信息请手工编辑)"
fi
rm -f "$DB_CREDS_FILE"

step "安装 systemd 服务单元"
install -m 644 "$PKG_ROOT/systemd/cnotes.service" /etc/systemd/system/cnotes.service
systemctl daemon-reload
systemctl enable cnotes >/dev/null

step "安装 nginx 站点配置"
mkdir -p /etc/nginx/conf.d
NGINX_CONF=/etc/nginx/conf.d/cnotes.conf

# 没显式传 SERVER_NAME/LISTEN_PORT 时,优先读当前已生效配置作为默认值,避免重跑 install.sh
# (比如只同步了脚本、没重新指定端口)时把已经改过的设置静默覆盖回硬编码默认值。
if [ -z "${SERVER_NAME:-}" ] && [ -f "$NGINX_CONF" ]; then
  SERVER_NAME="$(grep -m1 -oE 'server_name +[^;]+' "$NGINX_CONF" | sed -E 's/server_name +//' || true)"
fi
SERVER_NAME="${SERVER_NAME:-_}"

if [ -z "${LISTEN_PORT:-}" ] && [ -f "$NGINX_CONF" ]; then
  LISTEN_PORT="$(grep -m1 -oE 'listen +[0-9]+' "$NGINX_CONF" | grep -oE '[0-9]+' || true)"
fi
LISTEN_PORT="${LISTEN_PORT:-80}"

NGINX_CONF_BAK=""
if [ -e "$NGINX_CONF" ]; then
  NGINX_CONF_BAK="$(mktemp)"
  cp "$NGINX_CONF" "$NGINX_CONF_BAK"
fi
NGINX_TEMPLATE_CONTENT="$(cat "$PKG_ROOT/nginx/cnotes.prod.conf")"
NGINX_TEMPLATE_CONTENT="${NGINX_TEMPLATE_CONTENT//__SERVER_NAME__/$SERVER_NAME}"
NGINX_TEMPLATE_CONTENT="${NGINX_TEMPLATE_CONTENT//__LISTEN_PORT__/$LISTEN_PORT}"
printf '%s\n' "$NGINX_TEMPLATE_CONTENT" > "$NGINX_CONF"
if [ -e /etc/nginx/sites-enabled/default ]; then
  echo "禁用 nginx 默认站点(避免抢占 80 端口): /etc/nginx/sites-enabled/default"
  rm -f /etc/nginx/sites-enabled/default
fi
if ! nginx -t; then
  echo "nginx 配置校验失败" >&2
  if [ -n "$NGINX_CONF_BAK" ]; then
    mv "$NGINX_CONF_BAK" "$NGINX_CONF"
    echo "已回滚到更新前的 $NGINX_CONF" >&2
  else
    rm -f "$NGINX_CONF"
    echo "本次是新装(此前无旧配置可回滚),已删除本次写入的 $NGINX_CONF" >&2
  fi
  exit 1
fi
if [ -n "$NGINX_CONF_BAK" ]; then rm -f "$NGINX_CONF_BAK"; fi
systemctl reload nginx 2>/dev/null || systemctl restart nginx

if [ "$FRESH_ENV" -eq 1 ]; then
  step "首次安装完成"
  echo "下一步:"
  echo "  1) 编辑 ${ENV_FILE},填入 DEEPSEEK_API_KEY / ARK_API_KEY / JWT_SECRET(见文件内注释)"
  echo "  2) 回到物料包目录执行: ./scripts/service.sh start"
else
  step "更新完成,重启服务"
  systemctl restart cnotes
  sleep 3
  systemctl --no-pager -l status cnotes | head -15
fi
