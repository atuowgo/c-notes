-- 多用户阶段 1:给文章/想法/标签加归属;存量数据回填到系统初始用户。
ALTER TABLE article ADD COLUMN owner_id CHAR(32) DEFAULT NULL;
ALTER TABLE note    ADD COLUMN owner_id CHAR(32) DEFAULT NULL;
ALTER TABLE tag     ADD COLUMN owner_id CHAR(32) DEFAULT NULL;

UPDATE article SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;
UPDATE note    SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;
UPDATE tag     SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;

-- URL 幂等改为按所有者隔离:同一 URL 不同用户各存副本,互不可见。
ALTER TABLE article DROP CONSTRAINT uk_url_hash;
ALTER TABLE article ADD CONSTRAINT uk_owner_url UNIQUE (owner_id, url_hash);

CREATE INDEX idx_note_owner ON note (owner_id);
CREATE INDEX idx_tag_owner  ON tag (owner_id);
