#!/usr/bin/env bash
# ops/scripts/package.sh —— 本地编译 + 打包生产物料包(开发机执行,产物手工上传到远程部署)。
# 用法: ./ops/scripts/package.sh
# 产物: release/cnotes-release-<时间戳>.tar.gz
#   backend/cnotes.jar、frontend/dist/、sql/、systemd/、nginx/、env/、scripts/(ops/scripts/*.sh 全部,
#   目前含 install.sh、install-db.sh、service.sh、rollback.sh、cleanup-logs.sh)、README.md ——
#   上传解压后 sudo ./scripts/install.sh 即可部署。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

step(){ printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }

step "编译后端(gradlew bootJar)"
( cd server && ./gradlew bootJar )
JAR="server/build/libs/cnotes.jar"
[ -f "$JAR" ] || { echo "未找到 $JAR,构建失败?" >&2; exit 1; }

step "编译前端(pnpm --filter @cnotes/web build)"
( cd frontend && pnpm install && pnpm --filter @cnotes/web build )
DIST="frontend/apps/web/dist"
[ -d "$DIST" ] || { echo "未找到 $DIST,构建失败?" >&2; exit 1; }

STAMP="$(date +%Y%m%d-%H%M%S)"
PKG_NAME="cnotes-release-${STAMP}"
WORK_DIR="$(mktemp -d)"
PKG_DIR="$WORK_DIR/$PKG_NAME"

step "组装物料包 $PKG_NAME"
mkdir -p "$PKG_DIR"/backend "$PKG_DIR"/frontend "$PKG_DIR"/sql "$PKG_DIR"/systemd "$PKG_DIR"/nginx "$PKG_DIR"/env "$PKG_DIR"/scripts
cp "$JAR" "$PKG_DIR/backend/cnotes.jar"
cp -r "$DIST" "$PKG_DIR/frontend/dist"
cp ops/sql/00-init-database.sql "$PKG_DIR/sql/"
cp ops/systemd/cnotes.service "$PKG_DIR/systemd/"
cp ops/nginx/cnotes.prod.conf "$PKG_DIR/nginx/"
cp ops/env/cnotes.env.example "$PKG_DIR/env/"
for f in ops/scripts/*.sh; do
  [ "$(basename "$f")" = "package.sh" ] && continue   # 本机构建脚本,不随包上生产机
  cp "$f" "$PKG_DIR/scripts/"
done
chmod +x "$PKG_DIR"/scripts/*.sh
cp ops/README-DEPLOY.md "$PKG_DIR/README.md"
echo "$STAMP" > "$PKG_DIR/VERSION"

mkdir -p release
TARBALL="release/${PKG_NAME}.tar.gz"
tar -C "$WORK_DIR" -czf "$TARBALL" "$PKG_NAME"
rm -rf "$WORK_DIR"

step "完成"
ls -lh "$TARBALL"
echo "上传后: tar xzf $(basename "$TARBALL") && cd ${PKG_NAME} && sudo ./scripts/install.sh"
