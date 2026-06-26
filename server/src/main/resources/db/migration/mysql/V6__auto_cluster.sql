-- V6:语义簇(自动聚类,复用 Ark embedding)——按 cosine 相似度凝聚层次聚类产出的簇及其成员。
-- 与 V3 的标签簇(chat_session/chat_message)不同:这里无人工标签,由向量相似度自动成簇。
-- owner_id 隔离(A1):每个用户独立聚类,查询按当前用户过滤;可空(兼容历史/无主数据)。
CREATE TABLE auto_cluster (
    id           CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    owner_id     CHAR(32)              DEFAULT NULL COMMENT '所有者用户 id',
    title        VARCHAR(255)          DEFAULT NULL COMMENT '簇标题(取 medoid 文章标题)',
    member_count INT          NOT NULL DEFAULT 0 COMMENT '成员文章数',
    summary      MEDIUMTEXT            DEFAULT NULL COMMENT 'AI 织的语义综述(可空)',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auto_cluster_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语义簇(自动聚类)';

CREATE TABLE auto_cluster_member (
    id          CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    cluster_id  CHAR(32)     NOT NULL COMMENT '所属语义簇 id',
    article_id  CHAR(32)     NOT NULL COMMENT '成员文章 id',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_acm_cluster (cluster_id),
    KEY idx_acm_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语义簇成员';
