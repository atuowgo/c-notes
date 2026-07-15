# 生产部署手册(c-notes 知识炼金炉)

> 目的:在 Linux 生产机上部署后端 jar + 前端 dist + MySQL + nginx 同源反代,启动并验证。
> 与本地 dev 的差异:数据库切 MySQL(非 H2)、前端用 nginx 托管 dist(非 Vite dev)、后端以 systemd 常驻、密钥走环境变量。

## 1. 产物准备(在构建机一次性产出)

```bash
# 仓库根
make build
# Windows / 无 make:
server/gradlew.bat build
pnpm -C frontend install && pnpm -C frontend -r build
```

产物:
- 后端可执行 jar:`server/build/libs/cnotes.jar`(Spring Boot fat jar,含内嵌 Tomcat;文件名已在 `build.gradle` 固定,不随版本号变化)
- 前端静态:`frontend/apps/web/dist/`(index.html + assets)

> 也可在生产机上 `git pull && make build` 就地构建(需生产机有 JDK21 + pnpm)。
>
> **另一种部署方式(离线上传包)**:若生产机不便让本机 SSH 直连(如本文档 §1-§9 的 push 流程),
> 可用 `ops/scripts/package.sh` 在本地编译并打包成一个自包含的 tar.gz(含 jar、dist、SQL 建库脚本、
> systemd 单元、nginx 配置、install.sh/service.sh),手工上传解压后 `sudo ./scripts/install.sh` 一键接线。
> 详见 `ops/README-DEPLOY.md`。数据库为 MariaDB(而非 MySQL)时同样适用 —— 迁移 SQL 与建库脚本均为
> 可移植标准语法,已验证兼容 MariaDB 11。

## 2. 生产机前置

- JDK 21(`java -version`)
- MySQL 8(建库 `cnotes` utf8mb4;`application.yml` 默认 `jdbc:mysql://localhost:3306/cnotes` user/pass `root/root`,生产改强密码)
- nginx(`apt install nginx`)
- 系统 Chrome(仅当生产要跑 HeadlessRenderer 动态抓取兜底;默认 `HEADLESS_ENABLED=false` 不需要)

## 3. 目录与上传

```
/opt/cnotes/
  ├─ cnotes.jar                 # 上传的 fat jar
  ├─ .env                       # 密钥( chmod 600,不入库)
  └─ data/                      # 运行期:对象存储正文(STORAGE_ROOT)
/var/www/cnotes/dist/           # 前端 dist 全量上传
/etc/nginx/conf.d/cnotes.conf   # nginx 站点配置
/etc/systemd/system/cnotes.service
```

## 4. 环境变量(/opt/cnotes/.env,systemd 读入)

```
DEEPSEEK_API_KEY=sk-...
LLM_MODEL=deepseek-chat
ARK_API_KEY=...
ARK_EMBEDDING_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
ARK_EMBEDDING_MODEL=ep-20260617000458-2mslf
ARK_EMBEDDING_DIM=2048
JWT_SECRET=<随机长串>           # A1:JWT 签名密钥,生产必填
JWT_EXPIRY=PT1h
STORAGE_ROOT=/opt/cnotes/data   # A2:本地文件对象存储根目录
WORKER_SCHEDULING_ENABLED=true  # ArticleWorker + ClusterSummaryWorker + AutoClusterWorker
HEADLESS_ENABLED=false
# 微信(可选,接公众号后):
WECHAT_TOKEN=...
```

> `server/.env` 的同名变量在本地用;生产用 systemd `EnvironmentFile=/opt/cnotes/.env`。

## 5. nginx 同源反代(/etc/nginx/conf.d/cnotes.conf)

```nginx
server {
    listen 80;                       # 有证书改 443 + ssl_certificate
    server_name cnotes.example.com;
    root /var/www/cnotes/dist;       # 前端静态
    index index.html;

    location /api/ {                 # 反代后端
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    location / { try_files $uri /index.html; }   # SPA 回退
}
```

校验:`nginx -t` → `systemctl reload nginx`。模板可参照仓内 `ops/nginx/cnotes.dev.conf`(8088 同源 dev 版)。

## 6. systemd 常驻(/etc/systemd/system/cnotes.service)

```ini
[Unit]
Description=cnotes backend
After=network.target mysql.service

[Service]
WorkingDirectory=/opt/cnotes
EnvironmentFile=/opt/cnotes/.env
ExecStart=/usr/bin/java -jar /opt/cnotes/cnotes.jar
SuccessExitStatus=143
Restart=on-failure
User=cnotes

[Install]
WantedBy=multi-user.target
```

启动:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now cnotes
sudo journalctl -u cnotes -f      # 看 "Started CNotesApplication"
```

> 默认 profile 走 `application.yml` 的 MySQL + Flyway `db/migration/mysql/*`(V1–V8 建表/owner_id/auto_cluster/article_link/cluster_preference 等自动迁移)。`worker.scheduling.enabled` 由 env `WORKER_SCHEDULING_ENABLED` 控(dev 默认开;生产按需)。

## 7. 部署后验证

```bash
# 健康检查(放行端点)
curl -i http://127.0.0.1:8080/actuator/health      # 或任一公开端点
# 浏览器 https://cnotes.example.com → /login
# 注册/登录(首个用户)→ 收件箱;收一篇文章 → 整理/深聊/语义簇/关联推荐(同本地验证清单)
```

安全确认:
- `/api/auth/**`、`/wechat/callback`、健康端点 permitAll;其余需 JWT(A1 SecurityConfig)
- 未登录访问受保护端点返回 401
- 长正文落 `/opt/cnotes/data/`(A2),article.content 存引用
- 密钥仅 env,不入仓不入 jar

## 8. 更新流程

```bash
# 构建机:make build → 上传新 jar + dist
sudo systemctl stop cnotes
sudo cp /path/cnotes.jar /opt/cnotes/cnotes.jar
sudo rsync -a /path/dist/ /var/www/cnotes/dist/
sudo systemctl start cnotes
sudo journalctl -u cnotes -f --since "1 min ago"
# Flyway 自动增量迁移(V 已应用的跳过;新增 V 执行)
```

## 9. 回滚

```bash
sudo systemctl stop cnotes
sudo cp /opt/cnotes/cnotes.jar.bak /opt/cnotes/cnotes.jar   # 保留上一版 jar
sudo systemctl start cnotes
```
数据库迁移如需回退,Flyway 不自动 undo;保留旧 jar 对应的迁移版本即可(新表为新增,旧 jar 不读写新表,兼容)。

## 10. 常见问题

- 后端起不来 → `journalctl -u cnotes` 看栈;多为 env 缺 `JWT_SECRET`/`DEEPSEEK_API_KEY` 或 MySQL 不可达。
- 404 刷新 → nginx 缺 `try_files ... /index.html`。
- Ark 400 → 用了纯文本 `/embeddings`(应为 `/embeddings/multimodal`,代码已正确;检查 `ARK_EMBEDDING_MODEL` 是否被改)。
- 微信 401 → `WECHAT_TOKEN` 与公众号后台不一致。
- 语义簇迟迟不出现 → `WORKER_SCHEDULING_ENABLED` 未开,或同主题文章 <2 篇(阈值 0.75)。
