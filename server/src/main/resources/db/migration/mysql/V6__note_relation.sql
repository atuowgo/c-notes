-- V6:批注↔批注关联(note relations)——想法之间的"呼应/对立/延伸/同主题"连接。
CREATE TABLE note_relation (
    id            CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    from_note_id  CHAR(32)     NOT NULL COMMENT '源批注',
    to_note_id    CHAR(32)     NOT NULL COMMENT '目标批注',
    relation_type VARCHAR(32)  NOT NULL COMMENT '呼应/对立/延伸/同主题/同篇',
    reason        VARCHAR(512)          DEFAULT NULL COMMENT '一句话理由',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nrel_from_to (from_note_id, to_note_id),
    KEY idx_nrel_from (from_note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批注关联';
