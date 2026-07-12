-- V9:净化后正文 HTML 落盘 key。
ALTER TABLE article ADD COLUMN html_object_key VARCHAR(255) DEFAULT NULL;
