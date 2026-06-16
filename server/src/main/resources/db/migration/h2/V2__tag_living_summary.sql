-- V2:tag 长成"簇"——加演进式综述字段(知识网 V3)。
ALTER TABLE tag ADD COLUMN living_summary TEXT DEFAULT NULL;
ALTER TABLE tag ADD COLUMN summary_member_count INT DEFAULT NULL;
ALTER TABLE tag ADD COLUMN summary_updated_at DATETIME DEFAULT NULL;
