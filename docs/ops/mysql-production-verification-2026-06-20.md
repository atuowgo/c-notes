# 生产 MySQL 部署验证(真实 MySQL 8.0)

> 日期:2026-06-20
> 结论:**默认 profile(MySQL)下 Flyway 全量迁移 V1~V6 在真实 MySQL 8.0 上干净应用,
> 应用正常启动,全链路(收集→worker→DeepSeek→done)读写与中文 utf8mb4 往返均正确。**

此前仅在 H2(MODE=MySQL)dev 档验证过;本次用 Docker 起真实 MySQL 8.0 跑通生产形态。

## 环境
- MySQL 8.0.46(Docker `mysql:8.0`),库 `cnotes`,root/root,3306 —— 与 `application.yml` 默认数据源一致。
- 应用默认 profile(非 dev):`./gradlew bootRun --args='--worker.scheduling.enabled=true'`,读 `server/.env` 的真实 DeepSeek/Ark 密钥。

## 验证结果
- **Flyway**:`Successfully applied 6 migrations to schema cnotes, now at version v6`(执行 0.582s)。
  `flyway_schema_history` 6 条 `success=1`,与迁移文件数 6 一一对应。
- **建表**:11 张表全部以 `utf8mb4_0900_ai_ci` 创建 ——
  article / article_relation / article_tag / chat_message / chat_session / note / note_relation /
  tag / tag_merge / tag_suggestion / flyway_schema_history。
- **启动**:`Started CNotesApplication in 5.769 seconds`,Tomcat 8080。
- **全链路冒烟**:`POST /api/collect`(中文标题「MySQL 验证」)→ MySQL 落库 → worker 取出 →
  真实 DeepSeek 处理 → `status=done`;`GET /api/articles` 读回 `title='MySQL 验证'` 正确;
  MySQL 内 `HEX(title)` 解码为「MySQL 验证」,确认 utf8mb4 中文无损往返。

## 复现
```bash
docker run -d --name cnotes-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=cnotes \
  -p 3306:3306 mysql:8.0
# 待 mysqladmin ping 通后:
cd server && set -a && . ./.env && set +a
./gradlew bootRun --args='--worker.scheduling.enabled=true'   # 默认 profile = MySQL
# 校验:docker exec cnotes-mysql mysql -uroot -proot cnotes \
#   -e "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;"
```

## 诚实边界
- 无前端 UI 变更,故无截图;验证以 Flyway 日志 + schema_history + 真实库 schema/数据为准。
- 向量库仍用本地文件型 SimpleVectorStore(生产可换 pgvector/外部库,见 local-build-run.md §7)。
