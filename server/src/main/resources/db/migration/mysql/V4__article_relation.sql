-- V4:文章↔文章 关联（relations）——「为什么相关」连边,阅读页展示。
CREATE TABLE article_relation (
    id              CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    from_article_id CHAR(32)     NOT NULL COMMENT '来源文章',
    to_article_id   CHAR(32)     NOT NULL COMMENT '关联到的文章',
    relation_type   VARCHAR(32)  NOT NULL COMMENT '同概念/互补/对立/延伸/相关',
    reason          VARCHAR(512)          DEFAULT NULL COMMENT '一句话中文理由',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arel_from_to (from_article_id, to_article_id),
    KEY idx_arel_from (from_article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章关联';
