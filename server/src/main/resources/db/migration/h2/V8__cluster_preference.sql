-- V8:簇纠偏审计(cluster_preference)——H2 等价语法。语义见 mysql/V8__cluster_preference.sql。
CREATE TABLE cluster_preference (
    id          CHAR(32)     NOT NULL,
    owner_id    CHAR(32)              DEFAULT NULL,
    action      VARCHAR(16)  NOT NULL,
    source_id   CHAR(32)              DEFAULT NULL,
    target_id   CHAR(32)              DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_cp_owner ON cluster_preference(owner_id);
CREATE INDEX idx_cp_action ON cluster_preference(action);
