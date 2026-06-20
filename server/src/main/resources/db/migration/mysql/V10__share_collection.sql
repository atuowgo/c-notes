-- 多用户阶段 2:分享级别 + 收藏 + 收录(链接引用,不深拷贝)。
ALTER TABLE article ADD COLUMN share_level VARCHAR(20) DEFAULT NULL
    COMMENT '逐篇覆盖账号默认;NULL=继承 owner.default_share_level';

CREATE TABLE bookmark (
    id          CHAR(32) NOT NULL COMMENT '32位UUID hex',
    user_id     CHAR(32) NOT NULL COMMENT '收藏者 app_user.id',
    article_id  CHAR(32) NOT NULL COMMENT '被收藏文章 article.id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookmark (user_id, article_id),
    KEY idx_bookmark_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏(轻量阅读列表,不进知识库)';

CREATE TABLE collection (
    id                CHAR(32)  NOT NULL COMMENT '32位UUID hex',
    user_id           CHAR(32)  NOT NULL COMMENT '收录者 app_user.id',
    source_article_id CHAR(32)  NOT NULL COMMENT '被收录的源文章 article.id',
    personal_note     TEXT               DEFAULT NULL COMMENT '收录时的个人笔记',
    create_time       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collection (user_id, source_article_id),
    KEY idx_collection_user (user_id),
    KEY idx_collection_source (source_article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收录(链接引用+本地笔记,不深拷贝)';
