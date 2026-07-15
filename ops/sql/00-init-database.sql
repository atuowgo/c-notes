-- ops/sql/00-init-database.sql
-- 建库 + 建应用专用账号(MariaDB 11 / MySQL 8 通用,无版本特有语法)。
-- 表结构不在此建:后端首次启动时由 Flyway 自动迁移(db/migration/mysql/V1..V8)。
-- 本文件含占位符,不要直接手工执行——由 ops/scripts/install-db.sh 替换后运行。
--   __DB_NAME__      数据库名(默认 cnotes)
--   __DB_USER__      应用专用账号(默认 cnotes,不复用 root)
--   __DB_PASSWORD__  应用账号密码(install-db.sh 未指定时自动生成)

CREATE DATABASE IF NOT EXISTS `__DB_NAME__`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- 仅授权本机连接(后端与数据库同机部署),不开放远程账号。
-- CREATE USER IF NOT EXISTS 在账号已存在时整条(含 IDENTIFIED BY)静默跳过,不会改密码;
-- 紧跟的 ALTER USER IF EXISTS 保证密码总是被同步到本次传入值,使本脚本可重复执行做密码轮换。
CREATE USER IF NOT EXISTS '__DB_USER__'@'localhost' IDENTIFIED BY '__DB_PASSWORD__';
ALTER USER IF EXISTS '__DB_USER__'@'localhost' IDENTIFIED BY '__DB_PASSWORD__';
GRANT ALL PRIVILEGES ON `__DB_NAME__`.* TO '__DB_USER__'@'localhost';
FLUSH PRIVILEGES;
