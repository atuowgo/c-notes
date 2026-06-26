-- V7:文章关联推荐(article_link)——H2 等价语法。语义见 mysql/V7__article_link.sql。
CREATE TABLE article_link (
    id                CHAR(32)     NOT NULL,
    owner_id          CHAR(32)              DEFAULT NULL,
    article_id        CHAR(32)     NOT NULL,
    target_article_id CHAR(32)     NOT NULL,
    link_type         VARCHAR(16)  NOT NULL DEFAULT '相关',
    reason            VARCHAR(255)          DEFAULT NULL,
    score             DOUBLE                DEFAULT NULL,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_al_article ON article_link(article_id);
CREATE INDEX idx_al_owner ON article_link(owner_id);
