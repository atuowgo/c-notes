-- V4:多用户鉴权(A1)——用户表 + 现有业务表加 owner_id 隔离列(H2 等价语法)。
-- `user` 在 H2 为保留字,用反引号引用(H2 MySQL 模式支持),与实体 @TableName 一致。
CREATE TABLE `user` (
    id            CHAR(32)     NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);

ALTER TABLE article      ADD COLUMN owner_id CHAR(32) DEFAULT NULL;
CREATE INDEX idx_article_owner ON article(owner_id);

ALTER TABLE note         ADD COLUMN owner_id CHAR(32) DEFAULT NULL;
CREATE INDEX idx_note_owner ON note(owner_id);

ALTER TABLE chat_session ADD COLUMN owner_id CHAR(32) DEFAULT NULL;
CREATE INDEX idx_chat_session_owner ON chat_session(owner_id);
