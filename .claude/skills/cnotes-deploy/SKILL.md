---
name: cnotes-deploy
description: c-notes 生产部署/更新/回滚/运维 —— 交互式菜单，覆盖首次部署到日志清理的 8 个已验证场景，针对 aliyun 生产机 /root/service/cnotes。
---

# c-notes 生产部署 Skill

本 skill 把已经在真实生产机上逐步验证过的部署流程固化下来，供任何一次全新会话（无任何历史记忆）也能照做不出错。

## 固定常量（不要每次询问用户，直接使用）

- SSH 别名：`aliyun`（用户已配置好，`ssh aliyun` / `scp file aliyun:/path` / `rsync ... aliyun:/path` 均可直接用）
- 远程部署目录：`/root/service/cnotes` —— 解压后的物料包目录和线上运行文件（jar、`.env`、`logs/`、`data/`）共用同一个路径，`PKG_ROOT` 和 `INSTALL_DIR` 是同一处
- 前端静态目录（nginx web root）：`/var/www/cnotes/dist`
- nginx 当前监听端口：`38088`（因为云厂商安全组默认没开 80 端口才选用；如需改回 80 或换其它端口，先跟操作者确认安全组是否已放行）
- 公网 IP（连通性检查用）：`120.77.79.151`
- 服务进程以 **root** 运行（非独立低权账号）——因为部署路径在 `/root` 下，普通账号无法遍历进去
- 远程上传暂存路径：`/root/docs/upload/`

> **本 skill 是当前唯一在用的部署方式**。仓库里另有 `ops/prod-deploy.sh` + `docs/ops/prod-deploy.md`，那是一套更早期的、直接从开发机 SSH push 到 `/opt/cnotes`（`User=cnotes`）的部署流程，目录布局和本 skill 完全不同，已不再实际使用/验证，不要混用或参照那一套。

## 使用方式：先呈现菜单，再执行

**本 skill 被调用时，第一步永远是把下面 8 个菜单项列给用户看，问清楚要执行哪一项，绝不能自作主张地连续执行多项或“全部做完”。** 用户选定后，只执行对应的那一节命令序列。

```
1. 首次部署 (fresh deploy)
2. 更新版本 (app 代码改动，需要重新编译打包)
3. 脚本更新 (只改了 ops/ 脚本或配置，不需要重新编译)
4. 改端口/域名 (change port or domain)
5. 数据库密码轮换 (DB password rotation)
6. 回滚到上一版 (rollback)
7. 日志清理 (log cleanup)
8. 只看日志/状态 (read-only status check)
```

---

## 1. 首次部署 (fresh deploy)

1. 本地打包：
   ```bash
   ./ops/scripts/package.sh
   ```
   产出 `release/cnotes-release-<时间戳>.tar.gz`。

2. 上传到远程暂存目录：
   ```bash
   scp release/cnotes-release-*.tar.gz aliyun:/root/docs/upload/
   ```

3. 远程解压到部署目录（把实际文件名替换进去）：
   ```bash
   ssh aliyun "mkdir -p /root/service/cnotes && tar xzf /root/docs/upload/<实际文件名>.tar.gz -C /root/service/cnotes --strip-components=1"
   ```

4. **已知坑**：如果 tarball 是在 macOS 上打包的，Linux 远程机上的 GNU tar 解压时会警告并生成 AppleDouble 的 `._*` 伴生文件。清理掉：
   ```bash
   ssh aliyun "cd /root/service/cnotes && find . -name '._*' -delete"
   ```

5. 保险起见确认脚本可执行位（rsync/tar 理论上会保留执行位，但显式确认一次）：
   ```bash
   ssh aliyun "chmod +x /root/service/cnotes/scripts/*.sh"
   ```

6. 执行安装脚本（首次运行会建库、生成 `.env`，装 systemd + nginx）：
   ```bash
   ssh aliyun "cd /root/service/cnotes && ./scripts/install.sh"
   ```

