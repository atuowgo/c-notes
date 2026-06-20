package com.cnotes.auth;

/**
 * 分享级别 —— 单调递增,能力随级别累加:
 * PRIVATE < READ_ONLY < BOOKMARKABLE < COLLECTABLE < ANNOTATABLE < COMMENTABLE。
 * 文章生效级别 = article.shareLevel ?? owner.defaultShareLevel。
 */
public enum ShareLevel {
    PRIVATE, READ_ONLY, BOOKMARKABLE, COLLECTABLE, ANNOTATABLE, COMMENTABLE;

    /** 当前级别是否满足(>=)所需能力级别。 */
    public boolean atLeast(ShareLevel required) {
        return this.ordinal() >= required.ordinal();
    }

    /** 宽松解析:null/未知一律回退到最安全的 PRIVATE。 */
    public static ShareLevel parse(String s) {
        if (s == null) return PRIVATE;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PRIVATE;
        }
    }
}
