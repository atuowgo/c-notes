-- V2:tag 长成"簇"——加演进式综述字段(知识网 V3)。
ALTER TABLE tag ADD COLUMN living_summary TEXT DEFAULT NULL COMMENT '演进式综述(AI 维护)';
ALTER TABLE tag ADD COLUMN summary_member_count INT DEFAULT NULL COMMENT '上次生成综述时的成员文章数,用于判定是否需重写';
ALTER TABLE tag ADD COLUMN summary_updated_at DATETIME DEFAULT NULL COMMENT '综述最近更新时间';
