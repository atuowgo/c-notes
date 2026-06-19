-- V5:知识网纠偏——article_tag 来源标记、tag 归档、簇合并重定向表。
ALTER TABLE article_tag ADD COLUMN source VARCHAR(16) DEFAULT 'ai' COMMENT '链接来源:ai 自动归类 / user 用户钉选';
ALTER TABLE tag ADD COLUMN archived TINYINT(1) DEFAULT 0 COMMENT '合并后的源簇归档(1=已归档),列表隐藏';

CREATE TABLE tag_merge (
    id          CHAR(32) NOT NULL COMMENT '32位UUID hex',
    from_tag_id CHAR(32) NOT NULL COMMENT '被合并的源簇',
    to_tag_id   CHAR(32) NOT NULL COMMENT '合并目标簇',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tagmerge_from (from_tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='簇合并重定向';
