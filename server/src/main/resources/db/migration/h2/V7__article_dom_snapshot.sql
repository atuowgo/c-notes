-- 二级抓取(产品设计 §5.4):存插件提交的渲染后 DOM 快照,正文提取不佳时由模型清洗兜底。
ALTER TABLE article ADD COLUMN dom_snapshot LONGTEXT DEFAULT NULL;
