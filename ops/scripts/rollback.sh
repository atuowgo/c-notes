#!/usr/bin/env bash
# ops/scripts/rollback.sh —— 把 cnotes.jar / 前端 dist 与各自的 .bak 互换,回滚到上一版本。
# 仅支持 1 层历史(install.sh 每次"更新"部署前会自动把当前版本备份成 .bak,见 install.sh)。
# 用的是交换而不是单向覆盖:再跑一次本脚本,等于把刚换下去的版本换回来,两版之间来回切换,
# 不需要另外实现"回滚的回滚"。
# 注意:临时文件名固定(.cnotes.jar.swap-tmp / ${WEB_DIR}.swap-tmp),若脚本在两次 mv 之间被中断,
# 现场遗留的这个临时文件就是崩溃时"活着"的那一版;若不看内容就盲目重跑,开头的 rm 会直接把它冲掉,
# 导致这一版无法恢复(.bak 里的另一版仍在,不会导致彻底没有可用 jar/dist,但会丢失崩溃时那个具体版本)。
# 用法(生产机,root/sudo):
#   sudo ./scripts/rollback.sh
set -euo pipefail

trap 'echo "遇到错误,尝试确保服务仍在运行(可能是切换前或切换后的版本,视失败发生的时点而定)..." >&2
      systemctl start cnotes >/dev/null 2>&1 || echo "  自动重启也失败,请手工检查: systemctl status cnotes,以及 $INSTALL_DIR 下的 .bak / .swap-tmp 文件" >&2' ERR

[ "$(id -u)" -eq 0 ] || { echo "请用 sudo/root 运行本脚本" >&2; exit 1; }

INSTALL_DIR=/root/service/cnotes
WEB_DIR=/var/www/cnotes/dist

JAR="$INSTALL_DIR/cnotes.jar"
JAR_BAK="$INSTALL_DIR/cnotes.jar.bak"
DIST_BAK="${WEB_DIR}.bak"

[ -f "$JAR_BAK" ] || { echo "没有可回滚的备份($JAR_BAK 不存在),可能还没做过一次更新部署" >&2; exit 1; }
[ -d "$DIST_BAK" ] || { echo "没有可回滚的前端备份($DIST_BAK 不存在)" >&2; exit 1; }

step(){ printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }

step "停止服务"
systemctl stop cnotes

step "交换 jar"
TMP_JAR="$INSTALL_DIR/.cnotes.jar.swap-tmp"
rm -f "$TMP_JAR"
mv "$JAR" "$TMP_JAR"
mv "$JAR_BAK" "$JAR"
mv "$TMP_JAR" "$JAR_BAK"

step "交换前端 dist"
TMP_DIST="${WEB_DIR}.swap-tmp"
rm -rf "$TMP_DIST"
mv "$WEB_DIR" "$TMP_DIST"
mv "$DIST_BAK" "$WEB_DIR"
mv "$TMP_DIST" "$DIST_BAK"

step "重启服务"
systemctl start cnotes
sleep 3
systemctl --no-pager -l status cnotes | head -15 || true

echo
echo "已切换到备份版本(再跑一次 rollback.sh 可以切回来)。"
