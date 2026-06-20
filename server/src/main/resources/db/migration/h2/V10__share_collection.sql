-- 多用户阶段 2:分享级别 + 收藏 + 收录(链接引用,不深拷贝)。
ALTER TABLE article ADD COLUMN share_level VARCHAR(20) DEFAULT NULL;

CREATE TABLE bookmark (
    id          CHAR(32) NOT NULL,
    user_id     CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookmark (user_id, article_id),
    KEY idx_bookmark_user (user_id)
);

CREATE TABLE collection (
    id                CHAR(32)  NOT NULL,
    user_id           CHAR(32)  NOT NULL,
    source_article_id CHAR(32)  NOT NULL,
    personal_note     TEXT               DEFAULT NULL,
    create_time       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collection (user_id, source_article_id),
    KEY idx_collection_user (user_id),
    KEY idx_collection_source (source_article_id)
);
