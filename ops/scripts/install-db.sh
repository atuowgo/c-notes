#!/usr/bin/env bash
# ops/scripts/install-db.sh
# 建库 + 建应用专用账号(不复用 root);表结构由后端首次启动时 Flyway 自动迁移。
# 用法(生产机上,已装 mariadb-server/mysql-server 的机器执行):
#   ./install-db.sh                      # 默认走 sudo <client> 的 unix_socket 免密登录(新装 MariaDB 默认行为)
#   DB_ADMIN_USER=root DB_ADMIN_PASSWORD=xxx ./install-db.sh   # 管理员账号非 unix_socket 免密时显式提供
# 可重复执行:CREATE USER IF NOT EXISTS + 紧跟的 ALTER USER IF EXISTS 保证密码总被同步到本次传入值,
# 因此本脚本单独重跑(如 DB_PASSWORD=newpw ./install-db.sh)即是标准的密码轮换方式。
#
# 环境变量(均可覆盖,不填则用默认/自动生成):
#   DB_NAME               数据库名,默认 cnotes(仅允许字母数字下划线)
#   DB_USER               应用账号,默认 cnotes(仅允许字母数字下划线)
#   DB_PASSWORD           应用账号密码,不填则自动生成(openssl rand -hex 20)
#   DB_ADMIN_USER/HOST/PASSWORD  显式指定管理员账号走 TCP 登录(默认走本机 sudo unix_socket)
#   CREDENTIALS_OUT_FILE  指定则把最终 DB_NAME/DB_USER/DB_PASSWORD 写入该文件(600 权限),
#                         供 install.sh 拼装 /root/service/cnotes/.env 用;不指定则打印到终端。
set -euo pipefail

DB_NAME="${DB_NAME:-cnotes}"
DB_USER="${DB_USER:-cnotes}"
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -hex 20)}"

# 库名/账号名严格限定字符集:它们作为裸标识符(反引号/引号包裹)拼进 SQL,限定字符集
# 直接杜绝标识符注入,比事后转义更可靠。
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo "DB_NAME 含非法字符,仅允许字母数字下划线: $DB_NAME" >&2; exit 1; }
[[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || { echo "DB_USER 含非法字符,仅允许字母数字下划线: $DB_USER" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_TEMPLATE="$SCRIPT_DIR/../sql/00-init-database.sql"
[ -f "$SQL_TEMPLATE" ] || { echo "找不到 $SQL_TEMPLATE" >&2; exit 1; }

# 优先 mariadb 客户端(MariaDB 11 自带命令名),兼容回退 mysql(部分发行版仍装 mysql 别名)。
CLIENT_BIN=""
for c in mariadb mysql; do
  if command -v "$c" >/dev/null 2>&1; then CLIENT_BIN="$c"; break; fi
done
[ -n "$CLIENT_BIN" ] || { echo "未找到 mariadb/mysql 客户端,请确认已安装 mariadb-client" >&2; exit 1; }

# SQL 单引号字符串字面量转义:反斜杠、单引号(MySQL/MariaDB 默认 sql_mode 均为转义字符)。
sql_escape() {
  local s=$1
  s=${s//\\/\\\\}
  s=${s//\'/\\\'}
  printf '%s' "$s"
}
PW_SQL_ESCAPED="$(sql_escape "$DB_PASSWORD")"

# 模板渲染用纯 bash 字符串替换(不外部起进程,值不会出现在任何子进程 argv 里,也没有
# sed 分隔符冲突/特殊替换字符/跨平台 sed 方言差异这类问题)。
TEMPLATE_CONTENT="$(cat "$SQL_TEMPLATE")"
TEMPLATE_CONTENT="${TEMPLATE_CONTENT//__DB_NAME__/$DB_NAME}"
TEMPLATE_CONTENT="${TEMPLATE_CONTENT//__DB_USER__/$DB_USER}"
TEMPLATE_CONTENT="${TEMPLATE_CONTENT//__DB_PASSWORD__/$PW_SQL_ESCAPED}"

TMP_SQL="$(mktemp)"
chmod 600 "$TMP_SQL"
trap 'rm -f "$TMP_SQL"' EXIT
printf '%s\n' "$TEMPLATE_CONTENT" > "$TMP_SQL"

echo "== 建库/建账号: ${DB_NAME} / ${DB_USER}@localhost =="
if [ -n "${DB_ADMIN_USER:-}" ]; then
  # 密码走 MYSQL_PWD 环境变量传入,避免 -p 明文出现在 ps 输出的命令行参数里。
  MYSQL_PWD="${DB_ADMIN_PASSWORD:-}" "$CLIENT_BIN" -h "${DB_ADMIN_HOST:-127.0.0.1}" -u "$DB_ADMIN_USER" < "$TMP_SQL"
elif [ "$(id -u)" -ne 0 ]; then
  # 新装 MariaDB 默认 root 走 unix_socket 免密认证,需本机 sudo 执行。
  sudo "$CLIENT_BIN" < "$TMP_SQL"
else
  "$CLIENT_BIN" < "$TMP_SQL"
fi
echo "建库/建账号完成(密码已通过 ALTER USER IF EXISTS 同步,重复执行即可用于密码轮换)。"

if [ -n "${CREDENTIALS_OUT_FILE:-}" ]; then
  ( umask 177
    {
      echo "CNOTES_DB_NAME=${DB_NAME}"
      echo "CNOTES_DB_USER=${DB_USER}"
      echo "CNOTES_DB_PASSWORD=${DB_PASSWORD}"
    } > "$CREDENTIALS_OUT_FILE" )
  echo "账号信息已写入 ${CREDENTIALS_OUT_FILE}(600 权限)"
else
  echo "---- 请记录以下账号信息,填入 /root/service/cnotes/.env ----"
  echo "DB_NAME=${DB_NAME}"
  echo "DB_USER=${DB_USER}"
  echo "DB_PASSWORD=${DB_PASSWORD}"
fi
