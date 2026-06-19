-- V6:批注↔批注关联(note relations)——想法之间的"呼应/对立/延伸/同主题"连接。
CREATE TABLE note_relation (
    id            CHAR(32)     NOT NULL,
    from_note_id  CHAR(32)     NOT NULL,
    to_note_id    CHAR(32)     NOT NULL,
    relation_type VARCHAR(32)  NOT NULL,
    reason        VARCHAR(512)          DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nrel_from_to (from_note_id, to_note_id),
    KEY idx_nrel_from (from_note_id)
);
