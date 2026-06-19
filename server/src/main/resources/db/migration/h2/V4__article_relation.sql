-- V4:文章↔文章 关联（relations）——「为什么相关」连边,阅读页展示。
CREATE TABLE article_relation (
    id              CHAR(32)     NOT NULL,
    from_article_id CHAR(32)     NOT NULL,
    to_article_id   CHAR(32)     NOT NULL,
    relation_type   VARCHAR(32)  NOT NULL,
    reason          VARCHAR(512)          DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arel_from_to (from_article_id, to_article_id),
    KEY idx_arel_from (from_article_id)
);
