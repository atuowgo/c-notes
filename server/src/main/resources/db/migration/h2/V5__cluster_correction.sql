-- V5:知识网纠偏——article_tag 来源标记、tag 归档、簇合并重定向表。
ALTER TABLE article_tag ADD COLUMN source VARCHAR(16) DEFAULT 'ai';   -- 'ai' | 'user'(用户钉选)
ALTER TABLE tag ADD COLUMN archived BOOLEAN DEFAULT FALSE;            -- 合并后的源簇归档,列表隐藏

CREATE TABLE tag_merge (
    id          CHAR(32) NOT NULL,
    from_tag_id CHAR(32) NOT NULL,
    to_tag_id   CHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tagmerge_from (from_tag_id)
);