7. 生成 JWT_SECRET 并直接注入 —— 这只是一个随机值，不是需要问用户要的凭证，自己生成即可，不要等用户提供：
   ```bash
   ssh aliyun "cd /root/service/cnotes && JWT=\$(openssl rand -hex 32) && sed -i \"s#^JWT_SECRET=.*#JWT_SECRET=\$JWT#\" .env"
   ```

8. 告知用户需要把 `DEEPSEEK_API_KEY` 和 `ARK_API_KEY` 填进 `/root/service/cnotes/.env` —— 这两项是用户自己的 API key，**绝不能编造或猜测**。等用户确认已手工填好，或者用户直接在对话里把值给你，你再用类似上面第 7 步的定向 `sed` 帮他们填入；无论哪种方式，都不要编造占位值。

9. 启动服务：
   ```bash
   ssh aliyun "cd /root/service/cnotes && ./scripts/service.sh start"
   ```

10. **完整冒烟测试**（仅首次部署场景需要 —— 用来证明包括刚填入的 LLM/embedding key 在内的全链路真的跑通）：
    - 注册一个测试用户（在 `ssh aliyun` 里对 localhost 跑，或者从外部对 `http://120.77.79.151:38088/api/auth/register` 跑，两者等效，因为 nginx 代理了 `/api/`）：
      ```bash
      ssh aliyun "curl -sS -X POST http://127.0.0.1:38088/api/auth/register -H 'Content-Type: application/json' -d '{\"username\":\"<唯一用户名>\",\"password\":\"<任意6位以上>\"}'"
      ```
      从返回中提取 `token`。
    - 提交一篇带真实文本内容的文章（跳过 URL 抓取，只测 AI 处理链路，不测爬虫）：
      ```
      POST /api/collect
      Authorization: Bearer <token>
      Body: {"url":"...","title":"...","sourceType":"browser","content":"<几句真实文本内容>"}
      ```
      从返回中提取文章 `id`。
    - 每隔约 5 秒轮询一次，最多轮询约 60 秒，直到 `status` 变成 `done`（或 `failed`）：
      ```
      GET /api/articles/<id>
      Authorization: Bearer <token>
      ```
    - 确认返回的 `summary` 和 `keyPoints` 确实和提交的内容相关（不是空的/占位符）—— 这证明 `DEEPSEEK_API_KEY` 真的生效。这段时间内 `journalctl -u cnotes` 里没有出现 embedding 相关报错，是 `ARK_API_KEY` 生效的实际信号（没有单一字段能直接证明某篇文章的 embedding 调用成功，只能查日志里有没有 `ark`/`embed` 相关报错）：
      ```bash
      ssh aliyun "journalctl -u cnotes --since '5 minutes ago' --no-pager | grep -iE 'ark|embed'"
      ```
    - **测试完必须清理测试数据** —— 这不是可选项，不能在生产库里留下测试用户/文章：
      ```bash
      ssh aliyun "mariadb -N -e \"SELECT id FROM cnotes.user WHERE username='<测试用户名>';\""
      # 拿到 id 后：
      ssh aliyun "mariadb -e \"DELETE FROM cnotes.article WHERE owner_id='<id>'; DELETE FROM cnotes.user WHERE id='<id>';\""
      ```

11. 最终连通性检查：
    ```bash
    curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
    ```
    期望 `http_code=200`。

---

## 2. 更新版本 (app 代码改动，需要重新编译打包)

和首次部署的第 1-6 步完全一样（打包、上传、解压+清理 AppleDouble、chmod、执行 install.sh）：

```bash
./ops/scripts/package.sh
scp release/cnotes-release-*.tar.gz aliyun:/root/docs/upload/
ssh aliyun "mkdir -p /root/service/cnotes && tar xzf /root/docs/upload/<实际文件名>.tar.gz -C /root/service/cnotes --strip-components=1"
ssh aliyun "cd /root/service/cnotes && find . -name '._*' -delete"
ssh aliyun "chmod +x /root/service/cnotes/scripts/*.sh"
ssh aliyun "cd /root/service/cnotes && ./scripts/install.sh"
```

