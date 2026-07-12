-- V9:沉浸式阅读——净化后正文 HTML 落盘;article 旁加 html_object_key 指向存储 key。
-- HTML 一律落盘(不进 content 列);取详情按 key 读回,空=无 HTML 走降级看原文。
ALTER TABLE article ADD COLUMN html_object_key VARCHAR(255) DEFAULT NULL
    COMMENT '净化正文 HTML 落盘 key;空=无 HTML' AFTER content_object_key;
