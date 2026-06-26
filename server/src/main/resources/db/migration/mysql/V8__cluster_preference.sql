-- V8:簇纠偏审计(cluster_preference)——记录用户对标签簇的 merge/split/move 操作。
-- 仅审计(谁在何时把哪个源簇导向哪个目标簇),不驱动业务逻辑;action 取值 merge/split/move。
--   merge :source 簇全部并入 target 簇,删 source;source_id=源簇,target_id=目标簇。
--   split :从源簇拆出若干文章到新建簇;source_id=源簇,target_id=新建簇。
--   move  :单篇从源簇移到目标簇;source_id=源簇,target_id=目标簇。
-- owner_id 隔离(A1):记录操作者;可空(测试 permitAll / 历史无主数据)。
-- 仅 create_time:纠偏为事件快照,无 update 语义。
CREATE TABLE cluster_preference (
    id          CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    owner_id    CHAR(32)              DEFAULT NULL COMMENT '操作者用户 id',
    action      VARCHAR(16)  NOT NULL COMMENT 'merge/split/move',
    source_id   CHAR(32)              DEFAULT NULL COMMENT '源簇(标签)id',
    target_id   CHAR(32)              DEFAULT NULL COMMENT '目标簇(标签)id;split 为新建簇',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cp_owner (owner_id),
    KEY idx_cp_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='簇纠偏审计';
