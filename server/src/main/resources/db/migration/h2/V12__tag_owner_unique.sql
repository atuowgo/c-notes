-- 多用户补强:标签私有池。改 tag 全局唯一 uk_name 为按所有者唯一 uk_owner_name,
-- 与 article 的 uk_owner_url 同构。

UPDATE tag SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;

ALTER TABLE tag DROP CONSTRAINT uk_name;
ALTER TABLE tag ADD CONSTRAINT uk_owner_name UNIQUE (owner_id, name);
