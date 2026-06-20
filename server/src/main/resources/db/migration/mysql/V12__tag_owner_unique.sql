-- 多用户补强:标签私有池。V9 给 tag 加了 owner_id 但唯一键仍是全局 uk_name(name),
-- 导致两个用户无法拥有同名标签(私有标签池名存实亡)。此处改为按所有者唯一,
-- 与 article 的 uk_owner_url 同构。

-- 先把历史/漏赋值导致的 owner_id 为空的标签回填到系统用户,避免空归属。
UPDATE tag SET owner_id = '00000000000000000000000000000001' WHERE owner_id IS NULL;

ALTER TABLE tag DROP INDEX uk_name;
ALTER TABLE tag ADD UNIQUE KEY uk_owner_name (owner_id, name);
