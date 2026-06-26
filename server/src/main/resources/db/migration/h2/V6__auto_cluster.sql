-- V6:语义簇(自动聚类,复用 Ark embedding)——按 cosine 相似度凝聚层次聚类产出的簇及其成员(H2 等价语法)。
-- owner_id 隔离(A1):每个用户独立聚类;可空(兼容历史/无主数据)。
CREATE TABLE auto_cluster (
    id           CHAR(32)     NOT NULL,
    owner_id     CHAR(32)              DEFAULT NULL,
    title        VARCHAR(255)          DEFAULT NULL,
    member_count INT          NOT NULL DEFAULT 0,
    summary      TEXT                 DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_auto_cluster_owner ON auto_cluster(owner_id);

CREATE TABLE auto_cluster_member (
    id          CHAR(32)     NOT NULL,
    cluster_id  CHAR(32)     NOT NULL,
    article_id  CHAR(32)     NOT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_acm_cluster ON auto_cluster_member(cluster_id);
CREATE INDEX idx_acm_article ON auto_cluster_member(article_id);
