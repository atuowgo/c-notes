-- V4:多用户鉴权(A1)——用户表 + 现有业务表加 owner_id 隔离列。
-- `user` 在 H2 为保留字,统一用反引号引用(MySQL 原生 / H2 MySQL 模式均支持),实体 @TableName 同步反引号。
CREATE TABLE `user` (
    id            CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt 哈希',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 现有表加 owner_id:按当前用户过滤数据。可空(兼容历史/wechat 无主数据 + DevDataSeeder 回填)。
ALTER TABLE article      ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '所有者用户 id' AFTER id;
CREATE INDEX idx_article_owner ON article(owner_id);

ALTER TABLE note         ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '所有者用户 id';
CREATE INDEX idx_note_owner ON note(owner_id);

ALTER TABLE chat_session ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '所有者用户 id';
CREATE INDEX idx_chat_session_owner ON chat_session(owner_id);