`install.sh` 会自动检测到 `.env` 已存在，走更新流程：跳过建库、不碰 `.env`，自动把当前 jar/dist 备份成 `.bak`，重新部署，结尾自动 `systemctl restart cnotes`。不需要再做 JWT_SECRET/API key 相关步骤（首次部署时已经配置好）。

跳过完整冒烟测试，改做**轻量验证**：
```bash
ssh aliyun "systemctl is-active cnotes"
# 期望: active

curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
# 期望: http_code=200

ssh aliyun "journalctl -u cnotes --since '2 minutes ago' --no-pager | grep -iE 'error|exception'"
# 期望: 空输出(没有新增报错)
```

---

## 3. 脚本更新 (只改了 ops/ 脚本或配置，不需要重新编译)

跳过 package.sh/上传环节，直接把小体积的 ops/ 子目录同步过去：

```bash
rsync -avz ops/scripts/ aliyun:/root/service/cnotes/scripts/
rsync -avz ops/systemd/ aliyun:/root/service/cnotes/systemd/
rsync -avz ops/nginx/ aliyun:/root/service/cnotes/nginx/
rsync -avz ops/env/ aliyun:/root/service/cnotes/env/
rsync -avz ops/sql/ aliyun:/root/service/cnotes/sql/
ssh aliyun "chmod +x /root/service/cnotes/scripts/*.sh"
ssh aliyun "cd /root/service/cnotes && ./scripts/install.sh"
```

说明：`install.sh` 会把没变动的 `backend/cnotes.jar`/`frontend/dist/` 重新拷贝到自己身上（纯本地磁盘操作，无害，不需要为此重新上传）。`LISTEN_PORT`/`SERVER_NAME` 现在会自动从现有 nginx 配置里持久化读取（未显式传值时），所以这里**不需要**记得重新传端口/域名参数 —— 这正是为了让这个场景安全而修复的。

**如果最近做过回滚（菜单项 6），跑本项之前先看一下菜单项 6 的警告——这里会把物料包里的 jar/dist 重新部署一遍，可能悄悄撤销刚才的回滚。**

轻量验证同菜单项 2：
```bash
ssh aliyun "systemctl is-active cnotes"
curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
ssh aliyun "journalctl -u cnotes --since '2 minutes ago' --no-pager | grep -iE 'error|exception'"
```

---

## 4. 改端口/域名 (change port or domain)

```bash
ssh aliyun "cd /root/service/cnotes && LISTEN_PORT=<新端口> ./scripts/install.sh"
# 或改域名:
ssh aliyun "cd /root/service/cnotes && SERVER_NAME=<域名> ./scripts/install.sh"
```

提醒用户：如果改的是端口，必须在云控制台的安全组里为新端口添加入站规则 —— 这一步不能通过 SSH 完成，是云网络层面的设置，在操作系统之外。端口改变之后，后续验证要相应更新连通性检查用的 URL。

**如果最近做过回滚（菜单项 6），跑本项之前先看一下菜单项 6 的警告——这里会把物料包里的 jar/dist 重新部署一遍，可能悄悄撤销刚才的回滚。**

轻量验证（用**新**端口/URL 去 curl）：
```bash
curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:<新端口>/
ssh aliyun "systemctl is-active cnotes"
ssh aliyun "journalctl -u cnotes --since '2 minutes ago' --no-pager | grep -iE 'error|exception'"
```

---

## 5. 数据库密码轮换 (DB password rotation)

```bash
ssh aliyun "cd /root/service/cnotes && DB_PASSWORD=<新密码> ./scripts/install-db.sh"
```

这个可以安全地单独重跑，**不会**自动改动 `.env`。跑完之后，提醒用户（或者如果用户已经把新密码给你了，就自己动手）手工把 `/root/service/cnotes/.env` 里的 `SPRING_DATASOURCE_PASSWORD` 改成新密码，然后：

```bash
ssh aliyun "cd /root/service/cnotes && ./scripts/service.sh restart"
```

轻量验证：
```bash
ssh aliyun "systemctl is-active cnotes"
curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
ssh aliyun "journalctl -u cnotes --since '2 minutes ago' --no-pager | grep -iE 'error|exception'"
```

---

## 6. 回滚到上一版 (rollback)

