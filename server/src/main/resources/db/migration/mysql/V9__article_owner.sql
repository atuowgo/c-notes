-- 多用户阶段 1:给文章/想法/标签加归属;存量数据回填到系统初始用户。
ALTER TABLE article ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '所有者 app_user.id';
ALTER TABLE note    ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '想法作者 app_user.id';
ALTER TABLE tag     ADD COLUMN owner_id CHAR(32) DEFAULT NULL COMMENT '标签所有者 app_user.id(私有标签池)';

UPDATE article SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;
UPDATE note    SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;
UPDATE tag     SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;

-- URL 幂等改为按所有者隔离:同一 URL 不同用户各存副本,互不可见。
ALTER TABLE article DROP INDEX uk_url_hash;
ALTER TABLE article ADD UNIQUE KEY uk_owner_url (owner_id, url_hash);

CREATE INDEX idx_note_owner ON note (owner_id);
CREATE INDEX idx_tag_owner  ON tag (owner_id);
