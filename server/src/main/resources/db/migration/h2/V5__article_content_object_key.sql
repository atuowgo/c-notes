-- V5:本地文件对象存储(A2)——长正文落盘;article 旁加 content_object_key 指向存储 key。
-- 落库时 content 长度超 storage.threshold-chars 则写文件、content 列置空、记此 key;取详情按 key 读回。
ALTER TABLE article ADD COLUMN content_object_key VARCHAR(255) DEFAULT NULL;