```bash
ssh aliyun "cd /root/service/cnotes && ./scripts/rollback.sh"
```

提醒用户：这只在此前至少做过一次"更新版本"部署时才有效（没有 `.bak` 存在时 `rollback.sh` 会明确报错退出）。再跑一次这个命令，等于把刚换下去的版本换回来（撤销刚才的回滚）。

**重要提醒**：回滚之后，如果紧接着执行菜单项 3（脚本更新）或菜单项 4（改端口/域名），`install.sh` 仍然会无条件把物料包目录里的 `backend/cnotes.jar`/`frontend/dist/` 重新部署到线上路径——这会静默撤销刚才的回滚（把版本又换回回滚前的那个），并且会把 `.bak` 覆盖掉，导致刚才回滚出来的版本也丢了备份。**回滚之后，在做任何新的真实版本发布（菜单项 2）之前，先避免执行菜单项 3/4；如果确实需要做端口/域名调整，跑完之后记得再检查一下 `systemctl status cnotes` 里报告的实际运行版本是否符合预期。**

轻量验证：
```bash
ssh aliyun "systemctl is-active cnotes"
curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
ssh aliyun "journalctl -u cnotes --since '2 minutes ago' --no-pager | grep -iE 'error|exception'"
```

---

## 7. 日志清理 (log cleanup)

```bash
ssh aliyun "cd /root/service/cnotes && ./scripts/cleanup-logs.sh [KEEP_DAYS]"
```

`KEEP_DAYS` 不传默认 14 天。会清理 `/root/service/cnotes/logs` 和 `/var/log/nginx` 下超过保留天数的历史归档文件（`.gz` 或 `.log.数字` 后缀），按设计绝不会碰当前活跃日志文件（`cnotes.log`/`cnotes-error.log`/`access.log`/`error.log` 这些不带日期/序号后缀的文件名天然不会被匹配到）。

不需要额外验证 —— 脚本本身会打印删除了哪些文件、释放了多少空间，看这个输出即可。

---

## 8. 只看日志/状态 (read-only status check)

```bash
ssh aliyun "systemctl status cnotes --no-pager -l"
ssh aliyun "tail -n 50 /root/service/cnotes/logs/cnotes.log"
ssh aliyun "journalctl -u cnotes -n 50 --no-pager"
curl -sS -m 8 -o /dev/null -w 'http_code=%{http_code}\n' http://120.77.79.151:38088/
```

这一项从不改动任何东西 —— 排查问题、或者调试本 skill 本身时最安全的选项。

---

## 收尾注意事项 / 常见坑

- **AppleDouble `._*` 文件**：本地在 macOS 上打包的 tarball，在 Linux 远程机用 GNU tar 解压会生成 `._*` 伴生文件并警告。只影响菜单项 1/2 的解压步骤，用 `find . -name '._*' -delete` 清掉即可，无害但建议清理干净。
- **绝不编造 API key**：`DEEPSEEK_API_KEY` 和 `ARK_API_KEY` 是用户自己申请的凭证，永远不要生成占位值或猜测；必须等用户确认填好，或用户直接把值给你再帮忙写入。
- **JWT_SECRET 可以安全地自动生成**：这只是一个随机字符串（`openssl rand -hex 32`），不是需要问用户要的外部凭证，直接生成并注入即可，不用等待用户确认。
- **完整冒烟测试 vs 轻量验证**：只有菜单项 1（首次部署）需要跑注册用户 → 提交文章 → 轮询状态 → 校验 AI 输出 → 清理测试数据 的完整链路，因为这是唯一一次证明 LLM/embedding key 真正生效的机会。菜单项 2/3/4/5/6 只需要轻量验证（`systemctl is-active` + curl 200 + 日志无新增报错），不需要也不应该反复注册测试用户脏化生产库。菜单项 7 用脚本自己的输出确认即可，菜单项 8 本身就是只读检查。
- `ops/README-DEPLOY.md` 是打包进 tarball 里、给人工阅读的简版部署指南，和本 SKILL.md 描述的流程应保持一致；但本文件更详细、以此为准。
