-- V7:文章关联推荐(article_link)——为给定文章算"相关"文章:候选=共享标签,
-- 用 Ark embedding cosine 相似度排序,top-N 入库;reason 由 ChatClient(DeepSeek)生成。
-- link_type 取值:相关/更深入/对立/互补(本批次算法只产"相关",余者预留)。
-- owner_id 隔离(A1):每个用户独立关联图,查询按当前用户过滤;可空(兼容历史/无主数据)。
-- 仅 create_time:关联为算后快照,无 update 语义(重算先删后插,不就地改)。
CREATE TABLE article_link (
    id                CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    owner_id          CHAR(32)              DEFAULT NULL COMMENT '所有者用户 id',
    article_id        CHAR(32)     NOT NULL COMMENT '源文章 id',
    target_article_id CHAR(32)     NOT NULL COMMENT '关联目标文章 id',
    link_type         VARCHAR(16)  NOT NULL DEFAULT '相关' COMMENT '相关/更深入/对立/互补',
    reason            VARCHAR(255)          DEFAULT NULL COMMENT 'AI 生成的为什么相关短句;无 key 时空串',
    score             DOUBLE                DEFAULT NULL COMMENT 'embedding cosine 相似度 0~1',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_al_article (article_id),
    KEY idx_al_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章关联推荐';
